package app.orbittv.feeds

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JourneysTest {
    private fun store() = FeedStore(Files.createTempDirectory("j").toFile())

    @Test fun `places, weather, native names and lines parse`() {
        val g = Journeys.parseGeocode(fixture("meteo_geo_kyoto.json")).first()
        assertEquals("Kyoto", g.name); assertEquals("Japan", g.country); assertEquals("JP", g.countryCode); assertEquals("Asia/Tokyo", g.tz); assertTrue(g.lat > 34 && g.lon > 135)
        val w = Journeys.parseForecast(fixture("meteo_forecast_kyoto.json"))!!
        assertTrue(w.sunrise.matches(Regex("""\d\d:\d\d""")) && w.sunset.matches(Regex("""\d\d:\d\d""")) && w.desc.isNotBlank())
        assertEquals("京都市", Journeys.parseNativeName(fixture("wikidata_kyoto.json")))
        assertNull(Journeys.parseNativeName("""{"entities":{"Q1":{"claims":{}}}}"""))
        assertEquals("City in the Kansai region of Japan", Journeys.parseSummary(fixture("wiki_summary_kyoto.json")))
    }

    @Test fun `footage is long enough, clean and HD first`() {
        val ids = Journeys.parseSearch(fixture("yt_search_kyoto_walk.json")); assertEquals(12, ids.size)
        val v = Journeys.parseVideos(fixture("yt_videos_kyoto.json"), minMinutes = 8, live = false)
        assertTrue(v.isNotEmpty() && v.all { it.minutes >= 8 && !it.live })
        assertFalse(v.any { it.id == "Rl_YSqygFEM" })   // 19 s and a sensational title
        assertFalse(v.any { it.id == "tJUoA0YmU-M" })   // 55 s
        assertEquals(71, Journeys.minutes("PT1H11M42S")); assertEquals(0, Journeys.minutes("PT19S")); assertEquals(45, Journeys.minutes("PT45M25S"))
        assertTrue(Journeys.parseSearch(fixture("yt_search_live.json")).size == 5)
    }

    @Test fun `no key means no network at all`() {
        var calls = 0
        val s = JourneyService({ calls++; null }, store(), key = "") { 1_000L }
        val r = JSONObject(s.handle("""{"op":"videos","id":"1","name":"Kyoto","mode":"walk"}"""))
        assertFalse(r.getBoolean("ok")); assertEquals("nokey", r.getString("error")); assertEquals(0, calls)
    }

    @Test fun `the daily quota stops at eighty searches and results are cached`() {
        var searches = 0
        val fetch: (String) -> String? = { u -> if (u.contains("/search?")) { searches++; fixture("yt_search_kyoto_walk.json") } else fixture("yt_videos_kyoto.json") }
        val s = JourneyService(fetch, store(), key = "k") { 86_400_000L * 3 + 1000 }
        repeat(80) { i -> assertTrue(JSONObject(s.handle("""{"op":"videos","id":"$i","name":"Place$i","mode":"walk"}""")).getBoolean("ok")) }
        val over = JSONObject(s.handle("""{"op":"videos","id":"x","name":"Another","mode":"walk"}"""))
        assertFalse(over.getBoolean("ok")); assertEquals("quota", over.getString("error"))
        assertTrue(JSONObject(s.handle("""{"op":"videos","id":"y","name":"Place3","mode":"walk"}""")).getBoolean("ok"))   // cached: no search needed
        assertEquals(80, searches)
        assertFalse(s.handle("""{"op":"videos","id":"y","name":"Place3","mode":"walk"}""").contains("key="))
    }

    @Test fun `a mode with no footage says so`() {
        val s = JourneyService({ u -> if (u.contains("/search?")) """{"items":[]}""" else """{"items":[]}""" }, store(), key = "k") { 1000L }
        val r = JSONObject(s.handle("""{"op":"videos","id":"1","name":"Tiny town","mode":"drive"}"""))
        assertTrue(r.getBoolean("ok")); assertEquals(0, r.getJSONArray("videos").length())
    }
    @Test fun `a place comes with a Wikipedia photograph`() {
        val img = Journeys.parseSummaryImage(fixture("wiki_summary_kyoto.json"))!!; assertTrue(img, img.contains("wikimedia.org") && img.contains("/1280px-"))
        val fetch: (String) -> String? = { u -> when { u.contains("open-meteo") -> fixture("meteo_forecast_kyoto.json"); u.contains("wikidata") -> fixture("wikidata_kyoto.json"); u.contains("wikipedia") -> fixture("wiki_summary_kyoto.json"); else -> null } }
        val r = JSONObject(JourneyService(fetch, store(), key = "") { 1000L }.handle("""{"op":"place","id":"1","name":"Kyoto","lat":35.0,"lon":135.7,"tz":"Asia/Tokyo"}"""))
        assertTrue(r.getJSONObject("place").getString("image").contains("/1280px-"))
        assertEquals("京都市", r.getJSONObject("place").getString("local"))
    }

    @Test fun `a failed details call is a network error and is not cached for a week`() {
        var detailsUp = false
        val fetch: (String) -> String? = { u -> if (u.contains("/search?")) fixture("yt_search_kyoto_walk.json") else if (detailsUp) fixture("yt_videos_kyoto.json") else null }
        val s = JourneyService(fetch, store(), key = "k") { 1000L }
        val first = JSONObject(s.handle("""{"op":"videos","id":"1","name":"Kyoto","mode":"walk"}"""))
        assertFalse(first.getBoolean("ok")); assertEquals("net", first.getString("error"))
        detailsUp = true
        val again = JSONObject(s.handle("""{"op":"videos","id":"2","name":"Kyoto","mode":"walk"}"""))
        assertTrue(again.getBoolean("ok")); assertTrue(again.getJSONArray("videos").length() > 0)
    }

    @Test fun `the quota day follows Google's Pacific day, not UTC`() {
        val fetch: (String) -> String? = { u -> if (u.contains("/search?")) fixture("yt_search_kyoto_walk.json") else fixture("yt_videos_kyoto.json") }
        var t = java.time.Instant.parse("2026-10-30T23:00:00Z").toEpochMilli()   // 16:00 in California, 30 October
        val s = JourneyService(fetch, store(), key = "k") { t }
        repeat(80) { i -> assertTrue(JSONObject(s.handle("""{"op":"videos","id":"$i","name":"Place$i","mode":"walk"}""")).getBoolean("ok")) }
        t = java.time.Instant.parse("2026-10-31T01:00:00Z").toEpochMilli()        // a new UTC day, still 30 October in California
        assertEquals("quota", JSONObject(s.handle("""{"op":"videos","id":"a","name":"Other","mode":"walk"}""")).getString("error"))
        t = java.time.Instant.parse("2026-10-31T08:00:00Z").toEpochMilli()        // 01:00 on 31 October in California
        assertTrue(JSONObject(s.handle("""{"op":"videos","id":"b","name":"Other","mode":"walk"}""")).getBoolean("ok"))
    }

    @Test fun `geocode answers carry the country code so setup can map a town to its country`() {
        val s = JourneyService({ fixture("meteo_geo_kyoto.json") }, store(), key = "") { 1000L }
        val r = JSONObject(s.handle("""{"op":"geocode","id":"g","query":"Kyoto"}"""))
        assertEquals("JP", r.getJSONArray("results").getJSONObject(0).getString("cc"))
    }

    @Test fun `the cache sweep drops files well past their age and keeps the rest`() {
        val dir = Files.createTempDirectory("j").toFile(); val st = FeedStore(dir)
        val day = 86_400_000L; val base = 1_800_000_000_000L
        fun put(name: String, ageMs: Long) { st.save(name, "{}"); java.io.File(dir, name).setLastModified(base - ageMs) }
        put("j_wx_kyoto.json", 2 * day); put("j_wx_fresh.json", 60_000L)
        put("j_vid_kyoto_walk.json", 9 * day); put("j_vid_recent.json", 6 * day)
        put("j_geo_kyoto.json", 32 * day); put("j_native_kyoto.json", 20 * day); put("j_line_kyoto.json", 40 * day)
        put("j_quota.json", 400 * day)
        val s = JourneyService({ null }, st, key = "") { base }
        assertEquals(4, s.sweep())
        assertEquals(listOf("j_native_kyoto.json", "j_quota.json", "j_vid_recent.json", "j_wx_fresh.json"), dir.list()!!.sorted())
    }

    @Test fun `requests sweep the cache at most once a day`() {
        val dir = Files.createTempDirectory("j").toFile(); val st = FeedStore(dir)
        val day = 86_400_000L; var t = 1_800_000_000_000L
        st.save("j_wx_old.json", "{}"); java.io.File(dir, "j_wx_old.json").setLastModified(t - 2 * day)
        val s = JourneyService({ null }, st, key = "") { t }
        s.handle("""{"op":"nope","id":"1"}""")
        assertFalse(java.io.File(dir, "j_wx_old.json").exists())
        st.save("j_wx_old2.json", "{}"); java.io.File(dir, "j_wx_old2.json").setLastModified(t - 2 * day)
        t += day / 2; s.handle("""{"op":"nope","id":"2"}""")
        assertTrue(java.io.File(dir, "j_wx_old2.json").exists())
        t += day; s.handle("""{"op":"nope","id":"3"}""")
        assertFalse(java.io.File(dir, "j_wx_old2.json").exists())
    }
}
