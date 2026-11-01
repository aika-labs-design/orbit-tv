#!/usr/bin/env python3
"""Builds app/src/main/assets/countries.json and languages.json from the vendored sources in tools/data/src/.

    python3 tools/data/build_countries.py                    # regenerate both assets
    python3 tools/data/build_countries.py --trim cities15000.txt   # rewrite src/cities.tsv from a GeoNames dump

Sources (see src/README.txt for licences): GeoNames countryInfo, timeZones and cities, CLDR language names,
Netflix Top 10 country names, Google News editions checked by verify_news.py. Standard library only.
"""
import csv, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src")
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")

# Well-known film industries only. A language not listed gets "<Language> films" in the page.
FILM = {"yo": "Nollywood", "ig": "Nollywood", "ha": "Kannywood", "kn": "Sandalwood", "te": "Tollywood", "hi": "Bollywood",
        "ta": "Kollywood", "ml": "Mollywood", "ko": "K-drama", "pa": "Pollywood", "ur": "Lollywood", "or": "Ollywood", "gu": "Dhollywood"}

# Greetings (morning, afternoon, evening) only where each form is right for its window (05-12, 12-17, 17-05 in world-data.js,
# or the language's own hours in GREETING_HOURS). No entry means no greeting. Left out on purpose: languages whose evening form is a
# late-afternoon one (id, ms, fa, ur, th), whose afternoon form is not a greeting (zh), or with one form for two windows (ar, hi).
GREETING = {
    "en": ("Good morning", "Good afternoon", "Good evening"),
    "kn": ("ಶುಭೋದಯ", "ಶುಭ ಮಧ್ಯಾಹ್ನ", "ಶುಭ ಸಂಜೆ"),
    "te": ("శుభోదయం", "శుభ మధ్యాహ్నం", "శుభ సాయంత్రం"),
    "ta": ("காலை வணக்கம்", "மதிய வணக்கம்", "மாலை வணக்கம்"),
    "de": ("Guten Morgen", "Guten Tag", "Guten Abend"),
    "it": ("Buongiorno", "Buon pomeriggio", "Buonasera"),
    "nl": ("Goedemorgen", "Goedemiddag", "Goedenavond"),
    "ja": ("おはようございます", "こんにちは", "こんばんは"),
    "ko": ("좋은 아침입니다", "안녕하세요", "좋은 저녁입니다"),
    "ru": ("Доброе утро", "Добрый день", "Добрый вечер"),
    "uk": ("Доброго ранку", "Доброго дня", "Доброго вечора"),
    "tr": ("Günaydın", "İyi günler", "İyi akşamlar"),
    "pl": ("Dzień dobry", "Dzień dobry", "Dobry wieczór"),
    "sv": ("God morgon", "God eftermiddag", "God kväll"),
    "da": ("God morgen", "God eftermiddag", "God aften"),
    "no": ("God morgen", "God ettermiddag", "God kveld"),
    "fi": ("Hyvää huomenta", "Hyvää päivää", "Hyvää iltaa"),
    "cs": ("Dobré ráno", "Dobré odpoledne", "Dobrý večer"),
    "ro": ("Bună dimineața", "Bună ziua", "Bună seara"),
    "hu": ("Jó reggelt", "Jó napot", "Jó estét"),
    "el": ("Καλημέρα", "Καλό απόγευμα", "Καλησπέρα"),
    "he": ("בוקר טוב", "צהריים טובים", "ערב טוב"),
    "vi": ("Chào buổi sáng", "Chào buổi chiều", "Chào buổi tối"),
    "sw": ("Habari za asubuhi", "Habari za mchana", "Habari za jioni"),
    "yo": ("Ẹ kú àárọ̀", "Ẹ kú ọ̀sán", "Ẹ kú alẹ́"),
    "ig": ("Ụtụtụ ọma", "Ehihie ọma", "Mgbede ọma"),
    "ha": ("Barka da safiya", "Barka da rana", "Barka da yamma"),
    "es": ("Buenos días", "Buenas tardes", "Buenas noches"),
    "pt": ("Bom dia", "Boa tarde", "Boa noite"),
    "fr": ("Bonjour", "Bonjour", "Bonsoir"),
}
# Where a language's afternoon and evening start at other hours: "Buenas tardes" runs to 20:00, "Boa tarde" to 19:00, "Bonsoir" from 18:00.
GREETING_HOURS = {"es": (12, 20), "pt": (12, 19), "fr": (12, 18)}


def greeting(code):
    if code not in GREETING:
        return None
    g = dict(zip(("morning", "afternoon", "evening"), GREETING[code]))
    if code in GREETING_HOURS:
        g["afternoonFrom"], g["eveningFrom"] = GREETING_HOURS[code]
    return g

# Language codes are ISO 639-1, or ISO 639-3 where a language has no 639-1 code. Each extra: code, English name, native name,
# and the countries that list it after their 639-1 languages. Nigerian Pidgin (pcm) is how many Nigerians speak at home.
EXTRA_LANGUAGES = [("pcm", "Nigerian Pidgin", "Naijá", ["NG"])]

# One language, two codes: Bokmal and Nynorsk are Norwegian, Twi is Akan (CLDR names both "Akan"). The second code is never listed.
FOLD = {"nb": "no", "nn": "no", "tw": "ak"}

# GeoNames writes some names without their accents; these are the proper English names. The list keeps the GeoNames order.
PROPER_NAMES = {"AX": "Åland Islands", "CW": "Curaçao", "RE": "Réunion", "BL": "Saint Barthélemy", "ST": "São Tomé and Príncipe"}

# Common short words of a few Latin-script languages: enough for the app to tell which of a person's languages a headline is written in.
COMMON = {
    "en": "the of to in and on for at with is from as after by its",
    "es": "el la los las de del en y que por con para una un se tras",
    "pt": "o os as do da dos das em no na que com para uma um e",
    "fr": "le la les des du de en et que pour avec une un sur au aux",
    "de": "der die das und in mit von für auf ein eine im den dem nach",
    "it": "il lo la gli le di del della che per con una un nel sul",
}

RTL = {"ar", "he", "fa", "ur", "ps", "sd", "ug", "yi", "dv"}


def tsv(name):
    with open(os.path.join(SRC, name), encoding="utf8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def country_rows():
    rows = []
    for line in open(os.path.join(SRC, "countryInfo.txt"), encoding="utf8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        rows.append({"code": f[0], "name": f[4], "capital": f[5], "langs": f[15], "pop": int(f[7] or 0)})
    return rows


def lang_codes(raw, known):
    out = []
    for part in raw.split(","):
        c = part.split("-")[0]
        c = FOLD.get(c, c)
        if c in known and c not in out:
            out.append(c)
    return out


def trim_cities(dump):
    caps = {r["code"]: r["capital"] for r in country_rows()}
    by = {}
    for line in open(dump, encoding="utf8"):
        f = line.rstrip("\n").split("\t")
        by.setdefault(f[8], []).append((int(f[14] or 0), f[2], f[4], f[5], f[17]))
    with open(os.path.join(SRC, "cities.tsv"), "w", encoding="utf8") as out:
        out.write("code\tname\tlat\tlon\ttz\n")
        for cc in sorted(by):
            ranked = sorted(by[cc], key=lambda c: -c[0])
            keep = ranked[:5]
            cap = next((c for c in ranked if caps.get(cc) and c[1] == caps[cc]), None)
            if cap and cap not in keep:
                keep.append(cap)
            for _, name, lat, lon, tz in keep:
                out.write(f"{cc}\t{name}\t{round(float(lat), 2)}\t{round(float(lon), 2)}\t{tz}\n")


LATIN = re.compile(r"^[A-Za-z\u00C0-\u024F' .()-]+$")


def city_names(dump, admin1_file):
    """src/city-names.tsv: for each city in cities.tsv, its first-level region and its other Latin-script names (München, Bangalore),
    from GeoNames cities15000.txt and admin1CodesASCII.txt. Codes (MUC), lower-case transliterations and very long names are left out; at most 80 per city."""
    admin = {}
    for line in open(admin1_file, encoding="utf8"):
        f = line.rstrip("\n").split("\t")
        if len(f) >= 2:
            admin[f[0]] = f[1]
    want = {(r["code"], r["name"]) for r in tsv("cities.tsv")}
    out = {}
    for line in open(dump, encoding="utf8"):
        f = line.rstrip("\n").split("\t")
        key = (f[8], f[2])
        if key not in want or key in out:
            continue
        seen, names = {f[2].lower()}, []   # f[2] is the ASCII name cities.tsv uses, f[1] the name with its accents, f[3] the alternates
        for a in [f[1]] + [x.strip() for x in f[3].split(",")]:
            if not a or len(a) > 40 or not a[0].isupper() or not LATIN.match(a) or (a.isupper() and len(a) <= 4) or a.lower() in seen:
                continue
            seen.add(a.lower()); names.append(a)
        out[key] = (admin.get(f[8] + "." + f[10], ""), names[:80])
    with open(os.path.join(SRC, "city-names.tsv"), "w", encoding="utf8") as o:
        o.write("code\tname\tregion\tnames\n")
        for (cc, name), (region, names) in sorted(out.items()):
            o.write(f"{cc}\t{name}\t{region}\t{'|'.join(names)}\n")


def build():
    langs = [r for r in tsv("languages.tsv") if r["code"] not in FOLD] + [{"code": c, "name": n, "native": nat} for c, n, nat, _ in EXTRA_LANGUAGES]
    known = {r["code"] for r in langs}
    zones, cities = {}, {}
    for r in tsv("timeZones.txt"):
        zones.setdefault(r["CountryCode"], []).append(r["TimeZoneId"])
    for r in tsv("cities.tsv"):
        cities.setdefault(r["code"], []).append({"name": r["name"], "lat": float(r["lat"]), "lon": float(r["lon"]), "tz": r["tz"]})
    news = {r["code"]: {"hl": r["hl"], "gl": r["gl"], "ceid": r["ceid"]} for r in tsv("news-editions.tsv")}
    netflix = {r["code"]: r["netflixName"] for r in tsv("netflix-countries.tsv")}
    rows = country_rows()
    native = {}
    for r in tsv("country-names.tsv"):
        native.setdefault(r["code"], []).append(r["name"])
    tz_of = {r["code"]: set(zones.get(r["code"]) or {c["tz"] for c in cities.get(r["code"], [])}) for r in rows}
    # A zone shared by two countries (Europe/Belgrade: Serbia and Kosovo) belongs to the more populous one, so guessLiving is stable.
    for r in sorted(rows, key=lambda r: -r["pop"]):
        for other in rows:
            if other["code"] != r["code"] and other["pop"] < r["pop"]:
                tz_of[other["code"]] -= tz_of[r["code"]]
    countries = []
    for r in sorted(rows, key=lambda r: r["name"]):
        cc = r["code"]
        tzs = sorted(tz_of[cc])
        if not tzs and not cities.get(cc):
            continue   # retired or uninhabited entries (Netherlands Antilles, Bouvet Island...)
        extra = [c for c, _, _, ccs in EXTRA_LANGUAGES if cc in ccs]
        countries.append({"code": cc, "name": PROPER_NAMES.get(cc, r["name"]), "names": native.get(cc, []), "languages": lang_codes(r["langs"], known) + extra, "timezones": tzs,
                          "cities": cities.get(cc, []), "news": news.get(cc), "netflixName": netflix.get(cc)})
    languages = [{"code": r["code"], "name": r["name"], "native": r["native"], "film": FILM.get(r["code"]),
                  "greeting": greeting(r["code"]),
                  "rtl": r["code"] in RTL, "common": COMMON.get(r["code"], "").split()} for r in langs]
    # Home news: each city's region and other names, read only by the app (pages never load it). Code -> city -> {region, names}.
    places, common_words = {}, {w for ws in COMMON.values() for w in ws.split()}
    for r in tsv("city-names.tsv"):
        # a short alias (Vin, Bec, Pa) or one that is a common word in some language reads as an ordinary word in headlines: left out
        names = [n for n in r["names"].split("|") if len(n) > 3 and n.lower() not in common_words]
        places.setdefault(r["code"], {})[r["name"]] = {"region": r["region"], "names": names}
    for name, data in (("countries.json", countries), ("languages.json", languages), ("place-names.json", places)):
        with open(os.path.join(ASSETS, name), "w", encoding="utf8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
    print(f"{len(countries)} countries, {len(languages)} languages", file=sys.stderr)


if __name__ == "__main__":
    if len(sys.argv) == 4 and sys.argv[1] == "--city-names":
        city_names(sys.argv[2], sys.argv[3])
        sys.exit(0)
    if len(sys.argv) == 3 and sys.argv[1] == "--trim":
        trim_cities(sys.argv[2])
    else:
        build()
