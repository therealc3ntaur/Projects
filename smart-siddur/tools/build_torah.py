#!/usr/bin/env python3
"""
Builds app/src/main/assets/siddur/torah_weekday.json: the weekday (Monday/Thursday) Torah
reading for every parsha, split into the three aliyot.

Sources:
  - Aliyah divisions: the "weekday" readings in Hebcal's @hebcal/leyning (BSD-2-Clause).
  - Text: Sefaria's "Tanach with Nikkud" (vowels, no cantillation).

Usage:
    python3 tools/build_torah.py
"""
import io
import json
import os
import re
import tarfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "siddur", "torah_weekday.json")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")

LEYNING_TGZ = "https://registry.npmjs.org/@hebcal/leyning/-/leyning-10.1.0.tgz"
TORAH_URL = ("https://storage.googleapis.com/sefaria-export/json/Tanakh/Torah/{book}/Hebrew/"
             "Tanach%20with%20Nikkud.json")
BOOKS = ["Genesis", "Exodus", "Leviticus", "Numbers", "Deuteronomy"]

# Parsha names in order (1 = Bereshit ... 54 = Vezot Haberakhah)
NAMES = [
    "בראשית", "נח", "לך לך", "וירא", "חיי שרה", "תולדות", "ויצא", "וישלח", "וישב", "מקץ",
    "ויגש", "ויחי", "שמות", "וארא", "בא", "בשלח", "יתרו", "משפטים", "תרומה", "תצוה",
    "כי תשא", "ויקהל", "פקודי", "ויקרא", "צו", "שמיני", "תזריע", "מצורע", "אחרי מות", "קדושים",
    "אמור", "בהר", "בחקתי", "במדבר", "נשא", "בהעלתך", "שלח", "קרח", "חקת", "בלק",
    "פינחס", "מטות", "מסעי", "דברים", "ואתחנן", "עקב", "ראה", "שופטים", "כי תצא", "כי תבוא",
    "נצבים", "וילך", "האזינו", "וזאת הברכה",
]
ALIYAH_NAMES = {"1": "כהן", "2": "לוי", "3": "ישראל"}


def fetch(url, name):
    path = os.path.join(CACHE, name)
    if not os.path.exists(path):
        os.makedirs(CACHE, exist_ok=True)
        print("downloading", url)
        urllib.request.urlretrieve(url, path)
    return path


def load_aliyot():
    with tarfile.open(fetch(LEYNING_TGZ, "leyning.tgz")) as tar:
        js = tar.extractfile("package/dist/esm/aliyot.json.js").read().decode()
    body = re.sub(r"^\s*export default\s*", "", js)
    body = body[: body.rindex("}") + 1]
    return json.loads(body)


def verses(text, start, end):
    """Verses from "ch:v" to "ch:v" inclusive."""
    c1, v1 = map(int, start.split(":"))
    c2, v2 = map(int, end.split(":"))
    out = []
    for c in range(c1, c2 + 1):
        chapter = text[c - 1]
        first = v1 if c == c1 else 1
        last = v2 if c == c2 else len(chapter)
        out += [chapter[v - 1].strip() for v in range(first, last + 1)]
    return out


def main():
    torah = []
    for book in BOOKS:
        with open(fetch(TORAH_URL.format(book=book), f"{book}.json"), encoding="utf-8") as f:
            torah.append(json.load(f)["text"])

    parshiyot = []
    for key, p in load_aliyot().items():
        if "weekday" not in p or not isinstance(p.get("num"), int):
            continue  # combined parshiyot read the first one's weekday portion
        text = torah[p["book"] - 1]
        aliyot = []
        for n, (b, e) in sorted(p["weekday"].items()):
            aliyot.append({"name": ALIYAH_NAMES[n], "verses": verses(text, b, e)})
        parshiyot.append({"num": p["num"], "name": NAMES[p["num"] - 1], "aliyot": aliyot})

    parshiyot.sort(key=lambda x: x["num"])
    assert len(parshiyot) == 54, len(parshiyot)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"parshiyot": parshiyot}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"wrote {OUT}: {len(parshiyot)} parshiyot")


if __name__ == "__main__":
    main()
