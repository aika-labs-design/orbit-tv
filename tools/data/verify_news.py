#!/usr/bin/env python3
"""Finds the Google News editions that really exist and writes src/news-editions.tsv (code, hl, gl, ceid).

Google answers every hl/gl/ceid with HTTP 200 and quietly serves another edition (usually en-US) when the one asked
for does not exist. So a candidate counts only if it returns at least one <item> AND the feed's own <link> reports
the same country (gl) that was asked for. The reported hl and ceid are what is stored.
Network needed; run rarely: python3 tools/data/verify_news.py
"""
import html, os, re, sys, time, urllib.request

SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src")
# Contact for the User-Agent (Wikimedia asks for one): ORBIT_CONTACT in the environment, else the project page.
CONTACT = os.environ.get("ORBIT_CONTACT", "").strip() or "https://github.com/aika-labs-design/orbit-tv"
UA = "Orbit/1.0 (" + CONTACT + ")"


def countries():
    out = []
    for line in open(os.path.join(SRC, "countryInfo.txt"), encoding="utf8"):
        if line.startswith("#") or not line.strip():
            continue
        f = line.rstrip("\n").split("\t")
        langs = [l.split("-")[0] for l in f[15].split(",") if l]
        out.append((f[0], [l for l in langs if len(l) == 2]))
    return out


def candidates(cc, langs):
    seen = []
    for l in langs[:2]:
        for hl in ([f"{l}-{cc}", l] + (["es-419"] if l == "es" else [])):
            if (hl, f"{cc}:{hl.split('-')[0] if hl != 'es-419' else 'es-419'}") not in seen:
                seen.append((hl, f"{cc}:{hl.split('-')[0] if hl != 'es-419' else 'es-419'}"))
    seen.append((f"en-{cc}", f"{cc}:en"))
    return seen


def probe(hl, gl, ceid):
    url = f"https://news.google.com/rss?hl={hl}&gl={gl}&ceid={ceid}"
    try:
        r = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=30)
        body = r.read().decode("utf8", "replace")
    except Exception:
        return None
    if r.status != 200 or "<item>" not in body:
        return None
    link = html.unescape(re.search(r"<link>([^<]*)</link>", body).group(1))
    m = re.search(r"hl=([^&]+)&gl=([^&]+)&ceid=(\S+)", link)
    if not m or m.group(2) != gl or not m.group(3).startswith(gl + ":"):
        return None
    return m.group(1), m.group(2), m.group(3)


def main():
    rows = []
    for cc, langs in countries():
        for hl, ceid in candidates(cc, langs):
            got = probe(hl, cc, ceid)
            time.sleep(0.25)
            if got:
                rows.append((cc,) + got)
                break
    with open(os.path.join(SRC, "news-editions.tsv"), "w", encoding="utf8") as f:
        f.write("code\thl\tgl\tceid\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(f"{len(rows)} verified editions", file=sys.stderr)


if __name__ == "__main__":
    main()
