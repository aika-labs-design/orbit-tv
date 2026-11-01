#!/usr/bin/env python3
"""Builds app/src/main/assets/interests.json from catalogue_data.py and verifies it against the live sources.

    python3 tools/interests/build_catalogue.py            # resolve, verify, write the asset
    python3 tools/interests/build_catalogue.py --check    # verify only (no write)

Needs the TMDB key from the git-ignored local.properties (tmdb.apiKey); the key is read at run time, never printed or
written anywhere. Verification: every TMDB keyword resolves by an exact name, every genre id exists, every ESPN league
answers HTTP 200 and every espnTeam id is a team of its league, every image URL answers 200. Standard library only.
"""
import json, os, re, sys, time, urllib.parse, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
from catalogue_data import CATALOGUE, MIN_BY_THEME  # noqa: E402
import fetch_images  # noqa: E402

OUT = os.path.join(ROOT, "app", "src", "main", "assets", "interests.json")


def tmdb_key():
    with open(os.path.join(ROOT, "local.properties")) as f:
        for line in f:
            m = re.match(r"\s*tmdb\.apiKey\s*=\s*(\S+)", line)
            if m:
                return m.group(1)
    sys.exit("tmdb.apiKey missing in local.properties")


KEY = tmdb_key()


def tmdb(path, **q):
    url = "https://api.themoviedb.org/3/" + path + "?" + urllib.parse.urlencode(dict(q, api_key=KEY))
    for i in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": fetch_images.UA}), timeout=20) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(2 + i)
            else:
                sys.exit("TMDB %s failed with HTTP %s" % (path, e.code))  # never echo the url
        except Exception:
            time.sleep(1 + i)
    sys.exit("TMDB %s unreachable" % path)


def espn_ok(league):
    try:
        with urllib.request.urlopen("https://site.api.espn.com/apis/site/v2/sports/%s/scoreboard" % league, timeout=20) as r:
            return r.status == 200
    except Exception:
        return False


_teams = {}


def espn_team_ids(league):
    if league not in _teams:
        with urllib.request.urlopen("https://site.api.espn.com/apis/site/v2/sports/%s/teams" % league, timeout=20) as r:
            d = json.load(r)
        _teams[league] = {t["team"]["id"]: t["team"]["displayName"] for t in d["sports"][0]["leagues"][0]["teams"]}
    return _teams[league]


def main():
    check_only = "--check" in sys.argv
    problems = []
    genre_ids = {g["id"] for k in ("movie", "tv") for g in tmdb("genre/%s/list" % k)["genres"]}
    movie_genres = {g["id"] for g in tmdb("genre/movie/list")["genres"]}
    kw_cache, out = {}, []
    leagues_seen = set()
    for e in CATALOGUE:
        ent = {"id": e["id"], "name": e["name"], "theme": e["theme"]}
        if e["also"]:
            ent["also"] = e["also"]
        if e["cc"]:
            ent["countries"] = e["cc"]
        img = fetch_images.thumb(e["wiki"])
        if not img or not fetch_images.reachable(img):
            problems.append("image: %s (%s)" % (e["id"], e["wiki"]))
            img = ""
        ent["image"] = img
        ent["terms"] = e["terms"]
        if e["kw"] or e["g"]:
            ids = []
            for k in e["kw"]:
                if k not in kw_cache:
                    res = tmdb("search/keyword", query=k)["results"]
                    hit = next((r for r in res if r["name"].lower() == k.lower()), None)
                    kw_cache[k] = hit["id"] if hit else None
                    if not hit:
                        problems.append("keyword: %s (%s) candidates: %s" % (e["id"], k, [r["name"] for r in res[:4]]))
                if kw_cache[k] is not None and kw_cache[k] not in ids:
                    ids.append(kw_cache[k])
            for g in e["g"]:
                if g not in movie_genres or g not in genre_ids:
                    problems.append("genre: %s %s" % (e["id"], g))
            ent["shows"] = {"keywords": ids, "genres": e["g"]}
        ent["news"] = {"query": e["n"] or e["name"]}
        if e["v"]:
            ent["videos"] = {"query": e["v"], "minMinutes": e["mins"] or min([MIN_BY_THEME.get(t, 8) for t in [e["theme"]] + e["also"]])}
        if e["live"]:
            ent["live"] = e["live"]
            if e["live"]["kind"] == "espn":
                for lg in e["live"]["league"].split(","):
                    leagues_seen.add(lg)
        if e["refine"]:
            opts = []
            for o in e["refine"][1]:
                for lg in [o.get("league")]:
                    if lg:
                        leagues_seen.add(lg)
                        if o.get("espnTeam") and o["espnTeam"] not in espn_team_ids(lg):
                            problems.append("espnTeam: %s %s not in %s" % (o["id"], o["espnTeam"], lg))
                opts.append(o)
            ent["refine"] = {"label": e["refine"][0], "options": opts}
        out.append(ent)
        time.sleep(0.05)
    for lg in sorted(leagues_seen):
        if not espn_ok(lg):
            problems.append("espn league: " + lg)
    print("entries %d, keywords resolved %d, leagues checked %d" % (len(out), len([v for v in kw_cache.values() if v]), len(leagues_seen)))
    for p in problems:
        print("PROBLEM", p)
    if problems:
        sys.exit(1)
    if not check_only:
        with open(OUT, "w") as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("wrote", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
