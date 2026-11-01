package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserTest {
    @Test fun `kannada stations are https, playable codecs, unique and at most eight`() {
        val s = RadioBrowser.parse(fixture("radio_kannada.json"))
        assertTrue(s.size in 1..8)
        assertTrue(s.all { it.url.startsWith("https://") && it.codec in setOf("MP3", "AAC", "AAC+", "UNKNOWN") })
        assertEquals(s.size, s.map { it.url }.toSet().size)
        assertTrue(s.any { it.name.contains("Vividh Bharati") })
    }
    @Test fun `telugu keeps akashvani streams with an unknown codec`() {
        val s = RadioBrowser.parse(fixture("radio_telugu.json"), max = 30)
        assertTrue(s.any { it.name.startsWith("Akashvani") })
    }
    @Test fun `cleartext, broken and odd codecs are dropped`() {
        val json = """[
          {"name":" Plain ","url_resolved":"http://x/a.mp3","codec":"MP3","bitrate":128,"lastcheckok":1},
          {"name":"Broken","url_resolved":"https://x/b.mp3","codec":"MP3","bitrate":128,"lastcheckok":0},
          {"name":"Ogg","url_resolved":"https://x/c.ogg","codec":"OGG","bitrate":128,"lastcheckok":1},
          {"name":" Good ","url_resolved":"https://x/d.mp3","codec":"MP3","bitrate":128,"lastcheckok":1},
          {"name":"Good again","url_resolved":"https://x/d.mp3","codec":"MP3","bitrate":128,"lastcheckok":1}]"""
        assertEquals(listOf(Station("Good", "https://x/d.mp3", "MP3", 128)), RadioBrowser.parse(json))
    }
    @Test fun `junk gives nothing`() { assertEquals(emptyList<Station>(), RadioBrowser.parse("<html>")) }
}
