#!/usr/bin/env python3
"""
Builds the prayer JSON files in app/src/main/assets/siddur/<nusach>/ from the
Sefaria "Siddur Ashkenaz" export.

Usage:
    python3 tools/build_content.py [path/to/merged.json]

If no path is given the export is downloaded from Sefaria's public bucket.

Output format (one file per prayer):
    {
      "id": "shacharit",
      "title": "שחרית",
      "service": "shacharit" | "mincha" | "maariv" | null,
      "rollover": "none" | "noon" | "sunset",
      "sections": [
        {"title": "...", "level": 1|2, "when": "<expr>"?, "items": [
            {"type": "text"|"instruction"|"note", "text": "...", "when": "<expr>"?}
        ]}
      ]
    }

"when" is a boolean expression over day flags (see DayContext.kt), e.g.
"rosh_chodesh | chol_hamoed" or "tachanun & (day_2 | day_5)".
Text may contain <b>, <small> and <big> tags and newlines.
"""
import json
import os
import re
import sys
import urllib.request

import birnbaum

EXPORT_URL = ("https://storage.googleapis.com/sefaria-export/json/Liturgy/Siddur/"
              "Siddur%20Ashkenaz/Hebrew/merged.json")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "siddur", "ashkenaz")

NIKUD = re.compile(r"[֑-ׇ]")

# Labels that, when they make up the whole <small> prefix of an item, mark the
# item (or the following item, if the label stands alone) as conditional.
LABELS = {
    'בעשי"ת': "aseret_yemei_teshuva",
    'בעשי"ת יסיים': "aseret_yemei_teshuva",
    'בעשי"ת מסיים': "aseret_yemei_teshuva",
    'בעשרת ימי תשובה': "aseret_yemei_teshuva",
    'בעשרת ימי תשובה מוסיפין': "aseret_yemei_teshuva",
    'בקיץ': "morid_hatal",
    'בחורף': "mashiv_haruach",
    'בימות החמה': "vesen_beracha",
    'בימות הגשמים': "tal_umatar",
    'בחנוכה': "chanukah",
    'לחנוכה': "chanukah",
    'בחנוכה אומרים': "chanukah",
    'בחנוכה אומרים זה': "chanukah",
    'בפורים': "purim",
    'בפורים אומרים': "purim",
    'בראש חדש ובחול המועד אומרים זה': "yaaleh_veyavo",
    'בראש חודש ובחול המועד אומרים כאן "יעלה ויבא"': "yaaleh_veyavo",
    'בתענית ציבור': "fast_day",
    'בתענית ציבור אומרים כאן עננו בתפילת הלחש': "fast_day | tisha_bav",
    'לתענית ציבור': "fast_day | tisha_bav",
    'במנחת תשעה באב': "tisha_bav",
    'בתשעה באב': "tisha_bav",
    'בראשון בשבת': "day_1",
    'בשני בשבת': "day_2",
    'בשלישי בשבת': "day_3",
    'ברביעי בשבת': "day_4",
    'בחמישי בשבת': "day_5",
    'בשישי בשבת': "day_6",
    'בחול קודם ברכת המזון אומרים': "tachanun_day",
    'בשבת קודם ברכת המזון אומרים': "!tachanun_day",
    'בשבת': "shabbat",
    'בשבת אומר': "shabbat",
    'בשבת מוסיפים': "shabbat",
    'לר"ח': "rosh_chodesh",
    'בראש חודש': "rosh_chodesh",
    'לפסח': "pesach",
    'בשבועות': "shavuot",
    'לסכות': "sukkot",
    'בסוכות': "sukkot",
    'לש"ע וש"ת': "shemini_atzeret",
    'לר"ה': "rosh_hashana",
    'בראש השנה': "rosh_hashana",
    'ילדים האוכלים ביו"כ': "yom_kippur",
    'ביו"ט אומר': "yom_tov",
    'ביו"ט שחל בשבת': "yom_tov & shabbat",
    'בראש השנה שחל בשבת': "rosh_hashana & shabbat",
}

LABEL_RE = re.compile(r"^<small>\s*([^<]*?)\s*[:—\-]?\s*</small>\s*(.*)$", re.S)


def strip_nikud(s):
    return NIKUD.sub("", s)


def clean(text):
    text = re.sub(r"<br\s*/?>", "\n", text)
    text = re.sub(r"</?em>", "", text)
    text = text.replace("‎", "").replace("‏", "")
    text = re.sub(r"[ \t]+", " ", text)
    text = "\n".join(line.strip() for line in text.split("\n"))
    # Drop empty tag pairs left over by the source
    text = re.sub(r"<(b|small|big)>\s*</\1>", "", text)
    return text.strip()


# Labels of options embedded inside a line of text, e.g. "בְּיוֹם <small>לר"ח:</small> רֹאשׁ הַחֹֽדֶשׁ הַזֶּה:"
INLINE_LABELS = {
    'בעשי"ת': "aseret_yemei_teshuva",
    'בשבת וביו"ט אומר:': "shabbat | yom_tov",
    'לר"ח:': "rosh_chodesh",
    'לפסח:': "pesach",
    'לסכות:': "sukkot",
}


def inline_conditions(text):
    """Wraps embedded options in <if when="...">...</if> so the app can show only today's."""
    def paren(m):
        expr = INLINE_LABELS.get(m.group(1).strip())
        return f'<if when="{expr}">{m.group(0)}</if>' if expr else m.group(0)
    # "(<small>label</small> text)"
    text = re.sub(r"\(<small>([^<]+)</small>[^()]*\)", paren, text)

    def option(m):
        expr = INLINE_LABELS.get(m.group(1).strip())
        return f'<if when="{expr}">{m.group(0)}</if>' if expr else m.group(0)
    # "<small>label:</small> text:" (an option running up to its closing colon)
    return re.sub(r"(?<!^)(?<!\()<small>([^<]+:)</small>[^:<]*:", option, text)


def plain(text):
    return re.sub(r"<[^>]+>", "", text).strip()


def nikud_density(p):
    letters = len(re.findall(r"[\u05D0-\u05EA]", p))
    return len(NIKUD.findall(p)) / letters if letters else 0.0


def classify(text):
    """Returns the item type for a cleaned source string."""
    p = plain(text)
    whole_small = re.fullmatch(r"<small>(?:(?!</small>).)*</small>", text, re.S) is not None
    # Vocalized text in small print is prayer text said quietly / optionally
    if whole_small and nikud_density(p) > 0.4:
        return "text"
    if whole_small or not NIKUD.search(p):
        return "note" if len(p) > 110 else "instruction"
    return "text"


def label_condition(text):
    """If the item starts with a known label, returns (expr, rest_of_text)."""
    m = LABEL_RE.match(text)
    if not m:
        return None, text
    label = strip_nikud(m.group(1)).strip().rstrip(":").strip()
    expr = LABELS.get(label)
    if expr is None:
        return None, text
    return expr, m.group(2).strip()


class Source:
    def __init__(self, data):
        self.text = data["text"]
        self.schema = data["schema"]

    def node(self, *path):
        x = self.text
        for k in path:
            x = x[k]
        return x

    def he_title(self, *path):
        nodes = self.schema["nodes"]
        title = None
        for k in path:
            node = next(n for n in nodes if n["enTitle"] == k)
            title = node["heTitle"]
            nodes = node.get("nodes", [])
        return title




# Remarks that read as rubrics (end in ':') but are halachic notes or no longer needed
DROP_INSTRUCTIONS = (
    'בחזרת הש"ץ אומרים כאן קדושה',   # Kedusha is its own collapsible section
    "בחול אומר החזן קדיש שלם",
    "שלושה שאכלו כאחד חייבין בזימון",
)
# Long rubrics shortened to what the reader needs
SHORTEN_INSTRUCTIONS = {
    "מי שהגיע בנו לבן י\"ג שנה": "אבי בר המצווה אומר:",
    "בשני ובחמישי ושאר ימים שמוציאין ספר תורה אומרים יהללו": "ואומר החזן:",
}


def is_halachic_note(item):
    """Notes and halachic remarks are dropped; short rubrics ("שליח ציבור:") and labels stay."""
    if item["type"] == "note":
        return True
    if item["type"] != "instruction":
        return False
    p = plain(item["text"])
    if p.startswith(("אם ", "יש ")) or p.startswith(DROP_INSTRUCTIONS):
        return True
    if p.endswith(":") or p.startswith("("):
        return False
    return len(p) > 40 or "תקנ" in p


def flatten(lst):
    for x in lst:
        if isinstance(x, list):
            yield from flatten(x)
        else:
            yield x


def make_items(raw, overrides=None):
    """
    Converts a list of raw source strings into items.
    overrides: {index: {"when": expr, "type": t, "span": n, "text": s, "skip": bool,
                        "strip_label": bool}}
    "span" applies the item's condition to the following n items as well.
    """
    overrides = overrides or {}
    raw = list(flatten(raw))
    items = []
    carry = []  # list of [expr, remaining]
    for i, src in enumerate(raw):
        ov = overrides.get(i, {})
        if ov.get("skip"):
            continue
        text = clean(ov.get("text", src))
        if not text:
            continue
        expr, rest = label_condition(text)
        if ov.get("strip_label"):
            text = re.sub(r"^<small>.*?</small>\s*", "", text, flags=re.S)
        itype = ov.get("type") or classify(text)
        if "type" not in ov and itype != "text" and expr and rest and NIKUD.search(plain(rest)):
            # "<small>label:</small> prayer text" -> it's prayer text with a label
            itype = "text"
        when = ov.get("when")
        if when is None and expr is not None:
            when = expr
        if when is None and strip_nikud(plain(text)).startswith(("על הנסים", "ועל הנסים")):
            when = "al_hanisim"
        # Conditions carried from a preceding stand-alone label
        inherited = [c[0] for c in carry if c[1] > 0]
        for c in carry:
            c[1] -= 1
        carry = [c for c in carry if c[1] > 0]
        if inherited and "when" not in ov:
            parts = inherited + ([when] if when else [])
            when = " & ".join(f"({p})" if "|" in p else p for p in dict.fromkeys(parts))
        if itype == "instruction":
            for prefix, short in SHORTEN_INSTRUCTIONS.items():
                if plain(text).startswith(prefix):
                    text = f"<small>{short}</small>"
        item = {"type": itype, "text": inline_conditions(text)}
        if when:
            item["when"] = when
        if is_halachic_note(item) and not ov.get("keep"):
            continue
        prev = items[-1] if items else None
        if (prev is not None and prev.pop("_label", False) and itype == "text"
                and len(plain(text)) <= 40 and prev.get("when") and item.get("when") == prev["when"]):
            # A label followed by a short insert reads as one line: "בקיץ: מוֹרִיד הַטָּל"
            prev["type"] = "text"
            prev["text"] = f'<small>{plain(prev["text"])}</small> {item["text"]}'
        else:
            items.append(item)
        span = ov.get("span")
        if span is None and expr is not None and not rest:
            span = 1  # stand-alone label governs the next item
            item["_label"] = True
        if span and (ov.get("when") or expr):
            carry.append([ov.get("when") or expr, span])
    for it in items:
        it.pop("_label", None)
    return items


class Builder:
    def __init__(self, src, pid, title, service, rollover):
        self.src = src
        self.doc = {"id": pid, "title": title, "service": service,
                    "rollover": rollover, "sections": []}

    def part(self, title, when=None, zmanim=None):
        s = {"title": title, "level": 1, "items": []}
        if when:
            s["when"] = when
        if zmanim:
            s["zmanim"] = zmanim  # the app shows today's times under this heading
        self.doc["sections"].append(s)
        return self

    def leaf(self, path, title=None, when=None, overrides=None, raw=None):
        if raw is None:
            raw = self.src.node(*path)
        items = make_items(raw, overrides)
        if not items:
            return self
        title = title or self.src.he_title(*path)
        title = title.strip()  # a title of " " means "no heading"
        if title.startswith("תהילים"):
            title = ""  # show the psalm itself without a "Tehillim N" heading
        s = {"title": title, "level": 2, "items": items}
        if when:
            s["when"] = when
        self.doc["sections"].append(s)
        return self

    def tree(self, path, when=None, exclude=(), per_leaf=None, overrides=None, titles=None):
        """Adds every leaf under path, in source order."""
        per_leaf = per_leaf or {}
        overrides = overrides or {}
        titles = titles or {}
        node = self.src.node(*path)
        for k, v in node.items():
            if k in exclude:
                continue
            sub = path + (k,)
            if isinstance(v, dict):
                self.tree(sub, when, exclude, per_leaf, overrides, titles)
            else:
                self.leaf(sub, title=titles.get(k), when=per_leaf.get(k, when),
                          overrides=overrides.get(k))
        return self

    def amidah(self, path, kedusha, exclude=(), when=None, overrides=None):
        """The Amidah blessings in order, with Kedusha (said only in the repetition) as a
        collapsed section right before אתה קדוש."""
        keys = [k for k in self.src.node(*path) if k not in exclude and k != kedusha]
        keys.insert(keys.index("Holiness of God"), kedusha)
        for k in keys:
            self.leaf(path + (k,), title="קדושה" if k == kedusha else None, when=when,
                      overrides=(overrides or {}).get(k))
            if k == kedusha:
                self.doc["sections"][-1]["collapsible"] = True
        return self

    def custom(self, title, items, when=None, level=2):
        s = {"title": title, "level": level, "items": items}
        if when:
            s["when"] = when
        self.doc["sections"].append(s)
        return self

    def write(self):
        # Drop level-1 parts that ended up with no children
        secs = self.doc["sections"]
        keep = []
        for i, s in enumerate(secs):
            if s["level"] == 1 and not s["items"]:
                nxt = secs[i + 1] if i + 1 < len(secs) else None
                if nxt is None or nxt["level"] == 1:
                    continue
            keep.append(s)
        self.doc["sections"] = keep
        path = os.path.join(OUT_DIR, self.doc["id"] + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(self.doc, f, ensure_ascii=False, indent=1)
            f.write("\n")
        n = sum(len(s["items"]) for s in keep)
        print(f"wrote {path}: {len(keep)} sections, {n} items")


W = ("Weekday",)


def small(items):
    """Formats prayer text items in small print, like Modim d'Rabbanan."""
    out = []
    for it in items:
        it = dict(it)
        if it["type"] == "text":
            # Nested <small> inside keeps its own (relative) size; unwrap a leading label so the
            # response ("והקהל עונים: ...") reads like the blessing above it
            it["text"] = "<small>" + re.sub(r"^<small>([^<]*)</small>", r"\1", it["text"]) + "</small>"
        out.append(it)
    return out


def haftarah_section(b):
    """Haftarah with its weekday blessings; the app fills in the text (e.g. Tisha B'Av, fast-day
    Mincha) and drops the section on days without one."""
    page = "קריאת התורה לשחרית בשבת ויום טוב"
    ls = birnbaum.lines(page)
    before = ls.index(next(l for l in ls if l.startswith("לפני קריאת ההפטרה")))
    after = ls.index(next(l for l in ls if l.startswith("אחרי קריאת ההפטרה")))
    items = [
        {"type": "instruction", "text": "לפני ההפטרה המפטיר מברך:"},
        {"type": "text", "text": clean(ls[before + 1])},
        {"type": "text", "text": "@haftarah"},
        {"type": "instruction", "text": "אחרי ההפטרה:"},
    ] + [{"type": "text", "text": clean(l)} for l in ls[after + 1:after + 5]]
    b.custom("הפטרה", items)
    b.doc["sections"][-1]["source"] = "haftarah"


def chol_hamoed_musaf():
    """The middle blessing of Musaf for Chol Hamoed, from Birnbaum, with the Shabbat
    parentheticals removed and each festival's / day's lines tagged."""
    page = "מוסף לשלוש רגלים"
    lines = birnbaum.section(page, "אַתָּה בְחַרְתָּֽנוּ", "רְצֵה, יְהֹוָה")
    no_shabbat = lambda l: re.sub(r"\s*\([^)]*\)\s*", " ", l).replace(" ,", ",").strip()

    def opt(label_prefix, line_prefix):
        """An inline festival option, e.g. 'בפסח: חַג הַמַּצּוֹת הַזֶּה, ...'."""
        line = next(l for l in lines[start:] if l.startswith(line_prefix))
        label, text = line.split(":", 1)
        return f'<if when="{label_prefix}"><small>{label}:</small> {text.strip()}</if>'

    def text(prefix):
        return next(no_shabbat(l) for l in lines if no_shabbat(l).startswith(prefix))

    items = [{"type": "text", "text": text("אַתָּה בְחַרְתָּֽנוּ")}]
    start = 0
    first_options = lines.index(next(l for l in lines if l.startswith("וַתִּתֶּן")))
    start = first_options
    items.append({"type": "text", "text": " ".join([
        text("וַתִּתֶּן"), opt("pesach", "בפסח:"), opt("sukkot", "בסוכות:"), text("מִקְרָא קֹֽדֶשׁ"),
    ])})
    start = lines.index(next(l for l in lines if l.startswith("וּמִפְּנֵי")))
    items.append({"type": "text", "text": " ".join([
        text("וּמִפְּנֵי"), opt("pesach", "בפסח:"), opt("sukkot", "בסוכות:"), text("נַעֲשֶׂה וְנַקְרִיב"),
    ])})

    def korban(prefix):
        return next(l for l in lines if l.startswith(prefix))

    items.append({"type": "text", "when": "pesach", "text": "<small>בפסח:</small> " + korban("וְהִקְרַבְתֶּם אִשֶּׁה")})
    days = ["הַשֵּׁנִי", "הַשְּׁלִישִׁי", "הָרְבִיעִי", "הַחֲמִישִׁי", "הַשִּׁשִּׁי", "הַשְּׁבִיעִי"]
    for n, day in enumerate(days, start=2):
        # Outside Israel each day also says the previous day's line (ספיקא דיומא)
        when = f"sukkot_day_{n}" + (f" | (chul & sukkot_day_{n + 1})" if n < 7 else "")
        items.append({"type": "text", "when": when, "text": korban("וּבַיּוֹם " + day)})
    items.append({"type": "text", "text": korban("וּמִנְחָתָם")})
    items.append({"type": "text", "text": text("אֱלֹהֵֽינוּ וֵאלֹהֵי אֲבוֹתֵֽינוּ, מֶֽלֶךְ רַחֲמָן")})
    items.append({"type": "text", "text": text("וְהַשִּׂיאֵֽנוּ")})
    return [dict(it, text=clean(it["text"])) for it in items]


def birkat_kohanim(src):
    """Birkat Kohanim with duchening; the chazan's אלהינו... in small print."""
    bk = src.node("Weekday", "Shacharit", "Amidah", "Birkat Kohanim")
    verses = [clean(v) for v in bk[3:6]]
    items = [
        {"type": "instruction", "text": "שליח ציבור:"},
        {"type": "text", "text": "<small>" + clean(bk[2]) + "</small>"},
        {"type": "instruction", "text": "הכהנים מברכים:"},
        {"type": "text", "text": "<b>בָּרוּךְ</b> אַתָּה יְהֹוָה אֱלֹהֵֽינוּ מֶֽלֶךְ הָעוֹלָם אֲשֶׁר קִדְּשָֽׁנוּ "
                                 "בִּקְדֻשָּׁתוֹ שֶׁל אַהֲרֹן וְצִוָּֽנוּ לְבָרֵךְ אֶת עַמּוֹ יִשְׂרָאֵל בְּאַהֲבָה:"},
    ]
    for v in verses:
        items.append({"type": "text", "text": v + " <small>(הקהל: אָמֵן)</small>"})
    items += [
        {"type": "instruction", "text": "הקהל אומר:"},
        {"type": "text", "text": "<small>אַדִּיר בַּמָּרוֹם שׁוֹכֵן בִּגְבוּרָה, אַתָּה שָׁלוֹם וְשִׁמְךָ שָׁלוֹם. "
                                 "יְהִי רָצוֹן שֶׁתָּשִׂים עָלֵֽינוּ וְעַל כָּל עַמְּךָ בֵּית יִשְׂרָאֵל חַיִּים "
                                 "וּבְרָכָה לְמִשְׁמֶֽרֶת שָׁלוֹם:</small>"},
    ]
    return items


def half_kaddish(src):
    return src.node("Weekday", "Shacharit", "Pesukei Dezimra", "Half Kaddish")[1:]


def build_shacharit(src):
    b = Builder(src, "shacharit", "שחרית", "shacharit", "none")
    S = W + ("Shacharit",)

    b.part("ברכות השחר וקרבנות")
    label = lambda t: {"type": "instruction", "text": f"<small>{t}</small>"}
    b.tree(S + ("Preparatory Prayers",), exclude=(
        "Akedah", "Kiyor", "Terumat HaDeshen", "Ketoret", "Order of the Temple Service", "Laws of Sacrifices",
    ), overrides={
        # Tallit: just the bracha
        "Tallit": {0: {"skip": True}, 1: {"skip": True}, 2: {"skip": True}, 4: {"skip": True}},
        # Tefillin: the brachot and וארשתיך, with short labels
        "Tefillin": {
            0: {"skip": True},
            1: label("על תפילין של יד:"),
            3: label("על תפילין של ראש:"),
            5: label("ואומר:"),
            7: {"skip": True},
            8: label("כשכורך את הרצועה על האצבע:"),
            10: {"skip": True}, 11: {"skip": True}, 12: {"skip": True},
        },
    })

    b.part("פסוקי דזמרה")
    b.tree(S + ("Pesukei Dezimra",),
           per_leaf={"Mizmor Letoda": "mizmor_letoda", "Psalm 130": "aseret_yemei_teshuva"})

    b.part("קריאת שמע וברכותיה", zmanim="shema")
    b.tree(S + ("Blessings of the Shema",))

    b.part("שמונה עשרה", zmanim="tefila")
    b.amidah(S + ("Amidah",), "Kedushah", exclude=("Birkat Kohanim", "Peace", "Concluding Passage"))
    b.custom("ברכת כהנים", birkat_kohanim(src))
    b.leaf(S + ("Amidah", "Peace"))
    b.leaf(S + ("Amidah", "Concluding Passage"))

    b.part("אחר התפילה")
    rc = ("Festivals", "Rosh Chodesh", "Hallel")
    b.tree(rc, when="hallel", overrides={
        "Psalm 115": {0: {"when": "hallel_shalem", "strip_label": True}},
        "Psalm 116": {0: {"when": "hallel_shalem", "strip_label": True}},
        # The congregation's repeated "הודו" responses
        "Psalm 118": {2: {"skip": True}, 4: {"skip": True}, 6: {"skip": True}},
    })
    b.leaf(S + ("Post Amidah", "Vidui and 13 Middot"), when="aseret_yemei_teshuva | fast_day")
    b.leaf(S + ("Post Amidah", "Avinu Malkenu"), when="avinu_malkenu", overrides={
        20: {"span": 5}, 26: {"span": 5},
    })
    T = S + ("Post Amidah", "Tachanun")
    b.leaf(T + ("For Monday and Thursday",), when="tachanun & (day_2 | day_5)")
    b.leaf(T + ("Nefilat Apayim",), when="tachanun")
    b.leaf(T + ("God of Israel",), when="tachanun & (day_2 | day_5)")
    b.leaf(T + ("Shomer Yisrael",), when="tachanun")
    b.leaf(T + ("Half Kaddish",))

    b.part("קריאת התורה", when="torah_reading")
    R = S + ("Torah Reading",)
    b.tree(R + ("Removing the Torah from Ark",), when="torah_reading",
           per_leaf={"El Erech Appayim": "torah_reading & tachanun & (day_2 | day_5)"},
           titles={"Lekha Hashem": " "})
    F = R + ("Reading from Sefer",)
    b.leaf(F + ("Birkat HaTorah",), when="torah_reading")
    b.custom("ברכת הגומל", small(make_items(src.node(*F, "Birkat Hagomel"))), when="torah_reading")
    # Filled in by the app with the day's reading (parsha, Rosh Chodesh, Chanukah, fast...)
    b.custom("קריאת התורה", [])
    b.doc["sections"][-1]["source"] = "torah"
    b.leaf(F + ("Half Kaddish",), when="torah_reading")
    b.leaf(F + ("Raising the Torah",), when="torah_reading", overrides={
        3: {"when": "tachanun & (day_2 | day_5)", "span": 5},
    })
    haftarah_section(b)
    b.tree(R + ("Returning Sefer to Aron",), when="torah_reading")

    b.part("אשרי ובא לציון")
    C = S + ("Concluding Prayers",)
    b.leaf(C + ("Ashrei",))
    b.leaf(C + ("Lamenatze'ach",), when="lamenatzeach")
    b.leaf(C + ("Uva Letzion",))
    # Rosh Chodesh: half kaddish + Musaf, then the service continues with Kaddish Shalem
    b.custom("חצי קדיש", make_items(half_kaddish(src)), when="rosh_chodesh | chol_hamoed")
    # Musaf for Rosh Chodesh: the weekday blessings (better vocalized in the source),
    # with the Rosh Chodesh middle blessing and no Ya'aleh Veyavo
    A = S + ("Amidah",)
    musaf = ("Festivals", "Rosh Chodesh", "Musaf Amidah for Rosh Chodesh")
    rc = "rosh_chodesh"
    b.part("מוסף לראש חודש", when=rc)
    for leaf in ("Patriarchs", "Divine Might"):
        b.leaf(A + (leaf,), when=rc)
    b.leaf(A + ("Kedushah",), title="קדושה", when=rc)
    b.doc["sections"][-1]["collapsible"] = True
    b.leaf(A + ("Holiness of God",), when=rc)
    b.leaf(musaf + ("Sanctity of the Day",), when=rc)
    temple = src.node(*A, "Temple Service")
    b.leaf(A + ("Temple Service",), when=rc, raw=[temple[0], temple[4]])
    b.leaf(A + ("Thanksgiving",), when=rc)
    b.custom("ברכת כהנים", birkat_kohanim(src), when=rc)
    b.leaf(A + ("Peace",), when=rc)
    b.leaf(A + ("Concluding Passage",), when=rc)

    # Musaf for Chol Hamoed: weekday blessings around the festival middle blessing
    ch = "chol_hamoed"
    b.part("מוסף לחול המועד", when=ch)
    for leaf in ("Patriarchs", "Divine Might"):
        b.leaf(A + (leaf,), when=ch)
    b.leaf(A + ("Kedushah",), title="קדושה", when=ch)
    b.doc["sections"][-1]["collapsible"] = True
    b.leaf(A + ("Holiness of God",), when=ch)
    b.custom("קדושת היום", chol_hamoed_musaf(), when=ch)
    b.leaf(A + ("Temple Service",), when=ch, raw=[temple[0], temple[4]])
    b.leaf(A + ("Thanksgiving",), when=ch)
    b.custom("ברכת כהנים", birkat_kohanim(src), when=ch)
    b.leaf(A + ("Peace",), when=ch)
    b.leaf(A + ("Concluding Passage",), when=ch)
    b.part("סיום התפילה")
    b.leaf(C + ("Kaddish Shalem",))
    b.leaf(C + ("Alenu",))
    b.leaf(C + ("Mourner's Kaddish",))
    b.leaf(C + ("Song of the Day",), overrides={
        1: {"span": 2}, 4: {"span": 2}, 7: {"span": 2},
        10: {"span": 2}, 13: {"span": 2}, 16: {"span": 2},
    })
    b.leaf(C + ("Barchi Nafshi",), when="rosh_chodesh")
    b.leaf(C + ("LeDavid",), when="ledavid")
    b.tree(C + ("Korbanot (Israel)",), when="israel",
           titles={"Mourner's Kaddish": "קדיש דרבנן"})

    b.part("אחר התפילה")
    b.tree(S + ("Post Service",))
    b.write()


def build_mincha(src):
    b = Builder(src, "mincha", "מנחה", "mincha", "none")
    M = W + ("Minchah",)
    b.part("אשרי")
    ashrei = src.node(*M, "Ashrei")
    b.leaf(M + ("Ashrei",), raw=ashrei[:1])

    # Fast days: Torah reading (ויחל) and haftarah (דרשו) before the half Kaddish
    fast = "fast_day | tisha_bav"
    S = W + ("Shacharit",)
    R = S + ("Torah Reading",)
    b.part("קריאת התורה", when=fast)
    for leaf in ("Vayehi Binsoa", "Berich Shmei", "Lekha Hashem", "Vetigaleh Veteraeh"):
        b.leaf(R + ("Removing the Torah from Ark", leaf), when=fast,
               title=" " if leaf == "Lekha Hashem" else None)
    b.leaf(R + ("Reading from Sefer", "Birkat HaTorah"), when=fast)
    b.custom("קריאת התורה", [], when=fast)
    b.doc["sections"][-1]["source"] = "torah"
    b.leaf(R + ("Reading from Sefer", "Raising the Torah"), raw=src.node(*R, "Reading from Sefer", "Raising the Torah")[:3],
           when=fast)
    haftarah_section(b)
    b.doc["sections"][-1]["when"] = fast
    b.tree(R + ("Returning Sefer to Aron",), when=fast)
    b.custom("חצי קדיש", make_items(ashrei[1:]))
    b.part("שמונה עשרה")
    b.amidah(M + ("Amida",), "Keduasha", exclude=("Birkat Kohanim", "Peace", "Concluding Passage"))
    fast = "fast_day | tisha_bav"
    b.custom("ברכת כהנים", birkat_kohanim(src), when=fast)
    peace = src.node(*M, "Amida", "Peace")
    b.leaf(M + ("Amida", "Peace"), overrides={
        0: {"when": "!(fast_day | tisha_bav)"},
        1: {"when": fast, "type": "text",
            "text": "<small>לתענית ציבור:</small> " + re.sub(r"^<small>לתענית ציבור:\s*|</small>$", "", peace[1])},
    })
    b.leaf(M + ("Amida", "Concluding Passage"))
    b.part("אחר התפילה")
    b.leaf(M + ("Post Amidah", "Avinu Malkenu"), when="avinu_malkenu", overrides={
        20: {"span": 5}, 26: {"span": 5},
    })
    b.leaf(M + ("Post Amidah", "Tachanun", "Nefilat Appayim"), when="tachanun")
    b.leaf(M + ("Post Amidah", "Tachanun", "Shomer Yisrael"), when="tachanun")
    b.leaf(M + ("Post Amidah", "Kaddish Shalem"))
    b.part("סיום התפילה")
    b.tree(M + ("Concluding Prayers",))
    b.write()


def build_maariv(src):
    b = Builder(src, "maariv", "ערבית", "maariv", "noon")
    M = W + ("Maariv",)
    b.part("קריאת שמע וברכותיה")
    b.leaf(M + ("Vehu Rachum",))
    b.leaf(M + ("Barchu",))
    b.tree(M + ("Blessings of the Shema",),
           per_leaf={"Third Blessing after Shema": "chul"},
           titles={"Third Blessing after Shema": "ברוך ה׳ לעולם"})
    b.part("שמונה עשרה")
    b.tree(M + ("Amidah",), overrides={
        "Knowledge": {1: {"type": "text", "when": "motzaei_shabbat | motzaei_yom_tov",
                          "text": re.sub(r"</?small>", "", src.node(*M, "Amidah", "Knowledge")[1])}},
    })
    b.part("אחר התפילה")
    b.custom("חצי קדיש", make_items(half_kaddish(src)), when="vihi_noam")
    b.leaf(M + ("Additions for Motza'ei Shabbat", "Viyehi Noam"), when="vihi_noam")
    b.leaf(M + ("Kaddish Shalem",))
    omer = src.node(*M, "Sefirat HaOmer")
    ov = {}
    for i, s in enumerate(omer):
        m = re.search(r"<br>\s*(\d+)\.", s)
        if m:
            ov[i] = {"when": f"omer_{int(m.group(1))}", "type": "text"}
    b.leaf(M + ("Sefirat HaOmer",), when="omer", overrides=ov)
    b.leaf(M + ("Alenu",))
    b.leaf(M + ("Mourner's Kaddish",))
    b.leaf(M + ("LeDavid",), when="ledavid")
    b.leaf(M + ("Additions for Motza'ei Shabbat", "Veyiten Lekha"), when="motzaei_shabbat")
    b.write()


def birkat_hamazon_sections(b, src, zimun=None, extra_harachaman=None):
    """Adds the Birkat Hamazon sections. A simcha can replace the zimun and add הרחמן lines
    (after "בַּמָּרוֹם...")."""
    raw = src.node("Berachot", "Birkat HaMazon")

    def rng(a, z, overrides=None):
        ov = {k - a: v for k, v in (overrides or {}).items()}
        return make_items(raw[a:z + 1], ov)

    harachaman = rng(49, 75)
    if extra_harachaman:
        i = next(i for i, it in enumerate(harachaman) if strip_nikud(plain(it["text"])).startswith("במרום"))
        harachaman[i + 1:i + 1] = extra_harachaman

    b.custom("לפני ברכת המזון", rng(0, 3))
    b.custom("זימון", zimun if zimun is not None else rng(4, 19))
    b.custom("ברכת הזן", rng(20, 21))
    b.custom("ברכת הארץ", rng(22, 30, {25: {"when": "al_hanisim"}}))
    b.custom("בונה ירושלים", rng(31, 46, {35: {"span": 9}}))
    b.custom("הטוב והמטיב", rng(47, 48))
    b.custom("הרחמן", harachaman)
    b.custom("שכח ולא הזכיר", rng(76, 88), when="shabbat | rosh_chodesh | yom_tov")


def build_birkat_hamazon(src):
    b = Builder(src, "birkat_hamazon", "ברכת המזון", None, "sunset")
    birkat_hamazon_sections(b, src)
    b.write()


def birn_items(lines):
    """Birnbaum lines -> items. Unvocalized lines are rubrics; a vocalized line that doesn't end
    a sentence continues on the next line (poems, split verses)."""
    items = []
    for line in lines:
        p = plain(line)
        if nikud_density(p) < 0.3:
            items.append({"type": "instruction", "text": line})
        elif items and items[-1]["type"] == "text" and not plain(items[-1]["text"]).endswith((".", ":", "׃")):
            items[-1]["text"] += "\n" + line
        else:
            items.append({"type": "text", "text": line})
    return [dict(it, text=inline_conditions(clean(it["text"]))) for it in items]


def build_brit_milah(src):
    b = Builder(src, "brit_milah", "ברית מילה", None, "none")
    page = "ברית מילה"
    ceremony = birn_items(birnbaum.section(page, "מכניסים", "בִּרְכַּת הַמָּזוֹן לִבְרִית מִילָה"))
    # In Eretz Yisrael the father also says Shehecheyanu
    i = next(i for i, it in enumerate(ceremony) if "לְהַכְנִיסוֹ" in it["text"])
    ceremony[i + 1:i + 1] = [
        {"type": "instruction", "text": "בארץ ישראל נוהגים שהאב מברך גם:", "when": "israel"},
        {"type": "text", "when": "israel",
         "text": "בָּרוּךְ אַתָּה, יְהֹוָה אֱלֹהֵֽינוּ, מֶֽלֶךְ הָעוֹלָם, שֶׁהֶחֱיָֽנוּ וְקִיְּמָֽנוּ וְהִגִּיעָֽנוּ לַזְּמַן הַזֶּה."},
    ]
    b.part("ברית מילה")
    b.custom("סדר הברית", ceremony)

    raw = src.node("Berachot", "Birkat HaMazon")
    zimun = birn_items(birnbaum.section(page, "המזמן:", "ממשיכים ב"))
    zimun += [{"type": "instruction", "text": "וממשיך:"}] + make_items(raw[12:20])
    extra = birn_items(birnbaum.section(page, "הָרַחֲמָן, הוּא יְבָרֵךְ אֲבִי הַיֶּֽלֶד", "הערות"))
    for it in extra:
        it["when"] = None
        it.pop("when")
    b.part("ברכת המזון לברית מילה")
    birkat_hamazon_sections(b, src, zimun=zimun, extra_harachaman=extra)
    b.write()


def build_sheva_brachot(src):
    b = Builder(src, "sheva_brachot", "שבע ברכות", None, "none")
    page = "אירוסין ונישואין"
    brachot = birn_items(birnbaum.section(page, "לאחר קריאת הכתובה")[1:8])
    assert len(brachot) == 7, brachot
    note = {"type": "instruction", "text":
            "שבע הברכות נאמרות בסוף ברכת המזון, אחרי ״עוֹשֶׂה שָׁלוֹם בִּמְרוֹמָיו...״, על כוס של ברכה. "
            "שש הברכות הראשונות מתחלקות בין המסובים, והמברך ברכת המזון אומר ״בּוֹרֵא פְּרִי הַגָּֽפֶן״ בסוף."}
    b.custom("שבע ברכות", [note] + brachot[1:] + brachot[:1])
    zimun = birn_items(birnbaum.section(page, "דְּוַי הָסֵר", "ממשיכים"))
    b.custom("הזימון בסעודת שבע ברכות", [
        {"type": "instruction", "text": "בזימון, במקום ״בִּרְשׁוּת מָרָנָן... שֶׁאָכַֽלְנוּ מִשֶּׁלּוֹ״, המזמן אומר:"},
    ] + zimun)
    b.write()


def build_mein_shalosh(src):
    """Al Hamichya. The source packs the holiday inserts into one string, so we split it."""
    b = Builder(src, "mein_shalosh", "ברכה מעין שלוש", None, "sunset")
    raw = src.node("Berachot", "Birkat Hanehenin", "Eating", "Brachot Achronot", "Al Hamichyah")
    body = raw[4].split("<br>")
    head = body[0]  # ... וּנְבָרֶכְךָ עָלֶיהָ בִּקְדֻשָּׁה וּבְטָהֳרָה.
    inserts = [
        ("shabbat", body[1]),
        ("rosh_chodesh", body[2]),
        ("rosh_hashana", body[3]),
        ("yom_tov | chol_hamoed", body[4]),
        ("pesach", body[5]),
        ("shavuot", body[6]),
        ("sukkot", body[7]),
        ("shemini_atzeret", body[8]),
        ("(yom_tov | chol_hamoed) & !rosh_hashana", body[9]),
    ]
    tail = body[10]

    items = [{"type": "instruction",
              "text": "על מזונות, יין, או פירות משבעת המינים:"
                      "מי שאכל כמה מינים כולל אותם בברכה אחת (מחיה, גפן, עץ)."}]
    items += make_items(raw[0:4])
    items += make_items([head])
    for when, text in inserts:
        for it in make_items([text]):
            it["type"] = "text"
            it["when"] = when
            items.append(it)
    items += make_items([tail])
    items += make_items(raw[5:9])
    items += make_items(raw[9:12])
    b.custom("ברכה מעין שלוש", items)

    nefashot = src.node("Berachot", "Birkat Hanehenin", "Eating", "Brachot Achronot",
                        "Borei Nefashot")
    b.custom("בורא נפשות", make_items(nefashot))
    b.write()


def main():
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache", "merged.json")
        if not os.path.exists(path):
            os.makedirs(os.path.dirname(path), exist_ok=True)
            print("downloading", EXPORT_URL)
            urllib.request.urlretrieve(EXPORT_URL, path)
    with open(path, encoding="utf-8") as f:
        src = Source(json.load(f))
    os.makedirs(OUT_DIR, exist_ok=True)
    build_shacharit(src)
    build_mincha(src)
    build_maariv(src)
    build_birkat_hamazon(src)
    build_mein_shalosh(src)
    build_brit_milah(src)
    build_sheva_brachot(src)


if __name__ == "__main__":
    main()
