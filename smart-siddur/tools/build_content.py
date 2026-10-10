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
        # Conditions carried from a preceding stand-alone label
        inherited = [c[0] for c in carry if c[1] > 0]
        for c in carry:
            c[1] -= 1
        carry = [c for c in carry if c[1] > 0]
        if inherited and "when" not in ov:
            parts = inherited + ([when] if when else [])
            when = " & ".join(f"({p})" if "|" in p else p for p in dict.fromkeys(parts))
        item = {"type": itype, "text": text}
        if when:
            item["when"] = when
        items.append(item)
        span = ov.get("span")
        if span is None and expr is not None and not rest:
            span = 1  # stand-alone label governs the next item
        if span and (ov.get("when") or expr):
            carry.append([ov.get("when") or expr, span])
    return items


class Builder:
    def __init__(self, src, pid, title, service, rollover):
        self.src = src
        self.doc = {"id": pid, "title": title, "service": service,
                    "rollover": rollover, "sections": []}

    def part(self, title, when=None):
        s = {"title": title, "level": 1, "items": []}
        if when:
            s["when"] = when
        self.doc["sections"].append(s)
        return self

    def leaf(self, path, title=None, when=None, overrides=None, raw=None):
        if raw is None:
            raw = self.src.node(*path)
        items = make_items(raw, overrides)
        if not items:
            return self
        s = {"title": title or self.src.he_title(*path), "level": 2, "items": items}
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


def half_kaddish(src):
    return src.node("Weekday", "Shacharit", "Pesukei Dezimra", "Half Kaddish")[1:]


def build_shacharit(src):
    b = Builder(src, "shacharit", "שחרית", "shacharit", "none")
    S = W + ("Shacharit",)

    b.part("ברכות השחר וקרבנות")
    b.tree(S + ("Preparatory Prayers",))

    b.part("פסוקי דזמרה")
    b.tree(S + ("Pesukei Dezimra",),
           per_leaf={"Mizmor Letoda": "mizmor_letoda", "Psalm 130": "aseret_yemei_teshuva"})

    b.part("קריאת שמע וברכותיה")
    b.tree(S + ("Blessings of the Shema",))

    b.part("שמונה עשרה")
    b.tree(S + ("Amidah",))

    b.part("אחר התפילה")
    rc = ("Festivals", "Rosh Chodesh", "Hallel")
    b.tree(rc, when="hallel", overrides={
        "Psalm 115": {0: {"when": "hallel_shalem", "strip_label": True}},
        "Psalm 116": {0: {"when": "hallel_shalem", "strip_label": True}},
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
           per_leaf={"El Erech Appayim": "torah_reading & tachanun & (day_2 | day_5)"})
    b.tree(R + ("Reading from Sefer",), when="torah_reading", overrides={
        "Raising the Torah": {3: {"when": "tachanun & (day_2 | day_5)", "span": 5}},
    })
    b.tree(R + ("Returning Sefer to Aron",), when="torah_reading")

    b.part("אשרי ובא לציון")
    C = S + ("Concluding Prayers",)
    b.leaf(C + ("Ashrei",))
    b.leaf(C + ("Lamenatze'ach",), when="lamenatzeach")
    b.leaf(C + ("Uva Letzion",))
    b.custom("מוסף לחול המועד", [{
        "type": "instruction",
        "text": "בחול המועד אומרים כאן חצי קדיש ומתפללים מוסף לשלוש רגלים (עדיין לא נוסף לאפליקציה).",
    }], when="chol_hamoed")
    # Rosh Chodesh: half kaddish + Musaf, then the service continues with Kaddish Shalem
    b.custom("חצי קדיש", make_items(half_kaddish(src)), when="rosh_chodesh")
    musaf = ("Festivals", "Rosh Chodesh", "Musaf Amidah for Rosh Chodesh")
    b.part("מוסף לראש חודש", when="rosh_chodesh")
    b.tree(musaf, when="rosh_chodesh", overrides={
        "Gevurot": {1: {"when": "morid_hatal", "type": "text"},
                    2: {"when": "mashiv_haruach", "type": "text"}},
        "Hodayah": {7: {"when": "chanukah", "span": 5}},
    })
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
    b.leaf(M + ("Ashrei",))
    b.part("שמונה עשרה")
    b.tree(M + ("Amida",))
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


def build_birkat_hamazon(src):
    b = Builder(src, "birkat_hamazon", "ברכת המזון", None, "sunset")
    raw = src.node("Berachot", "Birkat HaMazon")

    def rng(a, z, overrides=None):
        ov = {k - a: v for k, v in (overrides or {}).items()}
        return make_items(raw[a:z + 1], ov)

    b.custom("לפני ברכת המזון", rng(0, 3))
    b.custom("זימון", rng(4, 19))
    b.custom("ברכת הזן", rng(20, 21))
    b.custom("ברכת הארץ", rng(22, 30, {25: {"when": "al_hanisim"}}))
    b.custom("בונה ירושלים", rng(31, 46, {35: {"span": 9}}))
    b.custom("הטוב והמטיב", rng(47, 48))
    b.custom("הרחמן", rng(49, 75))
    b.custom("שכח ולא הזכיר", rng(76, 88), when="shabbat | rosh_chodesh | yom_tov")
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
              "text": "אחרי אכילת מזונות (חמשת מיני דגן), שתיית יין, או פירות משבעת המינים. "
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


if __name__ == "__main__":
    main()
