"""
Reads pages of Birnbaum's "HaSiddur HaShalem" (1949, Nusach Ashkenaz) from Hebrew Wikisource,
where it is transcribed with full nikud. Used for texts the Sefaria siddur export lacks or has
without full vocalization (Musaf for Chol Hamoed, Brit Milah, Sheva Brachot, haftarah blessings).
"""
import html
import os
import re
import urllib.parse
import urllib.request

CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache", "birnbaum")
BASE = "https://he.wikisource.org/w/index.php?title="
PREFIX = "הסידור השלם (בירנבוים)/אשכנז/"

# Places where the transcription joins two words or lines without a space
FIXUPS = {
    "עָלֵֽינוּוְעַל": "עָלֵֽינוּ וְעַל",
    "עָשִֽׂיתִי.שִׂבַּֽרְתִּי": "עָשִֽׂיתִי. שִׂבַּֽרְתִּי",
    "בַּצָּרָהאֵל": "בַּצָּרָה, אֵל",
    "פְּרוּשָׁהצִוָּה": "פְּרוּשָׁה, צִוָּה",
    "הָעִבְרִיִּיםאֲהוֹדֶֽנּוּ": "הָעִבְרִיִּים, אֲהוֹדֶֽנּוּ",
    "וּשְׂפָתַיוְתֹאמַֽרְנָה": "וּשְׂפָתַי, וְתֹאמַֽרְנָה",
}

# Birnbaum writes the Divine name as יְיָ; the rest of the siddur uses יְהֹוָה
NAMES = {"לַייָ": "לַיהֹוָה", "בַּייָ": "בַּיהֹוָה", "יְיָ": "יְהֹוָה"}

CANTILLATION = re.compile(r"[֑-֯]")


def _fetch(title):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, re.sub(r"[^\w]+", "_", title) + ".html")
    if not os.path.exists(path):
        url = BASE + urllib.parse.quote(PREFIX + title) + "&action=render"
        req = urllib.request.Request(url, headers={"User-Agent": "SmartSiddurBuild/0.1 (siddur content build)"})
        print("downloading", url)
        with urllib.request.urlopen(req) as r, open(path, "wb") as f:
            f.write(r.read())
    with open(path, encoding="utf-8") as f:
        return f.read()


def lines(title):
    """The page as a list of clean text lines (bold kept as <b>)."""
    s = _fetch(title)
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", "", s, flags=re.S)
    s = re.sub(r'<sup[^>]*class="reference"[^>]*>.*?</sup>', "", s, flags=re.S)  # footnote marks
    s = re.sub(r"<b\b[^>]*>", "⟨b⟩", s)
    s = s.replace("</b>", "⟨/b⟩")
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"</(p|div|h\d|dd|li|tr)>", "\n", s)
    t = html.unescape(re.sub(r"<[^>]+>", "", s))
    for a, b in FIXUPS.items():
        t = t.replace(a, b)
    for a, b in NAMES.items():
        t = t.replace(a, b)
    t = CANTILLATION.sub("", t)
    t = t.replace("⟨b⟩", "<b>").replace("⟨/b⟩", "</b>")
    out = []
    for line in t.split("\n"):
        line = re.sub(r"\[\d+\]", "", line).strip()
        line = re.sub(r"<b>\s*</b>", "", line).strip()
        if line:
            out.append(line)
    return out


def section(title, start, end=None):
    """Lines from the first starting with `start` up to (not including) one starting with `end`."""
    ls = lines(title)
    i = next(i for i, l in enumerate(ls) if plain(l).startswith(start))
    j = next((k for k in range(i + 1, len(ls)) if end and plain(ls[k]).startswith(end)), len(ls))
    return ls[i:j]


def find(title, start):
    """The first line starting with `start`."""
    return next(l for l in lines(title) if plain(l).startswith(start))


def plain(s):
    return re.sub(r"<[^>]+>", "", s).strip()
