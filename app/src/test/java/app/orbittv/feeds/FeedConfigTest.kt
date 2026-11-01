package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeedConfigTest {
    @Test fun `bundled config parses and holds no followed teams or hand-offs`() {
        val text = File("src/main/assets/feeds.json").readText()
        val c = FeedConfig.parse(text)
        assertEquals("8048", c.cricketLeagues.first { it.name == "IPL" }.id)
        assertTrue("Wimbledon" in c.tennisRounds)
        assertEquals("IN", c.venues.getValue("Wankhede Stadium").country)
        assertTrue(c.venues.values.all { it.country.length == 2 })
        assertTrue(c.motogp.isNotEmpty())
        assertFalse(text.contains("cricketTeams")); assertFalse(text.contains("playersRegex")); assertFalse(text.contains("\"handoff\""))
        assertFalse(text.contains("\"topics\"")); assertFalse(text.contains("\"cities\""))
    }

    @Test fun `hand-offs are looked up by country, then the most specific key first`() {
        val h = Handoffs.parse(File("src/main/assets/handoffs.json").readText())
        assertEquals("com.discoveryplus.tv.fire", h.find("IE", "cricket.home", "cricket").pkg)
        assertNull(h.find("IE", "f1").pkg); assertEquals("On Sky Sports / NOW", h.find("IE", "f1").label)
        assertEquals("in.startv.hotstar", h.find("IN", "cricket.ipl", "cricket").pkg)
        assertEquals(Handoff(null, ""), h.find("NG", "cricket.away", "cricket"))
        assertEquals(Handoff(null, ""), h.find("", "f1"))
        assertEquals(Handoff(null, ""), Handoffs.NONE.find("IE", "f1"))
    }
}
