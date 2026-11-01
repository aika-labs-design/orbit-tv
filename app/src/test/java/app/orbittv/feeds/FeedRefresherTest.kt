package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FeedRefresherTest {
    private val config = FeedConfig.parse(File("src/main/assets/feeds.json").readText())
    private val countries = World.countries(File("src/main/assets/countries.json").readText())
    private val languages = World.languages(File("src/main/assets/languages.json").readText())
    private val interests = Interests.parse(File("src/main/assets/interests.json").readText())
    private val handoffs = Handoffs.parse(File("src/main/assets/handoffs.json").readText())
    private val providerApps = ProviderApps.parse(File("src/main/assets/providers.json").readText())
    private val inChannels = Channels.parse(File("src/main/assets/channels/IN.json").readText())
    private val festivals = mapOf("IN" to FestivalBook.parse(File("src/main/assets/festivals/IN.json").readText()))
    private val now = 1_793_500_000_000L   // 2026-11-01
    private fun store() = FeedStore(Files.createTempDirectory("feeds").toFile())

    private val cork = Living("Cork", 51.8985, -8.4756, "Europe/Dublin", "IE")
    private val berlin = Living("Berlin", 52.52, 13.41, "Europe/Berlin", "DE")
    private val sport = listOf(Love("cricket", listOf("india-cricket")), Love("formula-1", emptyList()), Love("motogp", emptyList()), Love("tennis", listOf("indian-tennis")))
    private val ann = Profile("p-ann", "Ann", Home("IN", listOf(Place("Mysuru", 12.2958, 76.6394, "Asia/Kolkata"), Place("Mangaluru", 12.9141, 74.856, "Asia/Kolkata"))), listOf("kn", "en"), cork, sport, 0L)
    private val bo = Profile("p-bo", "Bo", Home("IN", listOf(Place("Vijayawada", 16.5062, 80.648, "Asia/Kolkata"))), listOf("te"), cork, emptyList(), 0L)
    private val ada = Profile("p-ada", "Ada", Home("NG", listOf(Place("Lagos", 6.45, 3.39, "Africa/Lagos"))), listOf("yo", "en"), berlin, emptyList(), 0L)

    private fun refresher(
        s: FeedStore, fetch: (String) -> String?, people: List<Profile> = listOf(ann, bo), tmdbKey: String = "", installed: Set<String> = emptySet(),
        youtubeKey: String = "", quota: YouTubeQuota? = null, clock: () -> Long = { now },
    ) = FeedRefresher(config, s, fetch, channels = mapOf("IN" to inChannels), festivals = festivals, tmdbKey = tmdbKey, installed = { installed }, profiles = { people },
        interests = interests, countries = countries, languages = languages, handoffs = handoffs, providerApps = providerApps, youtubeKey = youtubeKey, quota = quota, now = clock)

    private val good: Map<String, String> = mapOf(
        FeedUrls.CRICINFO to fixture("cricinfo_livescores.xml"), FeedUrls.espnCricket("8048") to fixture("espn_cricket_8048.json"), FeedUrls.espnCricket("8039") to fixture("espn_cricket_8039.json"),
        FeedUrls.ESPN_F1 to fixture("espn_f1.json"), FeedUrls.JOLPICA_NEXT to fixture("jolpica_next.json"), FeedUrls.JOLPICA_STANDINGS to fixture("jolpica_standings.json"),
        FeedUrls.THERACE_MOTOGP to fixture("therace_motogp.xml"), FeedUrls.ESPN_ATP to fixture("espn_atp.json"),
    )
    private fun objs(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }

    @Test fun `a failed fetch keeps the previous raw body and the page still builds`() {
        val s = store(); var table = good
        val r = refresher(s, { table[it] })
        assertTrue(r.refreshLive())
        assertEquals(fixture("cricinfo_livescores.xml"), s.load("cricinfo.xml"))
        table = mapOf(FeedUrls.CRICINFO to "<!DOCTYPE html><html>Page Not Found</html>")
        r.refreshLive()
        assertEquals(fixture("cricinfo_livescores.xml"), s.load("cricinfo.xml"))
        val page = JSONObject(r.writePage())
        assertTrue(page.getJSONArray("live").length() >= 4)
        assertEquals("f1", objs(page.getJSONArray("live")).first { it.getString("sport") == "f1" }.getString("sport"))
        assertEquals(page.toString(), s.load("feeds.json"))
    }
    @Test fun `a cached body keeps its original fetch time on the card`() {
        val s = store(); var table = good; var clock = now
        val india = "<rss><channel><item><title>India 187/3 * v England 180/7</title><link>l</link></item></channel></rss>"
        table = good + (FeedUrls.CRICINFO to india)
        val r = refresher(s, { table[it] }, clock = { clock })
        r.refreshLive()
        clock = now + 15 * 60_000; table = emptyMap()
        r.refreshLive()
        val cricket = objs(JSONObject(r.writePage()).getJSONArray("live")).first { it.getString("sport") == "cricket" }
        assertEquals(now, cricket.getLong("fetchedMs"))
    }
    @Test fun `only loved sports are fetched`() {
        val urls = ArrayList<String>()
        val r = refresher(store(), { urls += it; good[it] }, people = listOf(bo.copy(loves = listOf(Love("formula-1", emptyList())))))
        r.refreshLive()
        assertTrue(urls.contains(FeedUrls.ESPN_F1)); assertFalse(urls.contains(FeedUrls.CRICINFO)); assertFalse(urls.contains(FeedUrls.ESPN_ATP)); assertFalse(urls.contains(FeedUrls.THERACE_MOTOGP))
        assertEquals(listOf("f1"), objs(JSONObject(r.writePage()).getJSONArray("live")).map { it.getString("sport") })
    }
    @Test fun `hand-offs follow the living country when the tv's own country is unknown`() {
        val india = "<rss><channel><item><title>India 187/3 * v England 180/7</title><link>l</link></item></channel></rss>"
        val inBerlin = ann.copy(living = berlin)
        val r = refresher(store(), { if (it == FeedUrls.CRICINFO) india else null }, people = listOf(inBerlin))
        r.refreshLive()
        val cards = objs(JSONObject(r.writePage()).getJSONArray("live"))
        assertTrue(cards.isNotEmpty()); assertTrue(cards.all { it.isNull("handoffPkg") && it.getString("handoffLabel") == "" })
        val home = refresher(store(), { if (it == FeedUrls.CRICINFO) india else null }, people = listOf(ann)); home.refreshLive()
        assertEquals("Check SonyLIV or discovery+", objs(JSONObject(home.writePage()).getJSONArray("live")).first { it.getString("sport") == "cricket" }.getString("handoffLabel"))
    }
    @Test fun `posters for a cached top 10 are resolved without re-downloading the list`() {
        val s = store(); s.save("tonight_IE.tsv", fixture("netflix_top10_ie.tsv").lineSequence().filter { it.startsWith("Ireland\tIE\t2021-07-11") }.joinToString("\n"))
        var tvmazeCalls = 0
        val fetch: (String) -> String? = { url -> when { url.startsWith("https://api.tvmaze.com/search/shows") -> { tvmazeCalls++; "[]" }; url.startsWith("https://en.wikipedia.org/") -> null; else -> good[url] } }
        val r = refresher(s, fetch)
        val page = JSONObject(r.writePage()); assertEquals(20, page.getJSONArray("tonight").length())
        assertTrue(r.refreshPosters()); assertTrue(tvmazeCalls >= 10)
        assertTrue(!r.refreshPosters())   // everything checked, nothing due for a retry
    }
    @Test fun `page json is under 50 KB with full fixtures`() {
        val s = store(); val r = refresher(s, { good[it] })
        r.refreshLive()
        val page = r.writePage()
        assertTrue("size ${page.length}", page.length < 50_000)
    }
    @Test fun `today is the loves' news of the people in the house, and nothing fetches a fixed news edition`() {
        val s = store(); val t = now - 3600_000L
        fun news(vararg titles: String) = JSONObject().put("news", JSONArray().also { a -> titles.forEach { a.put(JSONObject().put("title", it).put("link", "https://x/$it").put("source", "X").put("publishedMs", t)) } })
        s.save(FeedStore.PAGE, JSONObject().put("loves", JSONObject()
            .put("p-ann", JSONObject().put("cricket", news("India win in Perth", "Mumbai Indians retain their captain")).put("formula-1", news()))
            .put("p-gone", JSONObject().put("cooking", news("Jollof wars")))).toString())
        val urls = ArrayList<String>()
        val r = refresher(s, { urls += it; good[it] }, people = listOf(ann))
        r.refreshLive()
        val today = objs(JSONObject(r.writePage()).getJSONArray("today"))
        assertEquals(listOf("love_cricket"), today.map { it.getString("id") })   // empty loves and people who left are not shown
        assertEquals("Cricket", today[0].getString("label"))
        assertEquals(listOf("India win in Perth", "Mumbai Indians retain their captain"), objs(today[0].getJSONArray("headlines")).map { it.getString("title") })
        assertTrue(urls.none { it.contains("news.google.com") })
    }
    @Test fun `anyLive reflects a followed live match`() {
        val others = "<rss><channel><item><title>South Africa 12/0 * v Australia</title><link>l</link></item></channel></rss>"
        val quiet = refresher(store(), { if (it == FeedUrls.CRICINFO) others else null })
        quiet.refreshLive(); assertFalse(quiet.anyLive)
        val withF1 = refresher(store(), { good[it] })
        withF1.refreshLive(); assertTrue(withF1.anyLive) // the recorded F1 scoreboard has FP1 in progress
        val india = "<rss><channel><item><title>India 187/3 * v England 180/7</title><link>l</link></item></channel></rss>"
        val r2 = refresher(store(), { if (it == FeedUrls.CRICINFO) india else good[it] })
        r2.refreshLive(); assertTrue(r2.anyLive)
    }

    // review: with no country edition the news search is global, so an unnamed headline may be another country's: no fill there
    @Test fun `a home country with no news edition is never filled with headlines that do not name it`() {
        val al = Profile("p-al", "Ana", Home("AL", listOf(Place("Tirana", 41.33, 19.82, "Europe/Tirane"))), listOf("en"), cork, emptyList(), now)
        val t = now - 3600_000L
        fun rss(vararg titles: String) = "<rss><channel>" + titles.joinToString("") { "<item><title>$it</title><link>https://x/${it.hashCode()}</link><pubDate>" +
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochMilli(t).atZone(java.time.ZoneOffset.UTC)) + "</pubDate></item>" } + "</channel></rss>"
        val body = rss("The mayor of the city opens a new bridge", "Rain is expected in the region on Monday")
        val r = refresher(store(), { u -> if (u.startsWith("https://news.google.com/")) body else null }, people = listOf(al))
        r.refreshHomes()
        assertEquals(0, JSONObject(r.writePage()).getJSONObject("homes").getJSONObject("p-al").getJSONObject("news").getJSONArray("headlines").length())
        // with an edition (Ireland) the same unnamed headlines fill up to three
        val ie = al.copy(id = "p-ie", home = Home("IE", listOf(Place("Cork", 51.9, -8.47, "Europe/Dublin"))))
        val r2 = refresher(store(), { u -> if (u.startsWith("https://news.google.com/")) body else null }, people = listOf(ie))
        r2.refreshHomes()
        assertEquals(2, JSONObject(r2.writePage()).getJSONObject("homes").getJSONObject("p-ie").getJSONObject("news").getJSONArray("headlines").length())
    }

    // ---------- homes, now one per profile ----------
    private val homeFetch: Map<String, String> = mapOf(
        HomeWeather.url(ann.home.places.map { HomePlace(it.name, it.lat, it.lon) }) to fixture("meteo_multi.json"),
        HomeWeather.url(bo.home.places.map { HomePlace(it.name, it.lat, it.lon) }) to fixture("meteo_single.json"),
        World.newsUrl("Vijayawada", countries.first { it.code == "IN" }, "te") to fixture("gnews_telugu.xml"),
    )
    private val homeNow = Rss.parse(fixture("gnews_telugu.xml")).maxOf { it.publishedMs } + 60_000

    @Test fun `an india kannada profile gets the bundled festival days when nager has none, a country with neither gets none`() {
        val fetch: (String) -> String? = { u -> when { u.startsWith("https://date.nager.at/") && u.endsWith("/IN") -> ""; u.startsWith("https://date.nager.at/") -> "[]"; else -> null } }
        val iceland = bo.copy(id = "p-is", home = Home("IS", listOf(Place("Reykjavik", 64.14, -21.94, "Atlantic/Reykjavik"))), languages = listOf("is"))
        val r = refresher(store(), fetch, people = listOf(ann, iceland))
        r.refreshHomes()
        val homes = JSONObject(r.writePage()).getJSONObject("homes")
        val fest = objs(homes.getJSONObject("p-ann").getJSONObject("pack").getJSONArray("festivals"))
        assertTrue(fest.any { it.getString("date") == "2026-11-01" && it.getString("name") == "Kannada Rajyotsava" })
        assertTrue(fest.any { it.getString("date") == "2026-11-08" && it.getString("local") == "ದೀಪಾವಳಿ" })
        assertTrue(fest.none { it.getString("name") == "Vinayaka Chavithi" })
        assertEquals(0, homes.getJSONObject("p-is").getJSONObject("pack").getJSONArray("festivals").length())
    }
    @Test fun `homes reach the page keyed by profile with pack, weather and news`() {
        val r = refresher(store(), { homeFetch[it] }, clock = { homeNow })
        assertTrue(r.refreshHomes())
        val h = JSONObject(r.writePage()).getJSONObject("homes")
        assertEquals(setOf("p-ann", "p-bo"), h.keys().asSequence().toSet())
        assertEquals("India", h.getJSONObject("p-ann").getJSONObject("pack").getString("label"))
        assertEquals("Kannada", h.getJSONObject("p-ann").getJSONObject("pack").getString("channelTag"))
        assertEquals(listOf("Mysuru", "Mangaluru"), objs(h.getJSONObject("p-ann").getJSONArray("weather")).map { it.getString("name") })
        val bo = h.getJSONObject("p-bo")
        assertEquals("Vijayawada", bo.getJSONArray("weather").getJSONObject(0).getString("name"))
        assertEquals("home_p-bo", bo.getJSONObject("news").getString("id"))
        assertTrue(bo.getJSONObject("news").getJSONArray("headlines").length() > 0)
        assertEquals(0, bo.getJSONArray("stations").length())
        // home news never joins the hero's Today row
        val today = JSONObject(r.writePage()).getJSONArray("today")
        assertTrue((0 until today.length()).none { today.getJSONObject(it).getString("id").startsWith("home_") })
    }
    @Test fun `homes survive a failed fetch and a restart`() {
        val s = store(); var table = homeFetch
        val r = refresher(s, { table[it] }, clock = { homeNow })
        r.refreshHomes(); table = emptyMap(); r.refreshHomes()
        assertEquals("Vijayawada", JSONObject(r.writePage()).getJSONObject("homes").getJSONObject("p-bo").getJSONArray("weather").getJSONObject(0).getString("name"))
        val again = refresher(s, { null }, clock = { homeNow })
        assertEquals(2, JSONObject(again.writePage()).getJSONObject("homes").getJSONObject("p-ann").getJSONArray("weather").length())
    }

    @Test fun `a nigeria and berlin profile gets lagos weather, nigerian holidays, yoruba radio and the german top 10`() {
        val urls = ArrayList<String>()
        val lagos = listOf(HomePlace("Lagos", 6.45, 3.39))
        val fetch: (String) -> String? = { u -> urls += u; when (u) {
            HomeWeather.url(lagos) -> fixture("meteo_single.json")
            Nager.url(2026, "NG") -> fixture("nager_ng_2026.json"); Nager.url(2027, "NG") -> "[]"
            RadioBrowser.url("yoruba") -> fixture("radio_yoruba.json")
            else -> null } }
        val r = refresher(store(), fetch, people = listOf(ada))
        r.refreshHomes(); r.refreshStations()
        val h = JSONObject(r.writePage()).getJSONObject("homes").getJSONObject("p-ada")
        assertEquals("Lagos", h.getJSONArray("weather").getJSONObject(0).getString("name"))
        val pack = h.getJSONObject("pack")
        assertEquals("Nigeria", pack.getString("label")); assertEquals("Yoruba", pack.getString("langName"))
        assertTrue(objs(pack.getJSONArray("festivals")).any { it.getString("date") == "2026-06-12" && it.getString("name") == "Democracy Day" })
        assertEquals("Trove Radio Station", h.getJSONArray("stations").getJSONObject(0).getString("name"))
        assertTrue(urls.any { it.startsWith("https://news.google.com/") && it.contains("hl=yo&gl=NG&ceid=NG:yo") })
        assertTrue(urls.none { it.contains("bylanguage/kannada") || it.contains("bylanguage/telugu") })
        // holidays are fetched once a year, not on every refresh
        val before = urls.count { it.startsWith("https://date.nager.at/") }; r.refreshHomes()
        assertEquals(before, urls.count { it.startsWith("https://date.nager.at/") })

        val tsv = listOf("country_name\tcountry_iso2\tweek\tcategory\tweekly_rank\tshow_title\tseason_title\tcumulative_weeks_in_top_10",
            "Ireland\tIE\t2026-10-25\tFilms\t1\tAn Irish Film\tN/A\t1",
            "Germany\tDE\t2026-10-25\tFilms\t1\tDer Film\tN/A\t1", "Germany\tDE\t2026-10-25\tTV (English)\t1\tEine Serie\tSeason 1\t1",
            "Germany\tDE\t2026-10-18\tFilms\t1\tAlter Film\tN/A\t2", "Ghana\tGH\t2026-10-25\tFilms\t1\tX\tN/A\t1")
        assertTrue(r.refreshTonight { _, onLine -> for (l in tsv) if (!onLine(l)) break; true })
        assertEquals(listOf("Der Film", "Eine Serie"), objs(JSONObject(r.writePage()).getJSONArray("tonight")).map { it.getString("title") })
    }

    @Test fun `with no profiles nothing breaks and nothing is fetched for a home or a country`() {
        val urls = ArrayList<String>()
        val r = refresher(store(), { urls += it; good[it] }, people = emptyList(), tmdbKey = "k", youtubeKey = "y", quota = YouTubeQuota(store()) { now })
        r.refreshLive(); r.refreshHomes(); r.refreshStations(); r.refreshChannels(); r.refreshLoves()
        assertFalse(r.refreshCatalog())
        var streamed = false
        assertFalse(r.refreshTonight { _, _ -> streamed = true; true }); assertFalse(streamed)
        val page = JSONObject(r.writePage())
        assertEquals(0, page.getJSONObject("homes").length()); assertEquals(0, page.getJSONObject("loves").length())
        assertEquals(0, page.getJSONArray("tonight").length()); assertEquals(0, page.getJSONArray("channels").length()); assertEquals(0, page.getJSONArray("live").length())
        assertTrue(urls.none { it.contains("themoviedb") || it.contains("youtube") || it.contains("nager") || it.contains("radio-browser") })
    }
    @Test fun `a living country netflix does not report has no top 10, never another country's`() {
        val nowhere = countries.first { it.netflixName == null }
        val r = refresher(store(), { null }, people = listOf(bo.copy(living = Living("X", 1.0, 2.0, "UTC", nowhere.code))))
        var streamed = false
        assertFalse(r.refreshTonight { _, _ -> streamed = true; true }); assertFalse(streamed)
        assertEquals(0, JSONObject(r.writePage()).getJSONArray("tonight").length())
    }

    // ---------- live channels per home country ----------
    @Test fun `a home country with no channel file gets one cached live search, never another country's list`() {
        val dir = Files.createTempDirectory("feeds").toFile(); val s = FeedStore(dir); val urls = ArrayList<String>()
        val fetch: (String) -> String? = { u -> urls += u; if (u.contains("youtube/v3/search")) fixture("yt_search_live.json") else null }
        val r = refresher(s, fetch, people = listOf(ada), youtubeKey = "YTSECRET42", quota = YouTubeQuota(s) { now })
        assertTrue(r.refreshChannels())
        val searches = urls.filter { it.contains("youtube/v3/search") }
        assertEquals(1, searches.size); assertTrue(searches[0], searches[0].contains("q=Nigeria+live+news") && searches[0].contains("eventType=live"))
        val ch = objs(JSONObject(r.writePage()).getJSONArray("channels"))
        assertEquals(4, ch.size)   // one per channel
        assertTrue(ch.all { it.getString("tag") == "Nigeria · News" && it.getBoolean("live") && it.getString("url").startsWith("https://www.youtube.com/watch?v=") })
        assertTrue(ch.none { c -> inChannels.any { it.id == c.getString("id") } })
        r.refreshChannels(); assertEquals(1, urls.count { it.contains("youtube/v3/search") })   // once a day
        val again = refresher(s, fetch, people = listOf(ada), youtubeKey = "YTSECRET42", quota = YouTubeQuota(s) { now })
        assertEquals(4, JSONObject(again.writePage()).getJSONArray("channels").length()); again.refreshChannels()
        assertEquals(1, urls.count { it.contains("youtube/v3/search") })
        assertFalse(s.load(FeedStore.PAGE)!!.contains("YTSECRET42"))
        assertTrue(dir.listFiles()!!.none { it.readText().contains("YTSECRET42") })
    }
    @Test fun `a failing channel search rests an hour instead of searching every refresh`() {
        val s = store(); val urls = ArrayList<String>(); var t = now
        val r = refresher(s, { u -> urls += u; null }, people = listOf(ada), youtubeKey = "y", quota = YouTubeQuota(s) { t }, clock = { t })
        repeat(3) { r.refreshChannels(); t += 15 * 60_000L }
        assertEquals(1, urls.count { it.contains("youtube/v3/search") })
        t = now + 61 * 60_000L; r.refreshChannels()
        assertEquals(2, urls.count { it.contains("youtube/v3/search") })
    }
    @Test fun `a home country with a channel file uses it and searches nothing`() {
        val urls = ArrayList<String>()
        val r = refresher(store(), { urls += it; null }, people = listOf(ann), youtubeKey = "y", quota = YouTubeQuota(store()) { now })
        r.refreshChannels()
        assertTrue(urls.none { it.contains("youtube/v3/search") })
        assertEquals(inChannels.map { it.id }, objs(JSONObject(r.writePage()).getJSONArray("channels")).map { it.getString("id") })
    }
    @Test fun `each channel names its home country and each person names their loved sports, so a page shows only the current person's`() {
        val s = store()
        s.save("channels_search_NG.json", JSONObject().put("fetchedMs", now).put("items", JSONArray().put(JSONObject().put("id", "yt:ng1").put("name", "NG One").put("videoId", "abcdefghijk").put("title", "Live"))).toString())
        val page = JSONObject(refresher(s, { null }, people = listOf(ann, ada)).writePage())
        val ch = objs(page.getJSONArray("channels"))
        assertEquals(inChannels.map { listOf("IN") } + listOf(listOf("NG")), ch.map { c -> c.getJSONArray("countries").let { a -> List(a.length()) { a.getString(it) } } })
        val by = objs(page.getJSONArray("sportsBy")).associate { it.getString("id") to it.getJSONArray("sports").let { a -> List(a.length()) { i -> a.getString(i) }.toSet() } }
        assertEquals(listOf("p-ann", "p-ada"), objs(page.getJSONArray("sportsBy")).map { it.getString("id") })
        assertEquals(setOf("cricket", "f1", "motogp", "tennis"), by["p-ann"]); assertEquals(emptySet<String>(), by["p-ada"])
    }
    @Test fun `without a key or allowance the live search is skipped quietly`() {
        val urls = ArrayList<String>()
        refresher(store(), { urls += it; null }, people = listOf(ada)).refreshChannels()
        val s = store(); val q = YouTubeQuota(s) { now }; repeat(YouTubeQuota.LOVES_LIMIT) { q.take(YouTubeQuota.LOVES_LIMIT) }
        refresher(s, { urls += it; null }, people = listOf(ada), youtubeKey = "y", quota = q).refreshChannels()
        assertTrue(urls.none { it.contains("youtube") })
    }

    // ---------- catalogue, per profile and living country ----------
    private fun catalogFetch(): (String) -> String? = { url -> when {
        url.contains("/discover/movie") && url.contains("language=te") && url.contains("page=1") -> fixture("tmdb_discover_kn.json").replace("\"kn\"", "\"te\"")
        url.contains("/movie/") && url.contains("append_to_response") -> fixture("tmdb_detail_579974.json")
        else -> null } }
    @Test fun `catalogue reaches its own file per profile and survives a failed refresh`() {
        val s = store(); var f = catalogFetch()
        val r = refresher(s, { f(it) }, tmdbKey = "k", installed = setOf("com.netflix.ninja"))
        assertTrue(r.refreshCatalog())
        val bo = JSONObject(s.load("catalog.json")!!).getJSONArray("p-bo")
        assertEquals(20, bo.length())
        assertTrue((0 until bo.length()).all { bo.getJSONObject(it).getJSONArray("apps").length() == 1 })
        f = { null }; r.refreshCatalog()
        assertEquals(20, JSONObject(s.load("catalog.json")!!).getJSONArray("p-bo").length())
        assertFalse(s.load("catalog.json")!!.contains("api_key"))
    }
    @Test fun `catalogue providers are those of the profile's living country`() {
        val s = store()
        val inLagos = bo.copy(living = Living("Lagos", 6.45, 3.39, "Africa/Lagos", "NG"))
        val r = refresher(s, catalogFetch(), people = listOf(inLagos), tmdbKey = "k", installed = setOf("com.netflix.ninja"))
        assertTrue(r.refreshCatalog())
        val first = JSONObject(s.load("catalog.json")!!).getJSONArray("p-bo").getJSONObject(0)
        assertEquals(listOf("Netflix", "Zee5"), first.getJSONArray("providers").let { a -> List(a.length()) { a.getString(it) } })
        assertEquals(listOf("Netflix"), objs(first.getJSONArray("apps")).map { it.getString("name") })   // Zee5 has no known app: name only
    }
    @Test fun `a spanish-first person gets spanish titles and overviews, english where tmdb has no spanish overview`() {
        val ana = Profile("p-ana", "Ana", Home("MX", listOf(Place("Mexico City", 19.43, -99.13, "America/Mexico_City"))), listOf("es", "en"), Living("Toronto", 43.65, -79.38, "America/Toronto", "CA"), emptyList(), 0L)
        fun page(title: String, overview: String) = """{"results":[{"id":7,"title":"$title","original_title":"La casa","original_language":"es","overview":"$overview","release_date":"2024-01-01","genre_ids":[18],"popularity":9,"vote_average":7}]}"""
        val fetch: (String) -> String? = { u -> when {
            u.contains("/discover/movie") && u.contains("with_original_language=es") && u.contains("page=1") -> if (u.contains("&language=es")) page("La casa", "") else page("The House", "A family moves in.")
            else -> null } }
        val s = store()
        assertTrue(refresher(s, fetch, people = listOf(ana), tmdbKey = "k").refreshCatalog())
        val t = JSONObject(s.load("catalog.json")!!).getJSONArray("p-ana").getJSONObject(0)
        assertEquals("La casa", t.getString("title")); assertEquals("A family moves in.", t.getString("overview"))
    }
    // checked against TMDB: with no translation in the asked language it answers the original title (another script for a Spanish person)
    @Test fun `a title tmdb has not translated into the main language comes in english, not in another script`() {
        val ana = Profile("p-ana", "Ana", Home("MX", listOf(Place("Mexico City", 19.43, -99.13, "America/Mexico_City"))), listOf("es", "ko"), Living("Toronto", 43.65, -79.38, "America/Toronto", "CA"), emptyList(), 0L)
        fun page(title: String) = """{"results":[{"id":9,"title":"$title","original_title":"기생충","original_language":"ko","overview":"","release_date":"2019-01-01","genre_ids":[18],"popularity":9,"vote_average":8}]}"""
        val fetch: (String) -> String? = { u -> if (u.contains("/discover/movie") && u.contains("with_original_language=ko") && u.contains("page=1")) (if (u.contains("&language=es")) page("기생충") else page("Parasite")) else null }
        val s = store()
        assertTrue(refresher(s, fetch, people = listOf(ana), tmdbKey = "k").refreshCatalog())
        assertEquals("Parasite", JSONObject(s.load("catalog.json")!!).getJSONArray("p-ana").getJSONObject(0).getString("title"))
    }
    // review: a channel listed for two home countries was tagged with the first only
    @Test fun `a channel listed for two home countries belongs to both`() {
        val np = Profile("p-np", "Nima", Home("NP", listOf(Place("Kathmandu", 27.7, 85.3, "Asia/Kathmandu"))), listOf("ne"), cork, emptyList(), 0L)
        val r = FeedRefresher(config, store(), { null }, channels = mapOf("IN" to inChannels, "NP" to inChannels.take(1)), profiles = { listOf(ann, np) },
            interests = interests, countries = countries, languages = languages, now = { now })
        val first = objs(JSONObject(r.writePage()).getJSONArray("channels")).first { it.getString("id") == inChannels[0].id }
        assertEquals(listOf("IN", "NP"), first.getJSONArray("countries").let { a -> List(a.length()) { a.getString(it) } })
    }
    @Test fun `home radio is in the main language with the home country's stations first`() {
        val ana = Profile("p-ana", "Ana", Home("MX", listOf(Place("Mexico City", 19.43, -99.13, "America/Mexico_City"))), listOf("es"), Living("Toronto", 43.65, -79.38, "America/Toronto", "CA"), emptyList(), 0L)
        val body = """[{"name":"Los 40","url_resolved":"https://x/a.mp3","codec":"MP3","bitrate":128,"lastcheckok":1,"countrycode":"ES"},
            {"name":"CDMX Noticias","url_resolved":"https://x/b.mp3","codec":"MP3","bitrate":128,"lastcheckok":1,"countrycode":"MX"}]"""
        val r = refresher(store(), { u -> if (u == RadioBrowser.url("spanish")) body else null }, people = listOf(ana))
        assertTrue(r.refreshStations())
        val st = JSONObject(r.writePage()).getJSONObject("homes").getJSONObject("p-ana").getJSONArray("stations")
        assertEquals(listOf("CDMX Noticias", "Los 40"), objs(st).map { it.getString("name") })
    }
    // review: when the main-language list fails, the English one is used and its titles are English: they keep their English logos
    @Test fun `titles from the english fallback are marked as shown in english and keep their english logo`() {
        val kn = Profile("p-kn", "Ravi", Home("IN", listOf(Place("Mysuru", 12.30, 76.64, "Asia/Kolkata"))), listOf("kn", "it"), cork, emptyList(), 0L)
        val page = """{"results":[{"id":8,"title":"The Great Beauty","original_title":"La grande bellezza","original_language":"it","overview":"o","release_date":"2013-01-01","genre_ids":[18],"popularity":9,"vote_average":8}]}"""
        val detail = """{"images":{"logos":[{"file_path":"/en.png","iso_639_1":"en"}]}}"""
        val fetch: (String) -> String? = { u -> when {
            u.contains("/discover/movie") && u.contains("with_original_language=it") && u.contains("page=1") -> if (u.contains("&language=")) null else page
            u.contains("/movie/8?") -> detail
            else -> null } }
        val s = store()
        assertTrue(refresher(s, fetch, people = listOf(kn), tmdbKey = "k").refreshCatalog())
        val t = objs(JSONObject(s.load("catalog.json")!!).getJSONArray("p-kn")).first { it.getInt("id") == 8 }
        assertEquals("The Great Beauty", t.getString("title")); assertEquals("https://image.tmdb.org/t/p/w500/en.png", t.getString("logo"))
    }
    @Test fun `no key means no catalogue requests at all`() {
        var calls = 0
        val r = refresher(store(), { if (it.contains("themoviedb")) calls++; null }, tmdbKey = "")
        assertFalse(r.refreshCatalog()); assertEquals(0, calls)
    }
    @Test fun `a title that leaves a service loses its label at the next successful refresh`() {
        val s = store(); var f = catalogFetch()
        val r = refresher(s, { f(it) }, tmdbKey = "k", installed = setOf("com.netflix.ninja"))
        r.refreshCatalog()
        assertEquals(1, JSONObject(s.load("catalog.json")!!).getJSONArray("p-bo").getJSONObject(0).getJSONArray("apps").length())
        val gone = fixture("tmdb_detail_579974.json").let { j -> JSONObject(j).also { o -> o.put("watch/providers", JSONObject().put("results", JSONObject())) }.toString() }
        f = { url -> if (url.contains("append_to_response")) gone else catalogFetch()(url) }
        val later = refresher(s, { f(it) }, tmdbKey = "k", installed = setOf("com.netflix.ninja"), clock = { now + 8 * 24 * 3600_000L })
        later.refreshCatalog()
        assertEquals(0, JSONObject(s.load("catalog.json")!!).getJSONArray("p-bo").getJSONObject(0).getJSONArray("apps").length())
    }

    @Test fun `a refresh pass reads the profiles once`() {
        var reads = 0
        val r = FeedRefresher(config, store(), { good[it] }, channels = mapOf("IN" to inChannels), festivals = festivals, profiles = { reads++; listOf(ann, bo) },
            interests = interests, countries = countries, languages = languages, handoffs = handoffs, providerApps = providerApps, now = { now })
        reads = 0
        r.pass { r.refreshLive(); r.refreshHomes(); r.refreshStations(); r.refreshChannels(); r.refreshLoves(); r.writePage() }
        assertEquals(1, reads)
        r.writePage(); assertTrue(reads > 1)   // outside a pass, each call reads the file again (a save may have changed it)
    }
}
