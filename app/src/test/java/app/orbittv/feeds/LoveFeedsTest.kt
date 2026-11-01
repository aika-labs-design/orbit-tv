package app.orbittv.feeds

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LoveFeedsTest {
    private val catalog = Interests.parse(File("src/main/assets/interests.json").readText())
    private val countries = World.countries(File("src/main/assets/countries.json").readText())
    private val ng = countries.first { it.code == "NG" }
    private val t0 = java.time.Instant.parse("2026-10-31T18:00:00Z").toEpochMilli()
    private val dir = Files.createTempDirectory("love").toFile()
    private fun store() = FeedStore(dir)
    private fun love(id: String, vararg refine: String, weight: Double? = null) = LoveRef(id, catalog.first { it.id == id }, refine.toList(), weight)

    private val calls = ArrayList<String>()
    private fun net(extra: Map<String, String> = emptyMap()): (String) -> String? = { u ->
        calls += u
        extra.entries.firstOrNull { u.contains(it.key) }?.value ?: when {
            u.contains("themoviedb.org/3/discover") -> fixture("tmdb_discover_keyword.json")
            u.contains("themoviedb.org/3/search/keyword") -> """{"results":[{"id":5,"name":"x"}]}"""
            u.contains("news.google.com") -> fixture("gnews_cooking_ng.xml")
            u.contains("youtube/v3/search") -> fixture("yt_search_cooking.json")
            u.contains("youtube/v3/videos") -> fixture("yt_videos_cooking.json")
            u.contains("espn.com") && u.contains("soccer/eng.1") -> fixture("espn_soccer_eng1.json")
            else -> null
        }
    }
    private fun feeds(fetch: (String) -> String? = net(), tmdb: String = "tk", yt: String = "yk", now: () -> Long = { t0 }) =
        LoveFeeds(fetch, store(), LoveKeys(tmdb, yt), YouTubeQuota(store(), now), now = now)
    private fun titles(a: List<JSONObject>) = a.map { it.getString("title") }

    @Test fun `cooking brings shows, news and videos that all pass the relevance gate`() {
        val cooking = love("cooking"); val terms = cooking.interest.terms
        val r = feeds().refreshAll(listOf(LoveBatch("", listOf(cooking), listOf("en"), ng))).getValue("").getValue("cooking")
        assertEquals(listOf("Chef's Table: Home Kitchens", "Jollof Wars", "Bake Off Naija"), titles(r.shows))   // the Korean title is not in an English profile
        assertEquals(listOf("Jollof rice recipe: how to cook party-style rice at home - Punch", "Chef Fregz shares five tips for the perfect stew - Guardian Nigeria", "Cooking oil prices ease in Mile 12 market - BusinessDay"), titles(r.news))
        assertEquals(listOf("Easy Home Cooking: One-Pot Jollof Rice Recipe", "A Chef Explains Knife Skills for Home Cooks"), titles(r.videos))
        (r.shows + r.news + r.videos).forEach { assertTrue(it.getString("title"), Relevance.keep(it.getString("title") + " " + it.optString("overview"), terms)) }
        (r.news + r.videos).forEach { assertFalse(Relevance.clickbait(it.getString("title"), terms)) }
        assertTrue(r.live.isEmpty()); assertEquals("Because you love Home cooking", r.why)
        assertTrue(calls.any { it.startsWith("https://news.google.com/rss/search") && it.contains("hl=en-NG&gl=NG&ceid=NG:en") })
    }

    // review: the set-score style was a tennis literal in app code; it is catalogue data now
    @Test fun `a sport's score style comes from the catalogue`() {
        assertEquals("sets", catalog.first { it.id == "tennis" }.live!!.scoreStyle)
        assertEquals(null, catalog.first { it.id == "football" }.live!!.scoreStyle)
    }
    @Test fun `a tennis love keeps known players only and shows a live score as sets`() {
        val atp = """{"leagues":[{"name":"ATP"}],"events":[{"name":"Paris Masters","groupings":[{"grouping":{"displayName":"Men's Singles"},"competitions":[
            {"id":"1","date":"2026-10-31T19:00Z","status":{"type":{"state":"pre","shortDetail":"Scheduled"}},"competitors":[{"athlete":{"displayName":"TBD"}},{"athlete":{"displayName":"TBD"}}]},
            {"id":"3","date":"2026-10-31T17:00Z","status":{"type":{"state":"in","shortDetail":"3rd Set"}},"competitors":[
              {"athlete":{"displayName":"Ana Uno"},"linescores":[{"value":6},{"value":4},{"value":2}]},{"athlete":{"displayName":"Bea Dos"},"linescores":[{"value":3},{"value":6},{"value":1}]}]}]}]}]}"""
        val r = feeds(net(mapOf("tennis/atp" to atp, "tennis/wta" to """{"events":[]}"""))).refreshAll(listOf(LoveBatch("", listOf(love("tennis")), listOf("en"), ng))).getValue("").getValue("tennis")
        val sport = r.live.filter { it.getString("kind") == "sport" }
        assertEquals(listOf("Ana Uno v Bea Dos"), titles(sport))
        assertEquals("6-3 4-6 2-1", sport[0].getString("score"))
    }

    // QA: Jazz with Latin jazz chosen played generic jazz radio
    @Test fun `a refine option with its own radio tag asks for that tag, the love's tag without one`() {
        feeds().refreshAll(listOf(LoveBatch("", listOf(love("jazz", "latin-jazz")), listOf("es"), ng)))
        assertTrue(calls.any { it.contains("radio-browser") && it.contains("tag=latin+jazz") })
        assertFalse(calls.any { it.contains("radio-browser") && Regex("tag=jazz(&|$)").containsMatchIn(it) })
        calls.clear()
        feeds().refreshAll(listOf(LoveBatch("", listOf(love("jazz", "bebop")), listOf("es"), ng)))
        assertTrue(calls.any { it.contains("radio-browser") && Regex("tag=jazz(&|$)").containsMatchIn(it) })
    }

    @Test fun `a pidgin-first person gets news and videos in the next language Google and YouTube know`() {
        feeds().refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("pcm", "en"), ng))).getValue("")
        assertTrue(calls.any { it.startsWith("https://news.google.com/rss/search") && it.contains("hl=en-NG&gl=NG&ceid=NG:en") })
        assertFalse(calls.any { it.contains("hl=pcm") || it.contains("relevanceLanguage=pcm") })
    }

    @Test fun `a love whose fetched items all fail the gate comes back empty`() {
        val r = feeds().refreshAll(listOf(LoveBatch("", listOf(LoveRef("custom:sourdough", Interests.custom("sourdough"))), listOf("en"), ng))).getValue("").getValue("custom:sourdough")
        assertTrue(r.shows.isEmpty() && r.news.isEmpty() && r.videos.isEmpty() && r.live.isEmpty())
        assertTrue(calls.any { it.contains("search/keyword") })   // a custom love finds its shows by keyword search
    }

    @Test fun `with two searches left the two highest weighted loves are fetched and Journeys keeps its twenty`() {
        val q = YouTubeQuota(store()) { t0 }; repeat(58) { assertTrue(q.take(YouTubeQuota.LOVES_LIMIT)) }
        val ids = listOf("cooking", "football", "cricket", "tennis", "golf"); val weights = listOf(1.0, 5.0, 3.0, 2.0, 4.0)
        val loves = ids.mapIndexed { i, id -> love(id, weight = weights[i]) }
        feeds().refreshAll(listOf(LoveBatch("", loves, listOf("en"), ng))).getValue("")
        val searches = calls.filter { it.contains("youtube/v3/search") }
        assertEquals(2, searches.size)
        assertTrue(searches[0], searches[0].contains("football")); assertTrue(searches[1], searches[1].contains("golf"))
        repeat(20) { assertTrue(q.take()) }; assertFalse(q.take())   // 58 + 2 by loves, 20 left for Journeys
    }

    @Test fun `a love that cannot search serves its last cache, even when it is old`() {
        var t = t0; val f = feeds(now = { t })
        assertEquals(2, f.refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("en"), ng))).getValue("").getValue("cooking").videos.size)
        t += 25 * 3600_000L; calls.clear()
        val q = YouTubeQuota(store()) { t }; repeat(60) { q.take(YouTubeQuota.LOVES_LIMIT) }
        assertEquals(2, f.refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("en"), ng))).getValue("").getValue("cooking").videos.size)
        assertTrue(calls.none { it.contains("youtube/v3") })
    }

    @Test fun `videos are cached a day per love, language and country`() {
        val f = feeds(); val c = listOf(love("cooking"))
        f.refreshAll(listOf(LoveBatch("", c, listOf("en"), ng))).getValue(""); val first = calls.count { it.contains("youtube/v3/search") }
        f.refreshAll(listOf(LoveBatch("", c, listOf("en"), ng))).getValue(""); assertEquals(first, calls.count { it.contains("youtube/v3/search") })
        f.refreshAll(listOf(LoveBatch("", c, listOf("yo"), ng))).getValue(""); assertEquals(first + 1, calls.count { it.contains("youtube/v3/search") })
    }

    @Test fun `without keys nothing is requested from YouTube or TMDB and nothing fails`() {
        val r = feeds(tmdb = "", yt = "").refreshAll(listOf(LoveBatch("", listOf(love("cooking"), love("chess")), listOf("en"), ng))).getValue("")
        assertTrue(calls.none { it.contains("googleapis.com") || it.contains("themoviedb.org") })
        assertTrue(r.getValue("cooking").videos.isEmpty() && r.getValue("cooking").shows.isEmpty() && r.getValue("chess").live.isEmpty())
        assertEquals(3, r.getValue("cooking").news.size)   // keyless sources still work
    }

    @Test fun `a followed team narrows live football to its matches, and states sort live first`() {
        val all = feeds().refreshAll(listOf(LoveBatch("", listOf(love("football")), listOf("en"), ng))).getValue("").getValue("football").live
        assertEquals(listOf("in", "pre", "post"), all.map { it.getString("state") })
        calls.clear()
        val one = feeds().refreshAll(listOf(LoveBatch("", listOf(love("football", "arsenal")), listOf("en"), ng))).getValue("").getValue("football").live
        assertEquals(listOf("Arsenal v Fulham"), titles(one))
        assertTrue(calls.any { it.contains("sports/soccer/eng.1/scoreboard") })
        assertTrue(calls.none { it.contains("soccer/esp.1") })   // the option's own league only
    }

    @Test fun `the page carries loves per profile and no key appears in it or in any cache file`() {
        val tk = "TMDBSECRET9981"; val yk = "YTSECRET7742"
        val f = LoveFeeds(net(), store(), LoveKeys(tk, yk), YouTubeQuota(store()) { t0 }) { t0 }
        val cfg = FeedConfig.parse(File("src/main/assets/feeds.json").readText())
        val p = Profile("p1", "Ada", Home("NG", listOf(Place("Lagos", 6.45, 3.39, "Africa/Lagos"))), listOf("en"), Living("Lagos", 6.45, 3.39, "Africa/Lagos", "NG"), listOf(Love("cooking", emptyList()), Love("football", listOf("arsenal"))), 0L)
        val r = FeedRefresher(cfg, store(), net(), loveFeeds = f, profiles = { listOf(p) }, interests = catalog, countries = countries, now = { t0 })
        assertTrue(r.refreshLoves())
        val page = JSONObject(r.writePage())
        val o = page.getJSONObject("loves").getJSONObject("p1").getJSONObject("cooking")
        listOf("shows", "news", "videos", "live").forEach { assertTrue(it, o.has(it)) }
        assertEquals(3, o.getJSONArray("shows").length()); assertTrue(o.getString("why").isNotBlank())
        assertEquals(1, page.getJSONObject("loves").getJSONObject("p1").getJSONObject("football").getJSONArray("live").length())
        val blob = page.toString() + dir.listFiles()!!.filter { it.isFile }.joinToString("\n") { it.readText() }
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("love_") })
        assertFalse(blob.contains(tk)); assertFalse(blob.contains(yk)); assertFalse(blob.contains("api_key")); assertFalse(blob.contains("key="))
        assertFalse(LoveKeys(tk, yk).toString().contains(tk))
    }

    @Test fun `the quota class counts per Pacific day and stops at its limit`() {
        var t = java.time.Instant.parse("2026-10-30T23:00:00Z").toEpochMilli(); val q = YouTubeQuota(store()) { t }
        repeat(3) { assertTrue(q.take(3)) }; assertFalse(q.take(3)); assertTrue(q.take(80))
        t = java.time.Instant.parse("2026-10-31T08:00:00Z").toEpochMilli(); assertTrue(q.take(3))
    }

    @Test fun `shows follow the person's languages, all of them, in the request and in the result`() {
        val en = feeds().refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("en", "yo"), ng))).getValue("").getValue("cooking")
        assertFalse(titles(en.shows).contains("Kimchi Kitchen"))
        assertTrue(calls.any { it.contains("discover/tv") && it.contains("with_original_language=en%7Cyo") })
        val ko = feeds().refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("ko"), ng))).getValue("").getValue("cooking")
        assertTrue(titles(ko.shows).contains("Kimchi Kitchen")); assertFalse(titles(ko.shows).contains("Jollof Wars"))
    }

    @Test fun `YouTube live is searched once a day per love, sport live keeps its short cache`() {
        var t = t0; val f = feeds(now = { t }); val chess = listOf(love("chess"))
        f.refreshAll(listOf(LoveBatch("", chess, listOf("en"), ng))).getValue(""); t += 2 * 3600_000L; f.refreshAll(listOf(LoveBatch("", chess, listOf("en"), ng))).getValue("")
        val live = calls.filter { it.contains("youtube/v3/search") && it.contains("eventType=live") }
        assertEquals(1, live.size)
        calls.clear(); val fb = listOf(love("football")); f.refreshAll(listOf(LoveBatch("", fb, listOf("en"), ng))).getValue(""); t += 20 * 60_000L; f.refreshAll(listOf(LoveBatch("", fb, listOf("en"), ng))).getValue("")
        assertEquals(2, calls.count { it.contains("sports/soccer/eng.1/scoreboard") })
    }

    @Test fun `the heaviest love in the household gets the last search, whichever profile it is in`() {
        val q = YouTubeQuota(store()) { t0 }; repeat(59) { q.take(YouTubeQuota.LOVES_LIMIT) }
        val one = LoveBatch("p1", listOf(love("cooking", weight = 0.0)), listOf("en"), ng)
        val two = LoveBatch("p2", listOf(love("football", weight = 10.0)), listOf("en"), ng)
        val out = feeds().refreshAll(listOf(one, two))
        val searches = calls.filter { it.contains("youtube/v3/search") }
        assertEquals(1, searches.size); assertTrue(searches[0], searches[0].contains("football"))
        assertEquals(setOf("p1", "p2"), out.keys)
    }

    @Test fun `a match that has not kicked off carries no score`() {
        val pre = feeds().refreshAll(listOf(LoveBatch("", listOf(love("football")), listOf("en"), ng))).getValue("").getValue("football").live.single { it.getString("state") == "pre" }
        assertEquals("", pre.getString("score"))
    }

    @Test fun `a failing YouTube search spends one search an hour, not one per refresh`() {
        var t = t0; val down = net(mapOf("youtube/v3" to "")).let { n -> { u: String -> if (u.contains("youtube/v3")) { calls += u; null } else n(u) } }
        val f = feeds(fetch = down, now = { t }); val c = listOf(love("cooking"))
        repeat(3) { f.refreshAll(listOf(LoveBatch("", c, listOf("en"), ng))).getValue(""); t += 15 * 60_000L }
        assertEquals(1, calls.count { it.contains("youtube/v3/search") })
        t = t0 + 61 * 60_000L; f.refreshAll(listOf(LoveBatch("", c, listOf("en"), ng))).getValue("")
        assertEquals(2, calls.count { it.contains("youtube/v3/search") })   // tried again after an hour
        val q = YouTubeQuota(store()) { t }; repeat(YouTubeQuota.LOVES_LIMIT - 2) { assertTrue(q.take(YouTubeQuota.LOVES_LIMIT)) }; assertFalse(q.take(YouTubeQuota.LOVES_LIMIT))   // two spent in all
    }

    @Test fun `failures on six different loves do not lock the day, so YouTube back 15 minutes later is searched`() {
        var t = t0; var up = false
        val net = net(); val fetch = { u: String -> if (u.contains("youtube/v3") && !up) { calls += u; null } else net(u) }
        val f = feeds(fetch = fetch, now = { t }); val six = catalog.filter { it.videos != null }.take(6).map { love(it.id) }
        assertEquals(6, six.size)
        f.refreshAll(listOf(LoveBatch("", six, listOf("en"), ng))).getValue("")
        assertEquals(6, calls.count { it.contains("youtube/v3/search") })
        calls.clear(); up = true; t += 15 * 60_000L
        val r = f.refreshAll(listOf(LoveBatch("", listOf(love("cooking")), listOf("en"), ng))).getValue("").getValue("cooking")
        assertEquals(1, calls.count { it.contains("youtube/v3/search") })
        assertTrue(r.videos.isNotEmpty())
    }

    @Test fun `Journeys searches are never blocked by failed love searches`() {
        val q = YouTubeQuota(store()) { t0 }
        repeat(8) { assertTrue(q.take(YouTubeQuota.LOVES_LIMIT)); q.failed("k$it") }
        assertTrue(q.take()); assertTrue(q.take(YouTubeQuota.LOVES_LIMIT))
        assertTrue(q.resting("k0")); assertFalse(q.resting("other"))
    }

    @Test fun `a love with no saved weight counts as 1, as on the page`() {
        val q = YouTubeQuota(store()) { t0 }; repeat(59) { q.take(YouTubeQuota.LOVES_LIMIT) }
        feeds().refreshAll(listOf(LoveBatch("", listOf(love("cooking", weight = 0.5), love("football")), listOf("en"), ng))).getValue("")
        val searches = calls.filter { it.contains("youtube/v3/search") }
        assertEquals(1, searches.size); assertTrue(searches[0], searches[0].contains("football"))
    }
}
