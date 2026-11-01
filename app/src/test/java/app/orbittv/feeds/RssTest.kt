package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

fun fixture(name: String): String = File("src/test/resources/feeds/$name").readText()

class RssTest {
    @Test fun `google news items carry source and decoded titles`() {
        val items = Rss.parse(fixture("gnews_karnataka.xml"))
        assertTrue(items.size >= 20)
        val first = items.first()
        assertEquals("The Hindu", first.source)
        assertTrue(first.title.endsWith(" - The Hindu"))
        assertTrue(first.publishedMs > 1_700_000_000_000L)
        assertFalse(items.any { it.title.contains("&amp;") || it.title.contains("&#") })
    }
    @Test fun `cdata titles and descriptions are unwrapped`() {
        val items = Rss.parse(fixture("therace_motogp.xml"))
        assertEquals("Why 'motocross' Sepang wasn't fixed - and is bruising riders", items.first().title)
        assertTrue(items.first().description!!.startsWith("Multiple MotoGP riders"))
    }
    @Test fun `cricinfo items have no pubDate and keep the full score title`() {
        val items = Rss.parse(fixture("cricinfo_livescores.xml"))
        assertEquals("South Africa 387/9 & 329/10 * v Australia 429/10", items[1].title.trim())
        assertEquals(0L, items[1].publishedMs)
    }
    @Test fun `junk input yields no items`() {
        assertTrue(Rss.parse("<!DOCTYPE html><html>Page Not Found</html>").isEmpty())
        assertTrue(Rss.parse("").isEmpty())
    }
}
