package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChannelsTest {
    @Test fun `a live page resolves to live with an id and a title`() {
        val (live, id, title) = Channels.resolve(fixture("yt_live_page.html"))
        assertTrue(live); assertEquals(11, id!!.length); assertTrue(title!!.startsWith("NDTV LIVE"))
    }
    @Test fun `an offline page keeps the id but is not live`() {
        val (live, id, title) = Channels.resolve(fixture("yt_offline_page.html"))
        assertFalse(live); assertEquals("abcDEF12345", id); assertEquals("Public TV Kannada Live", title)
    }
    @Test fun `junk resolves to nothing`() { val (live, id, title) = Channels.resolve("<html>consent</html>"); assertFalse(live); assertNull(id); assertNull(title) }
    @Test fun `bundled channels have no YuppTV and every youtube entry has a handle`() {
        val list = Channels.parse(File("src/main/assets/channels/IN.json").readText())
        assertTrue(list.none { it.name.contains("Yupp", true) })
        assertTrue(list.filter { it.pkg == null }.all { !it.handle.isNullOrBlank() })
        assertTrue(list.any { it.pkg == "in.startv.hotstar" })
    }
    @Test fun `toJson gives a watch url when live and the channel page otherwise`() {
        val list = Channels.parse(File("src/main/assets/channels/IN.json").readText())
        val states = mapOf("tv9k" to ChannelState("tv9k", true, "KYJLbD9bd78", "TV9 live", Channels.thumb("KYJLbD9bd78"), 5L), "public" to ChannelState("public", false, "abcDEF12345", null, null, 5L))
        val arr = Channels.toJson(list, states)
        val byId = (0 until arr.length()).map { arr.getJSONObject(it) }.associateBy { it.getString("id") }
        assertEquals("https://www.youtube.com/watch?v=KYJLbD9bd78", byId.getValue("tv9k").getString("url")); assertTrue(byId.getValue("tv9k").getBoolean("live"))
        assertEquals("https://www.youtube.com/@publictv/live", byId.getValue("public").getString("url")); assertFalse(byId.getValue("public").getBoolean("live"))
        assertEquals("https://www.youtube.com/@suvarnanews/live", byId.getValue("suvarna").getString("url"))
        assertEquals("com.amazon.firetv.youtube", byId.getValue("tv9k").getString("pkg")); assertEquals("in.startv.hotstar", byId.getValue("hotstar").getString("pkg"))
    }
    @Test fun `channel states are seeded from the stored page after a restart`() {
        val dir = java.nio.file.Files.createTempDirectory("feeds").toFile(); val store = FeedStore(dir)
        val list = Channels.parse(File("src/main/assets/channels/IN.json").readText())
        val states = mapOf("tv9k" to ChannelState("tv9k", true, "KYJLbD9bd78", "TV9 live", Channels.thumb("KYJLbD9bd78"), 123L))
        store.save(FeedStore.PAGE, org.json.JSONObject().put("live", org.json.JSONArray()).put("today", org.json.JSONArray()).put("channels", Channels.toJson(list, states)).toString())
        val config = FeedConfig.parse(File("src/main/assets/feeds.json").readText())
        val home = Profile("p", "P", Home("IN", listOf(Place("B", 1.0, 2.0, "Asia/Kolkata"))), listOf("kn"), Living("D", 1.0, 2.0, "UTC", "XX"), emptyList(), 0L)
        val r = FeedRefresher(config, store, { null }, channels = mapOf("IN" to list), profiles = { listOf(home) })
        val page = org.json.JSONObject(r.writePage()).getJSONArray("channels")
        val tv9 = (0 until page.length()).map { page.getJSONObject(it) }.first { it.getString("id") == "tv9k" }
        assertTrue(tv9.getBoolean("live")); assertEquals(123L, tv9.getLong("resolvedMs"))
    }
    @Test fun `channels are due every thirty minutes outside quiet hours`() {
        assertTrue(FeedSchedule.channelsDue(0L, 30 * 60_000L + 1, localHour = 12, forced = false)); assertFalse(FeedSchedule.channelsDue(0L, 29 * 60_000L, localHour = 12, forced = false)); assertFalse(FeedSchedule.channelsDue(0L, 60 * 60_000L, localHour = 2, forced = false))
    }
    @Test fun `a channel page is read only until its video id, live flag and title are in`() {
        val page = listOf("<html><head><meta property=\"og:title\" content=\"TV9 &amp; more\">", "x\"videoId\":\"KYJLbD9bd78\"x", "y\"isLiveNow\":true", "\"videoDetails\":{\"videoId\":\"KYJLbD9bd78\",\"title\":\"TV9 LIVE\"") + List(500) { "filler $it" }
        var read = 0
        val text = Channels.readLive("u") { _, onLine -> for (l in page) { read++; if (!onLine(l)) break }; true }!!
        assertEquals(4, read)
        assertEquals(Channels.resolve(page.joinToString("\n")), Channels.resolve(text))
        assertEquals(Triple(true, "KYJLbD9bd78", "TV9 LIVE"), Channels.resolve(text))
    }
    @Test fun `a page without the markers is read to the end, and a failed request gives nothing`() {
        val page = fixture("yt_offline_page.html")
        assertEquals(Channels.resolve(page), Channels.resolve(Channels.readLive("u") { _, onLine -> page.lines().forEach { onLine(it) }; true }!!))
        assertNull(Channels.readLive("u") { _, _ -> false })
    }

    @Test fun `a live search gives one channel per channel, live, with its watch url and the given tag`() {
        val found = Channels.fromLiveSearch(fixture("yt_search_live.json"), "Nigeria · News")
        assertEquals(listOf("EarthCam", "FOX 5 New York", "AE Signage", "ms36K"), found.map { it.first.name })
        val (c, st) = found[0]
        assertEquals("yt:UC6qrG3W8SMK0jior2olka3g", c.id); assertEquals("Nigeria · News", c.tag); assertNull(c.handle); assertNull(c.pkg)
        assertTrue(st.live); assertEquals("KHy_n-EMWY8", st.videoId)
        val j = Channels.toJson(listOf(c), mapOf(c.id to st)).getJSONObject(0)
        assertEquals("https://www.youtube.com/watch?v=KHy_n-EMWY8", j.getString("url")); assertEquals(Channels.YOUTUBE_PKG, j.getString("pkg"))
        assertEquals(emptyList<Pair<Channel, ChannelState>>(), Channels.fromLiveSearch("junk", "x"))
    }
}
