#!/usr/bin/env python3
"""
Builds app/src/main/assets/siddur/torah_readings.json: the weekday Torah reading (and haftarah,
where there is one) for every date in a range, for Israel and the diaspora: Monday/Thursday
parsha readings, Rosh Chodesh, Chanukah, Purim, fast days (Shacharit and Mincha), Tisha B'Av
and Chol Hamoed.

Sources:
  - Which reading on which day, and the aliyah divisions: Hebcal (@hebcal/core + @hebcal/leyning,
    BSD-2-Clause), run through tools/leyning_table.mjs. Needs Node: `npm install` in tools/.
  - Text: Sefaria's "Tanach with Nikkud" (vowels, no cantillation).

Usage:
    (cd tools && npm install) && python3 tools/build_torah.py
"""
import json
import os
import re
import subprocess
import urllib.request

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "siddur", "torah_readings.json")
CACHE = os.path.join(TOOLS, ".cache")

FROM, TO = "2025-09-01", "2050-12-31"
TORAH = {"Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy"}
TEXT_URL = ("https://storage.googleapis.com/sefaria-export/json/Tanakh/{part}/{book}/Hebrew/"
            "Tanach%20with%20Nikkud.json")
NIKUD = re.compile(r"[֑-ֽֿ-ׇ]")  # keeps the maqaf

PARSHIYOT = [
    "בראשית", "נח", "לך לך", "וירא", "חיי שרה", "תולדות", "ויצא", "וישלח", "וישב", "מקץ",
    "ויגש", "ויחי", "שמות", "וארא", "בא", "בשלח", "יתרו", "משפטים", "תרומה", "תצוה",
    "כי תשא", "ויקהל", "פקודי", "ויקרא", "צו", "שמיני", "תזריע", "מצורע", "אחרי מות", "קדושים",
    "אמור", "בהר", "בחקתי", "במדבר", "נשא", "בהעלתך", "שלח", "קרח", "חקת", "בלק",
    "פינחס", "מטות", "מסעי", "דברים", "ואתחנן", "עקב", "ראה", "שופטים", "כי תצא", "כי תבוא",
    "נצבים", "וילך", "האזינו", "וזאת הברכה",
]
# Unvocalized holiday names in full spelling
HOLIDAY_SPELLING = {"סכות": "סוכות", "חל המועד": "חול המועד", "ערב שמחת תורה": "הושענא רבה"}

_books = {}


def book(name):
    if name not in _books:
        path = os.path.join(CACHE, f"{name}.json")
        if not os.path.exists(path):
            os.makedirs(CACHE, exist_ok=True)
            url = TEXT_URL.format(part="Torah" if name in TORAH else "Prophets", book=name)
            print("downloading", url)
            urllib.request.urlretrieve(url, path)
        with open(path, encoding="utf-8") as f:
            _books[name] = json.load(f)["text"]
    return _books[name]


def verses(ref):
    text = book(ref["k"])
    c1, v1 = map(int, ref["b"].split(":"))
    c2, v2 = map(int, ref["e"].split(":"))
    out = []
    for c in range(c1, c2 + 1):
        chapter = text[c - 1]
        first = v1 if c == c1 else 1
        last = v2 if c == c2 else len(chapter)
        out += [chapter[v - 1].strip() for v in range(first, last + 1)]
    return out


def aliyah_names(n, mincha):
    if mincha:
        return ["כהן", "לוי", "מפטיר"][:n]
    return ["כהן", "לוי", "ישראל"] if n == 3 else ["כהן", "לוי", "שלישי", "רביעי"][:n]


def main():
    table = json.loads(subprocess.check_output(["node", os.path.join(TOOLS, "leyning_table.mjs"), FROM, TO]))
    readings, index = [], {}
    names, name_index = [], {}

    def reading_id(entry, mincha):
        aliyot = entry["aliyot"]
        reading = {"aliyot": [
            {"name": n, "verses": verses(a)} for n, a in zip(aliyah_names(len(aliyot), mincha), aliyot)
        ]}
        if entry.get("haft"):
            reading["haftarah"] = [v for ref in entry["haft"] for v in verses(ref)]
        key = json.dumps(reading, ensure_ascii=False)
        if key not in index:
            index[key] = len(readings)
            readings.append(reading)
        return index[key]

    def name_id(entry):
        num = entry.get("parsha")
        if num:
            name = "פרשת " + "־".join(PARSHIYOT[n - 1] for n in ([num] if isinstance(num, int) else num))
        else:
            name = NIKUD.sub("", entry["name"].replace("־", " ")).replace("(מנחה)", "").strip()
            for a, b in HOLIDAY_SPELLING.items():
                name = name.replace(a, b)
        if name not in name_index:
            name_index[name] = len(names)
            names.append(name)
        return name_index[name]

    dates = {"il": {}, "chul": {}}
    parsha = {}
    for place, days in table.items():
        for date, day in days.items():
            out = {}
            for service, entry in day.items():
                rid = reading_id(entry, service == "mincha")
                out["s" if service == "shacharit" else "m"] = [rid, name_id(entry)]
                num = entry.get("parsha")
                if isinstance(num, list):
                    num = None  # combined parshiyot: the single ones cover every reading
                if num:
                    parsha[str(num)] = [rid, name_id(entry)]
            dates[place][date] = out

    assert len(parsha) == 54, sorted(map(int, parsha))
    data = {"from": FROM, "to": TO, "readings": readings, "names": names, "parsha": parsha, **dates}
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(f"wrote {OUT}: {len(readings)} readings, {len(dates['il'])}/{len(dates['chul'])} dates, "
          f"{os.path.getsize(OUT) // 1024} KB")


if __name__ == "__main__":
    main()
