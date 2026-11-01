package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonightTest {
    private val lines = fixture("netflix_top10_ie.tsv").lineSequence()

    @Test fun `top10 keeps only the latest irish week, films then tv, in rank order`() {
        val top = Tonight.parseTop10(fixture("netflix_top10_ie.tsv").lineSequence(), "Ireland")
        assertEquals(20, top.size)
        assertEquals(1, top.distinctBy { it.week }.size)
        assertEquals(top.maxOf { it.week }, top[0].week)
        assertEquals(listOf("film", "tv"), top.map { it.kind }.distinct())
        assertEquals((1..10).toList(), top.filter { it.kind == "film" }.map { it.rank })
        assertTrue(top.none { it.title.isBlank() })
    }
    @Test fun `top10 is read for the named country only, and a country with no rows has none`() {
        val uk = Tonight.parseTop10(lines, "United Kingdom")
        assertTrue(uk.isNotEmpty()); assertTrue(uk.size < 20)
        assertEquals(emptyList<TopTitle>(), Tonight.parseTop10(lines, "Germany"))
    }
    @Test fun `tvmaze search accepts only the exact-name netflix show`() {
        val show = fixture("tvmaze_diplomat.json")
        val search = "[{\"score\":0.9,\"show\":$show},{\"score\":0.5,\"show\":{\"name\":\"The Diplomat\",\"network\":{\"name\":\"Alibi\"},\"image\":{\"original\":\"https://x/uk.jpg\"},\"summary\":\"<p>UK version</p>\"}}]"
        val p = Tonight.posterFromTvmazeSearch(search, "The Diplomat")!!
        assertTrue(p.image!!.startsWith("https://static.tvmaze.com/")); assertTrue(!p.blurb!!.contains("<"))
        assertNull(Tonight.posterFromTvmazeSearch(search, "Diplomat"))                       // fuzzy hit, wrong title
        assertNull(Tonight.posterFromTvmazeSearch("[{\"score\":1,\"show\":{\"name\":\"Spike\",\"network\":{\"name\":\"Sohu TV\"},\"image\":{\"original\":\"https://x/s.jpg\"}}}]", "Spike"))  // right name, not Netflix
        assertNull(Tonight.posterFromTvmazeSearch("junk", "Spike"))
    }
    @Test fun `wikipedia rejects disambiguation pages and shrinks huge images`() {
        val dis = "{\"type\":\"disambiguation\",\"extract\":\"Unforgettable may refer to a film or a television series.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/x.jpg\"},\"thumbnail\":{\"source\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/b/x.jpg/320px-x.jpg\"}}"
        assertNull(Tonight.posterFromWiki(dis))
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/thumb/a/b/x.jpg/1280px-x.jpg", Tonight.wikiImage("{\"thumbnail\":{\"source\":\"https://upload.wikimedia.org/wikipedia/commons/thumb/a/b/x.jpg/320px-x.jpg\"},\"originalimage\":{\"source\":\"https://upload.wikimedia.org/wikipedia/commons/a/b/x.jpg\"}}"))
    }
    @Test fun `old top10 weeks are stale and poster misses are retried after three days`() {
        assertTrue(Tonight.isFresh("2026-10-26", 1_793_500_000_000L)); assertTrue(!Tonight.isFresh("2021-07-04", 1_793_500_000_000L))
        assertTrue(Tonight.shouldRetry(0L, 10L)); assertTrue(!Tonight.shouldRetry(10L, 10L + 2 * 24 * 3600_000L)); assertTrue(Tonight.shouldRetry(10L, 10L + 4 * 24 * 3600_000L))
    }
    @Test fun `wikipedia poster has the original image and the extract, a missing page gives null`() {
        val p = Tonight.posterFromWiki(fixture("wiki_kpop.json"))!!
        assertTrue(p.image!!.contains("upload.wikimedia.org")); assertTrue(p.blurb!!.startsWith("KPop Demon Hunters"))
        assertNull(Tonight.posterFromWiki(fixture("wiki_missing.json"))); assertNull(Tonight.posterFromWiki("junk"))
    }
    @Test fun `a wikipedia page that is not about a film or series is rejected`() {
        val animals = "{\"title\":\"Animal\",\"extract\":\"Animals are multicellular, eukaryotic organisms belonging to the biological kingdom Animalia.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/x.jpg\"}}"
        assertNull(Tonight.posterFromWiki(animals))
        val film = "{\"title\":\"Animals (2026 film)\",\"extract\":\"Animals is a 2026 Irish comedy film released on Netflix.\",\"originalimage\":{\"source\":\"https://upload.wikimedia.org/y.jpg\"}}"
        assertTrue(Tonight.posterFromWiki(film)!!.image!!.endsWith("y.jpg"))
    }
    @Test fun `wikipedia title candidates try the medium first`() {
        assertEquals(listOf("KPop Demon Hunters (2026 film)", "KPop Demon Hunters (2025 film)", "KPop Demon Hunters (film)", "KPop Demon Hunters"), Tonight.wikiTitleCandidates("KPop Demon Hunters", "film", 2026))
        assertEquals(listOf("The Diplomat (TV series)", "The Diplomat"), Tonight.wikiTitleCandidates("The Diplomat", "tv", 2026))
    }
    @Test fun `toJson allows a missing poster and caps the blurb`() {
        val t = TopTitle(1, "Fatherhood", "film", "2021-07-04")
        val arr = Tonight.toJson(listOf(t to null, t.copy(rank = 2, title = "Long") to Poster("https://x/y.jpg", "a".repeat(400))))
        assertTrue(arr.getJSONObject(0).isNull("image")); assertEquals("com.netflix.ninja", arr.getJSONObject(0).getString("pkg"))
        assertEquals(160, arr.getJSONObject(1).getString("blurb").length)
    }
    @Test fun `tonight is due once a day, immediately only when nothing is cached`() {
        assertTrue(FeedSchedule.tonightDue(0L, 25 * 3600_000L, localHour = 12, cached = true)); assertTrue(!FeedSchedule.tonightDue(1L, 20 * 3600_000L, localHour = 12, cached = true))
        assertTrue(!FeedSchedule.tonightDue(1L, 48 * 3600_000L, localHour = 2, cached = true)); assertTrue(FeedSchedule.tonightDue(0L, 1L, localHour = 2, cached = false)); assertTrue(!FeedSchedule.tonightDue(0L, 1L, localHour = 2, cached = true))
    }
}
