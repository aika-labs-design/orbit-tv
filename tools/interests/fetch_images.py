#!/usr/bin/env python3
"""Wikipedia tile photos for the interest catalogue.

Wikipedia's REST summary gives a thumbnail URL; Wikimedia only serves standard widths, so it is rewritten to 500 px
(400 is refused). Used by build_catalogue.py; can also be run alone to list entries whose image is missing:

    python3 tools/interests/fetch_images.py "Association football" "Jazz"
"""
import json, os, re, sys, time, urllib.parse, urllib.request

# Contact for the User-Agent (Wikimedia asks for one): ORBIT_CONTACT in the environment, else the project page.
CONTACT = os.environ.get("ORBIT_CONTACT", "").strip() or "https://github.com/aika-labs-design/orbit-tv"
UA = "Orbit/1.0 (" + CONTACT + ")"


def get_json(url, tries=3):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=20) as r:
                return json.load(r)
        except Exception:
            time.sleep(1 + i)
    return None


def thumb(title):
    """500 px thumbnail URL for a Wikipedia article title, or None."""
    d = get_json("https://en.wikipedia.org/api/rest_v1/page/summary/" + urllib.parse.quote(title.replace(" ", "_"), safe=""))
    src = ((d or {}).get("thumbnail") or {}).get("source")
    if not src:
        return None
    src = src.split("?")[0]
    if not re.search(r"/\d+px-", src):
        return None  # an original file, possibly 4000 px: too heavy for this TV
    return re.sub(r"/\d+px-", "/500px-", src)


def reachable(url):
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA}, method="HEAD")
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status == 200
    except Exception:
        return False


if __name__ == "__main__":
    for t in sys.argv[1:]:
        u = thumb(t)
        print(t, "->", u, "(ok)" if u and reachable(u) else "(MISSING)")
        time.sleep(0.2)
