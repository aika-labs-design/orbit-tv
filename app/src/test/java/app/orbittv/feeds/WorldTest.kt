package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorldTest {
    private val countries = World.countries(File("src/main/assets/countries.json").readText())
    private val languages = World.languages(File("src/main/assets/languages.json").readText())
    private fun by(code: String) = countries.single { it.code == code }

    @Test fun `Nigeria has its languages and Lagos`() {
        val ng = by("NG")
        assertEquals("Nigeria", ng.name)
        assertTrue(ng.languages.containsAll(listOf("en", "yo", "ig", "ha")))
        assertTrue(ng.cities.any { it.name == "Lagos" && it.tz == "Africa/Lagos" })
        assertTrue(ng.cities.size >= 3)
        assertEquals("Nigeria", ng.netflixName)
    }

    @Test fun `news url uses the country edition when there is one`() {
        val url = World.newsUrl("election", by("NG"), "en")
        assertTrue(url, url.startsWith("https://news.google.com/rss/search?q=election+when%3A1d"))
        assertTrue(url, url.contains("hl=en-NG&gl=NG&ceid=NG:en"))
    }

    @Test fun `news skips a 639-3 language Google News does not know, for the next one or the country edition`() {
        assertEquals(listOf("en", "de"), World.newsLanguages(listOf("pcm", "en", "de"), by("NG")))
        assertEquals(listOf("en"), World.newsLanguages(listOf("pcm"), by("NG")))   // NG edition is NG:en
        assertEquals(listOf("en"), World.newsLanguages(listOf("pcm"), null))
        assertTrue(World.newsUrl("lagos", by("NG"), World.newsLanguages(listOf("pcm", "en"), by("NG")).first()).contains("&gl=NG&ceid=NG:en"))
        assertTrue("pcm" in by("NG").languages)
    }

    @Test fun `news url without an edition uses the language only`() {
        val is_ = by("IS")
        assertNull(is_.news)
        val india = by("IN")
        assertTrue(World.newsUrl("cricket", india, "kn").contains("&hl=kn&gl=IN&ceid=IN:kn"))
        assertTrue(World.newsUrl("cricket", india, "en").contains("&hl=en-IN&gl=IN&ceid=IN:en"))
        assertEquals("https://news.google.com/rss/search?q=fotbolti+when%3A1d&hl=is", World.newsUrl("fotbolti", is_, "is"))
        assertEquals("https://news.google.com/rss/search?q=x+when%3A1d&hl=de", World.newsUrl("x", null, "de"))
    }

    @Test fun `languages parse with film names and greetings`() {
        val yo = languages.single { it.code == "yo" }
        assertEquals("Nollywood", yo.film)
        assertNotNull(yo.greeting)
        val icelandic = languages.single { it.code == "is" }
        assertNull(icelandic.film); assertNull(icelandic.greeting)
        assertTrue(languages.single { it.code == "ar" }.rtl)
        assertEquals("Good morning", languages.single { it.code == "en" }.greeting?.morning)
    }
}
