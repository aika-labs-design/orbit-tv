package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

object FeedUrls {
    const val CRICINFO = "https://static.cricinfo.com/rss/livescores.xml"
    const val ESPN_F1 = "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard"
    const val ESPN_ATP = "https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard"
    const val ESPN_WTA = "https://site.api.espn.com/apis/site/v2/sports/tennis/wta/scoreboard"
    const val JOLPICA_NEXT = "https://api.jolpi.ca/ergast/f1/current/next.json"
    const val JOLPICA_STANDINGS = "https://api.jolpi.ca/ergast/f1/current/driverStandings.json"
    const val THERACE_MOTOGP = "https://www.the-race.com/category/motogp/rss/"
    fun espnCricket(leagueId: String) = "https://site.api.espn.com/apis/site/v2/sports/cricket/$leagueId/scoreboard"
}

/**
 * Fetches every source, keeps the last good body per source on disk, parses into cards and writes feeds.json for the page.
 * A fetch that fails or parses to nothing leaves the previous body in place, so cards go stale rather than blank.
 */
class FeedRefresher(
    private val config: FeedConfig, private val store: FeedStore, private val fetch: (String) -> String?,
    /** Curated live channels per home country (assets/channels/<CC>.json). */
    private val channels: Map<String, List<Channel>> = emptyMap(),
    /** Bundled holidays per country (assets/festivals/<CC>.json), used when Nager.Date has none for it. */
    private val festivals: Map<String, FestivalBook> = emptyMap(),
    private val tmdbKey: String = "", private val installed: () -> Set<String> = { emptySet() },
    private val onSourceFailed: (String) -> Unit = {},
    private val loveFeeds: LoveFeeds? = null, private val profiles: () -> List<Profile> = { emptyList() },
    private val interests: List<Interest> = emptyList(), private val countries: List<Country> = emptyList(),
    private val languages: List<Language> = emptyList(),
    private val handoffs: Handoffs = Handoffs.NONE, private val providerApps: ProviderApps = ProviderApps.NONE,
    /** For the daily "<country> live news" search of a home country without a channel file; counted in [quota] within the loves' share. */
    private val youtubeKey: String = "", private val quota: YouTubeQuota? = null,
    private val now: () -> Long = System::currentTimeMillis,
    /** Other names of home places and countries for the home news gate (assets/place-names.json, web/aliases.json). */
    private val placeNames: PlaceNames = PlaceNames.NONE,
    /** Line streaming for the channels' /live pages ([Channels.readLive]); without it the page is fetched whole. */
    private val stream: ((String, (String) -> Boolean) -> Boolean)? = null,
) {
    private val channelStates = HashMap<String, ChannelState>()
    private val searched = HashMap<String, List<Pair<Channel, ChannelState>>>()   // home country -> channels from its live search
    private var tonight: List<Pair<TopTitle, Poster?>> = emptyList()
    private var tonightCode: String? = null   // the living country [tonight] belongs to
    private data class Cached<T>(val value: T, val checkedMs: Long)
    private val posters = HashMap<String, Cached<Poster>>()   // title -> poster (image may be null), persisted in posters.v2.json
    private val places = HashMap<String, Cached<String>>()    // venue -> photograph url ("" when none), persisted in places.v2.json
    private val nagerTried = HashMap<String, Long>()           // holiday file -> last failed attempt, so a failing year is asked once a day
    val tonightSavedAt: Long? get() = livingCountry()?.let { store.savedAt("tonight_${safe(it.code)}.tsv") }
    @Volatile var country: String = ""; private set
    @Volatile var anyLive = false; private set
    private val fetchedAt = HashMap<String, Long>()   // source file name -> time its body was fetched for real
    private val failing = HashSet<String>()
    private var cricketLive: List<CricketScore> = emptyList()
    private var cricketSeries: Map<String, Series> = emptyMap()
    private var cricketEvents: Map<String, List<SportEvent>> = emptyMap()
    private var f1Next: Race? = null
    private var f1Sessions: List<SportEvent> = emptyList()
    private var f1Standings: List<Standing> = emptyList()
    private var motogpNews: List<RssItem> = emptyList()
    private var tennis: List<SportEvent> = emptyList()
    private var espnEvents: Map<String, List<SportEvent>> = emptyMap()
    private var homeWeather: Map<String, List<PlaceWeather>> = emptyMap()
    private var homeNews: Map<String, List<List<RssItem>>> = emptyMap()   // per home, one list per news language in the person's order
    private var stations: Map<String, List<Station>> = emptyMap()
    private var loves = JSONObject()   // profile id -> love id -> items; seeded from the last page so a restart keeps them

    init {
        parseLive(cached = true); parseHomes(cached = true); parseStations(cached = true)
        runCatching {
            val pj = JSONObject(store.load("posters.v2.json") ?: "{}"); pj.keys().forEach { k -> val o = pj.getJSONObject(k); posters[k] = Cached(Poster(o.optString("image").ifBlank { null }, o.optString("blurb").ifBlank { null }), o.optLong("checkedMs")) }
            val pl = JSONObject(store.load("places.v2.json") ?: "{}"); pl.keys().forEach { k -> val o = pl.getJSONObject(k); places[k] = Cached(o.optString("image"), o.optLong("checkedMs")) }
        }
        runCatching { loves = JSONObject(store.pageJson() ?: "{}").optJSONObject("loves") ?: JSONObject() }
        // Channel resolutions survive a restart: seed from the last page written.
        runCatching {
            val arr = JSONObject(store.pageJson() ?: "{}").optJSONArray("channels")
            if (arr != null) for (i in 0 until arr.length()) { val c = arr.getJSONObject(i); val id = c.optString("id"); val vid = c.optString("url").substringAfter("watch?v=", ""); if (vid.isNotBlank()) channelStates[id] = ChannelState(id, c.optBoolean("live"), vid, c.optString("title").ifBlank { null }, Channels.thumb(vid), c.optLong("resolvedMs")) }
        }
    }

    /** Fetch one source: save the body only when it parses to something, otherwise reuse the stored body. Returns the parsed count. */
    private fun <T> source(name: String, url: String, parse: (String) -> List<T>, cached: Boolean): List<T> {
        if (!cached) {
            val body = fetch(url)
            val parsed = body?.let { b -> runCatching { parse(b) }.getOrDefault(emptyList()) } ?: emptyList()
            if (parsed.isNotEmpty()) { store.save(name, body!!); fetchedAt[name] = now(); if (failing.remove(name)) onSourceFailed("$name recovered"); return parsed }
            if (failing.add(name)) onSourceFailed(if (body == null) "$name fetch failed, using cached body" else "$name parsed to nothing, using cached body")
        }
        if (!fetchedAt.containsKey(name)) store.savedAt(name)?.let { fetchedAt[name] = it }
        return store.load(name)?.let { runCatching { parse(it) }.getOrDefault(emptyList()) } ?: emptyList()
    }
    private fun fetchedFor(vararg names: String): Long? = names.mapNotNull { fetchedAt[it] }.minOrNull()

    // ---------- who is in the house: everything below follows the profiles, and no profiles means no homes, sport or country ----------
    /** One refresh pass reads profiles.json once and builds the homes once ([pass]); outside a pass every call reads afresh. */
    private class Pass(val people: List<Profile>) { var homes: List<HomePack>? = null }
    @Volatile private var current: Pass? = null
    fun <T> pass(block: () -> T): T {
        current = Pass(readPeople())
        try { return block() } finally { current = null }
    }
    private fun readPeople(): List<Profile> = runCatching { profiles() }.getOrDefault(emptyList())
    private fun people(): List<Profile> = current?.people ?: readPeople()
    private fun countryOf(code: String): Country? = countries.firstOrNull { it.code == code }
    /** The streaming country (Netflix Top 10): where the first profile lives. */
    private fun livingCountry(): Country? = people().firstOrNull()?.let { countryOf(it.living.country) }
    /** Hand-offs are for where the TV is seen from, else where the household lives; never a fixed country. */
    private fun handoffCountry(): String = country.ifBlank { people().firstOrNull()?.living?.country ?: "" }
    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")
    private fun homes(): List<HomePack> {
        val p = current ?: return buildHomes(readPeople())
        return p.homes ?: buildHomes(p.people).also { p.homes = it }
    }
    /** New holidays were saved during this pass: the homes built earlier in it carry the old festivals, so they are built again. */
    private fun invalidateHomes() { current?.homes = null }
    private fun buildHomes(ps: List<Profile>): List<HomePack> = ps.map { p -> Homes.fromProfile(p, countries, languages, holidays(p.home.country, p.languages), p.home.country in channels) }
    private fun year(): Int = java.time.Instant.ofEpochMilli(now()).atZone(java.time.ZoneOffset.UTC).year
    private fun espnFile(league: String) = "espn_${safe(league)}.json"

    /**
     * Festival days for a home country: this year's and next year's public holidays from Nager.Date, cached for good once fetched. When Nager
     * has nothing for the country (India answers 204), the bundled calendar for it, with the person's languages' regional days. Never another country's.
     */
    private fun holidays(cc: String, langs: List<String>): List<Festival> =
        listOf(year(), year() + 1).flatMap { y -> store.load("nager_${safe(cc)}_$y.json")?.let { Nager.parse(it) } ?: emptyList() }.distinct().sortedBy { it.date }
            .ifEmpty { festivals[cc]?.forLanguages(langs) ?: emptyList() }
    private fun fetchHolidays(cc: String) {
        for (y in listOf(year(), year() + 1)) {
            val name = "nager_${safe(cc)}_$y.json"
            if (store.load(name) != null || nagerTried[name]?.let { now() - it in 0 until DAY } == true) continue
            val body = fetch(Nager.url(y, safe(cc)))
            if (body != null && Nager.parse(body) != null) { store.save(name, body); invalidateHomes() } else nagerTried[name] = now()
        }
    }

    /** Only the sports someone loves are fetched. */
    private fun parseLive(cached: Boolean) {
        val p = SportPrefs.of(people(), interests)
        val cricket = "cricket" in p.sports; val f1 = "f1" in p.sports
        cricketLive = if (!cricket) emptyList() else source("cricinfo.xml", FeedUrls.CRICINFO, { Rss.parse(it).mapNotNull { i -> CricinfoLive.parseTitle(i.title, i.link) } }, cached)
        cricketSeries = if (!cricket) emptyMap() else source("espn_cricket_header.json", CricketSeries.URL, CricketSeries::parseHeader, cached).toMap()
        cricketEvents = if (!cricket) emptyMap() else config.cricketLeagues.associate { l -> l.id to source("espn_${l.id}.json", FeedUrls.espnCricket(l.id), EspnScoreboard::events, cached) }
        f1Sessions = if (!f1) emptyList() else source("espn_f1.json", FeedUrls.ESPN_F1, EspnScoreboard::f1Sessions, cached)
        f1Next = if (!f1) null else source("jolpica_next.json", FeedUrls.JOLPICA_NEXT, { listOfNotNull(Jolpica.nextRace(it)) }, cached).firstOrNull()
        f1Standings = if (!f1) emptyList() else source("jolpica_standings.json", FeedUrls.JOLPICA_STANDINGS, Jolpica::driverStandings, cached)
        motogpNews = if ("motogp" !in p.sports) emptyList() else source("therace_motogp.xml", FeedUrls.THERACE_MOTOGP, Rss::parse, cached)
        tennis = (if ("tennis/atp" in p.tennisLeagues) source("espn_atp.json", FeedUrls.ESPN_ATP, EspnScoreboard::tennisMatches, cached) else emptyList()) +
            (if ("tennis/wta" in p.tennisLeagues) source("espn_wta.json", FeedUrls.ESPN_WTA, EspnScoreboard::tennisMatches, cached) else emptyList())
        espnEvents = p.espnLeagues.associateWith { lg -> source(espnFile(lg), "https://site.api.espn.com/apis/site/v2/sports/$lg/scoreboard", EspnScoreboard::events, cached) }
        anyLive = liveCards().any { it.state == CardState.LIVE }
    }

    /**
     * Home Mode, per profile: weather at the home places, news about them in the person's languages and the home country's edition, and the home
     * country's holidays. Stored like every other source, so a bad fetch keeps the last good body.
     */
    private fun parseHomes(cached: Boolean) {
        if (!cached) people().map { it.home.country }.distinct().forEach(::fetchHolidays)
        val hs = homes()
        homeWeather = hs.associate { h -> h.id to source("home_${safe(h.id)}_weather.json", HomeWeather.url(h.places), { HomeWeather.parse(it, h.places) }, cached) }
        homeNews = hs.associate { h -> h.id to h.queries.mapIndexed { i, q -> source("home_${safe(h.id)}_news_$i.xml", World.newsUrl(q.q, h.country, q.lang), Rss::parse, cached) } }
    }
    /** Radio in each person's main language, the home country's stations first; people sharing a language share the request. */
    private fun parseStations(cached: Boolean) {
        val hs = homes()
        val byLang = hs.map { it.radioLanguage }.distinct().associateWith { l -> source("radio_${safe(l)}.json", RadioBrowser.url(l), { RadioBrowser.parse(it, max = 30) }, cached) }
        stations = hs.associate { h -> h.id to (byLang[h.radioLanguage] ?: emptyList()).sortedBy { if (it.country == h.country?.code) 0 else 1 }.take(8) }
    }
    fun refreshStations(): Boolean { parseStations(cached = false); return stations.values.any { it.isNotEmpty() } }
    /**
     * Cinema catalogue: per profile, TMDB films and series in each of its languages (main language first) with logo, trailer keys and the
     * providers of the country the person lives in. Details are cached per title for 7 days (at most 150 lookups per refresh), with providers
     * kept for every living country in the house. Without a key or a profile nothing is requested.
     */
    fun refreshCatalog(): Boolean {
        val ps = people()
        if (tmdbKey.isBlank() || ps.isEmpty()) return false
        val living = ps.map { it.living.country }.distinct()
        val cache = HashMap<String, Pair<Detail, Long>>()
        runCatching { val o = JSONObject(store.load(DETAILS) ?: "{}"); o.keys().forEach { k -> val d = o.getJSONObject(k)
            val pr = d.optJSONObject("providers")?.let { m -> m.keys().asSequence().associateWith { c -> m.getJSONArray(c).let { a -> List(a.length()) { a.getString(it) } } } } ?: emptyMap()
            val lg = d.optJSONObject("logos")?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap()
            cache[k] = Detail(d.optString("logo").ifBlank { null }, d.optJSONArray("trailers").let { a -> if (a == null) emptyList() else List(a.length()) { a.getString(it) } },
                pr, d.optInt("runtime").takeIf { it > 0 }, d.optString("backdrop").ifBlank { null }, lg) to d.optLong("checkedMs") } }
        var budget = 150
        val out = JSONObject(); var any = false
        val apps = installed()
        val byLang = HashMap<String, List<TmdbTitle>>()
        val displays = ps.map { it.languages.first() }.distinct()
        ps.forEach { p ->
            val arr = JSONArray(); val display = p.languages.first()
            p.languages.forEach { lang ->
                // titles and overviews in the person's main language; TMDB's English where it has no overview in it
                val titles = byLang.getOrPut("$lang|$display") { listOf("movie", "tv").flatMap { kind -> (1..2).flatMap { page ->
                    val en = source("tmdb_${safe(lang)}_${kind}_$page.json", Tmdb.discoverUrl(tmdbKey, kind, lang, page), { Tmdb.parseDiscover(it, kind) }, cached = false)
                    if (display == "en") en else {
                        val local = source("tmdb_${safe(lang)}_${kind}_${page}_${safe(display)}.json", Tmdb.discoverUrl(tmdbKey, kind, lang, page, display), { Tmdb.parseDiscover(it, kind) }, cached = false)
                        // TMDB answers an untranslated title with the original one (another script for a Spanish person): English then
                        if (local.isEmpty()) en.map { it.copy(english = true) }   // the main-language list failed: English titles, English logos
                        else local.map { t -> en.firstOrNull { it.id == t.id }?.let { e ->
                            val untranslated = t.title.isBlank() || (t.title == t.originalTitle && t.lang != display)
                            t.copy(title = if (untranslated) e.title else t.title, overview = t.overview.ifBlank { e.overview }, english = untranslated) } ?: t }
                    } } } }
                titles.forEach { t ->
                    val k = Catalog.key(t); val c = cache[k]
                    if ((c == null || now() - c.second > 7 * 24 * 3600_000L || living.any { it !in c.first.providers }) && budget-- > 0) {
                        fetch(Tmdb.detailAllUrl(tmdbKey, t.kind, t.id, lang, displays))?.let { Tmdb.parseDetail(it, lang, living) }?.let { d ->
                            // availability and trailers are trusted as answered; only cosmetic fields fall back to what was known
                            cache[k] = Detail(d.logo ?: c?.first?.logo, d.trailers, (c?.first?.providers ?: emptyMap()) + d.providers, d.runtime ?: c?.first?.runtime, d.backdrop ?: c?.first?.backdrop,
                                d.logos.ifEmpty { c?.first?.logos ?: emptyMap() }) to now() }
                    }
                }
                val part = Catalog.build(titles, cache.mapValues { it.value.first }, p.living.country, providerApps.forCountry(p.living.country), apps, display)
                for (i in 0 until part.length()) arr.put(part.get(i))
            }
            if (arr.length() > 0) any = true
            out.put(p.id, arr)
        }
        store.save(DETAILS, JSONObject(cache.mapValues { (_, v) -> JSONObject().put("logo", v.first.logo ?: "").put("trailers", JSONArray(v.first.trailers))
            .put("providers", JSONObject(v.first.providers.mapValues { JSONArray(it.value) } as Map<*, *>)).put("runtime", v.first.runtime ?: 0).put("backdrop", v.first.backdrop ?: "")
            .put("logos", JSONObject(v.first.logos as Map<*, *>)).put("checkedMs", v.second) } as Map<*, *>).toString())
        if (any) store.save(FeedStore.CATALOG, out.put("fetchedMs", now()).toString())
        return any
    }

    /**
     * Loves for every profile: one [LoveFeeds.refreshAll] over every profile (languages main first, home country), most used loves first across the household. Caches are keyed by
     * love, language, country and refine choices, so profiles that share them share the requests. True when any love has something.
     */
    fun refreshLoves(): Boolean {
        val lf = loveFeeds ?: return false
        val out = JSONObject(); var any = false
        val ps = people()
        val batches = ps.map { p -> LoveBatch(p.id, p.loves.mapNotNull { LoveFeeds.resolve(it, interests) }, p.languages, countries.firstOrNull { it.code == p.home.country }) }
        val all = runCatching { lf.refreshAll(batches) }.getOrElse { onSourceFailed("loves failed"); emptyMap() }
        all.forEach { (pid, items) ->
            val o = JSONObject(); items.forEach { (id, v) -> o.put(id, v.toJson()); if (v.shows.isNotEmpty() || v.news.isNotEmpty() || v.videos.isNotEmpty() || v.live.isNotEmpty()) any = true }
            out.put(pid, o)
        }
        loves = out
        return any
    }

    fun refreshHomes(): Boolean { parseHomes(cached = false); return homeWeather.values.any { it.isNotEmpty() } || homeNews.values.any { l -> l.any { it.isNotEmpty() } } }

    /** Keyed by profile id, each in the shape Home Mode read from the old homes.json packs. */
    private fun homesJson(): JSONObject = JSONObject().also { out ->
        homes().forEach { h ->
            val names = placeNames.forHome(h.country, h.places) + h.label
            val langs = h.queries.map { q -> languages.firstOrNull { it.code == q.lang } ?: Language(q.lang, q.lang, q.lang, null, null, false) }
            val news = NewsCards.toJson(listOf(NewsCards.home("home_${h.id}", h.label, homeNews[h.id] ?: emptyList(), names, now(), languages = langs, countryScoped = h.country?.news != null))).getJSONObject(0)   // no edition: a global search, never filled
            out.put(h.id, JSONObject().put("pack", h.raw).put("weather", HomeWeather.toJson(homeWeather[h.id] ?: emptyList()))
                .put("weatherMs", fetchedFor("home_${h.id}_weather.json") ?: 0L).put("news", news).put("stations", RadioBrowser.toJson(stations[h.id] ?: emptyList())))
        }
    }

    /** The Top 10 for the living country, reloaded from its own file when the living country changes. A country Netflix does not report has none. */
    private fun tonightNow(): List<Pair<TopTitle, Poster?>> {
        val c = livingCountry()
        if (c?.code != tonightCode) {
            tonightCode = c?.code
            tonight = c?.netflixName?.let { n -> store.load("tonight_${safe(c.code)}.tsv")?.let { tsv -> Tonight.parseTop10(tsv.lineSequence(), n).map { it to posters[it.title]?.value } } } ?: emptyList()
        }
        return tonight
    }

    /** Netflix Top 10 for the living country with posters. The 32 MB TSV is streamed and the read stops once that country's latest week is complete. */
    fun refreshTonight(stream: (String, (String) -> Boolean) -> Boolean): Boolean {
        val c = livingCountry() ?: return false
        val name = c.netflixName ?: return false
        val rows = ArrayList<String>(); var firstWeek: String? = null
        val ok = stream(Tonight.TOP10_URL) { line ->
            if (line.startsWith("$name\t")) {
                val week = line.split('\t').getOrNull(2) ?: ""
                if (firstWeek == null) firstWeek = week
                if (week == firstWeek) rows += line else return@stream false   // a country's block lists the newest week first; stop when the week changes
            } else if (rows.isNotEmpty()) return@stream false                  // past the country's block
            true
        }
        if (!ok || rows.isEmpty()) return false
        val top = Tonight.parseTop10(rows.asSequence(), name)
        if (top.isEmpty() || !Tonight.isFresh(top[0].week, now())) { onSourceFailed("netflix top 10 week ${top.firstOrNull()?.week} is stale, keeping the previous list"); return false }
        tonightCode = c.code
        tonight = top.map { it to posters[it.title]?.value }
        store.save("tonight_${safe(c.code)}.tsv", rows.joinToString("\n"))
        refreshPosters()
        return true
    }

    /** Posters for the current Top 10 that are missing or due for a retry (3 days). Returns true when anything was looked up. */
    fun refreshPosters(): Boolean {
        var any = false
        tonightNow().map { it.first }.forEach { t ->
            val c = posters[t.title]
            if (c != null && (c.value.image != null || !Tonight.shouldRetry(c.checkedMs, now()))) return@forEach
            val fromTv = { fetch(Tonight.tvmazeUrl(t.title))?.let { Tonight.posterFromTvmazeSearch(it, t.title) }?.takeIf { it.image != null } }
            val fromWiki = { Tonight.wikiTitleCandidates(t.title, t.kind).firstNotNullOfOrNull { cand -> fetch(Tonight.wikiUrl(cand))?.let { Tonight.posterFromWiki(it) }?.takeIf { it.image != null } } }
            val p = if (t.kind == "tv") (fromTv() ?: fromWiki()) else fromWiki()   // films never take the series search
            posters[t.title] = Cached(p ?: Poster(null, null), now()); any = true
        }
        if (any) {
            tonight = tonight.map { it.first to posters[it.first.title]?.value }
            store.save("posters.v2.json", JSONObject(posters.mapValues { JSONObject().put("image", it.value.value.image ?: JSONObject.NULL).put("blurb", it.value.value.blurb ?: JSONObject.NULL).put("checkedMs", it.value.checkedMs) } as Map<*, *>).toString())
        }
        return any
    }

    /**
     * Live channels for each home country: a curated file's YouTube channels resolved to their current live video (a failed or consent-walled
     * fetch keeps the previous state); a country without a file gets one "<country> live news" search a day.
     */
    fun refreshChannels(): Boolean {
        var any = false
        val homeCountries = people().map { it.home.country }.distinct()
        homeCountries.filter { it !in channels }.forEach { if (searchChannels(it)) any = true }
        homeCountries.flatMap { channels[it] ?: emptyList() }.filter { it.pkg == null && it.handle != null }.forEach { c ->
            val url = Channels.liveUrl(c.handle!!)
            val html = if (stream != null) Channels.readLive(url, stream) else fetch(url)
            val (live, id, title) = html?.let { Channels.resolve(it) } ?: Triple(false, null, null)
            if (id != null) { channelStates[c.id] = ChannelState(c.id, live, id, title, Channels.thumb(id), now()); any = true; failing.remove("channel:${c.id}") }
            else if (failing.add("channel:${c.id}")) onSourceFailed("channel ${c.id} did not resolve, keeping previous state")
        }
        return any
    }

    private fun searchFile(cc: String) = "channels_search_${safe(cc)}.json"
    private fun searchedFor(cc: String): List<Pair<Channel, ChannelState>> = searched.getOrPut(cc) {
        val tag = (countryOf(cc)?.name ?: cc) + " · News"
        runCatching { val o = JSONObject(store.load(searchFile(cc)) ?: ""); val a = o.getJSONArray("items"); val at = o.optLong("fetchedMs")
            List(a.length()) { val j = a.getJSONObject(it); val c = Channel(j.getString("id"), j.getString("name"), tag, "#223", null, null)
                c to ChannelState(c.id, true, j.getString("videoId"), j.optString("title").ifBlank { null }, Channels.thumb(j.getString("videoId")), at) } }.getOrDefault(emptyList())
    }
    /** One cached YouTube live search for a home country with no channel file. Only parsed items are stored; the key stays in the request. */
    private fun searchChannels(cc: String): Boolean {
        val c = countryOf(cc) ?: return false
        val cachedAt = runCatching { JSONObject(store.load(searchFile(cc)) ?: "").optLong("fetchedMs") }.getOrNull()
        if (cachedAt != null && now() - cachedAt in 0 until DAY) return false
        val key = "channels|$cc"   // a failed search rests an hour instead of spending one every refresh
        if (youtubeKey.isBlank() || quota == null || quota.resting(key) || !quota.take(YouTubeQuota.LOVES_LIMIT)) return false
        val body = fetch(Journeys.searchUrl(youtubeKey, "${c.name} live news", live = true) + "&regionCode=${safe(c.code)}") ?: return false.also { quota.failed(key) }
        quota.succeeded(key)
        val found = Channels.fromLiveSearch(body, "${c.name} · News", now()).take(8)
        store.save(searchFile(cc), JSONObject().put("fetchedMs", now()).put("items", JSONArray().also { a -> found.forEach { (ch, st) ->
            a.put(JSONObject().put("id", ch.id).put("name", ch.name).put("videoId", st.videoId).put("title", st.title ?: "")) } }).toString())
        searched[cc] = found
        return found.isNotEmpty()
    }
    /** The channels row: every home country's curated list, or its searched channels, each marked with its country; never another country's list. */
    private fun pageChannels(): JSONArray {
        val list = ArrayList<Channel>(); val states = HashMap<String, ChannelState>(channelStates); val country = LinkedHashMap<String, MutableList<String>>()
        people().map { it.home.country }.distinct().forEach { cc ->
            val mine = channels[cc] ?: searchedFor(cc).map { (c, st) -> states[c.id] = st; c }
            mine.forEach { c -> list += c; country.getOrPut(c.id) { ArrayList() }.let { if (cc !in it) it += cc } }
        }
        return Channels.toJson(list.distinctBy { it.id }, states, country)
    }
    /** Each person's loved sports (the live cards' `sport`), in profile order: the pages and the screensaver show the current person's only. */
    private fun sportsBy(): JSONArray = JSONArray().also { a -> people().forEach { p ->
        a.put(JSONObject().put("id", p.id).put("sports", JSONArray(SportPrefs.of(listOf(p), interests).follows.map { it.sport }))) } }

    /** Which country the TV is seen from right now (ipwho.is, free, no key); hand-offs follow it. */
    fun refreshCountry(): Boolean {
        val body = fetch("https://ipwho.is/") ?: return false
        val code = runCatching { JSONObject(body).optString("country_code") }.getOrDefault("")
        if (code.isNotBlank()) country = code
        return code.isNotBlank()
    }

    fun refreshLive(): Boolean { parseLive(cached = false); return cricketLive.isNotEmpty() || f1Sessions.isNotEmpty() || tennis.isNotEmpty() || f1Next != null || espnEvents.values.any { it.isNotEmpty() } }

    /** Venue photographs for the current cards, looked up once per venue and cached. */
    fun refreshPlaces(): Boolean {
        var any = false
        liveCards().mapNotNull { it.venue }.distinct().filter { v -> places[v].let { it == null || (it.value.isBlank() && Tonight.shouldRetry(it.checkedMs, now())) } }.forEach { v ->
            val img = fetch(Places.url(v))?.let { Places.fromWiki(it) }
            places[v] = Cached(img ?: "", now()); any = true
        }
        if (any) store.save("places.v2.json", JSONObject(places.mapValues { JSONObject().put("image", it.value.value).put("checkedMs", it.value.checkedMs) } as Map<*, *>).toString())
        return any
    }

    private fun liveCards(): List<LiveCard> {
        val p = SportPrefs.of(people(), interests)
        val at = (mapOf("cricket" to fetchedFor("cricinfo.xml"), "f1" to fetchedFor("espn_f1.json", "jolpica_next.json"), "motogp" to fetchedFor("therace_motogp.xml"), "tennis" to fetchedFor("espn_atp.json", "espn_wta.json")) +
            p.follows.filter { it.sport !in SportPrefs.FIXED }.associate { f -> f.sport to fetchedFor(*f.leagues.map(::espnFile).toTypedArray()) })
            .filterValues { it != null }.mapValues { it.value!! }
        return LiveCards.build(LiveInputs(cricketLive, cricketEvents, f1Next, f1Sessions, f1Standings, motogpNews, tennis, at, handoffCountry(), cricketSeries, espnEvents), config, p, handoffs, now())
            .map { c -> c.copy(image = c.venue?.let { places[it]?.value }?.ifBlank { null }) }
    }

    /**
     * The Today rail: each love's news for the people in the house (the same love once, first person first), in the shape the page read from
     * the old topics. Loves with no news today and people who left are not shown.
     */
    private fun todayJson(): JSONArray {
        val topics = ArrayList<Topic>(); val items = HashMap<String, List<RssItem>>()
        people().forEach { p -> p.loves.forEach { love ->
            val id = "love_${love.id}"
            if (items.containsKey(id)) return@forEach
            val arr = loves.optJSONObject(p.id)?.optJSONObject(love.id)?.optJSONArray("news") ?: return@forEach
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }.map { RssItem(it.optString("title"), it.optString("link"), it.optLong("publishedMs"), it.optString("source").ifBlank { null }, null) }
            if (list.isEmpty()) return@forEach
            topics += Topic(id, LoveFeeds.resolve(love, interests)?.interest?.name ?: love.id, emptyList(), emptyList()); items[id] = list
        } }
        return NewsCards.toJson(NewsCards.build(topics, items, now()).filter { it.headlines.isNotEmpty() })
    }

    /** fetchedMs stays the last key: the activity's push memo ignores it there (PushMemo.freshFeeds). */
    fun writePage(): String {
        val page = JSONObject().put("live", LiveCards.toJson(liveCards())).put("today", todayJson())
            .put("channels", pageChannels()).put("sportsBy", sportsBy()).put("tonight", Tonight.toJson(tonightNow())).put("country", country).put("homes", homesJson()).put("loves", loves).put("fetchedMs", now()).toString()
        store.save(FeedStore.PAGE, page)
        return page
    }

    private companion object {
        const val DAY = 24 * 3600_000L
        const val DETAILS = "catalog_details.v3.json"   // v3: providers per country
    }
}
