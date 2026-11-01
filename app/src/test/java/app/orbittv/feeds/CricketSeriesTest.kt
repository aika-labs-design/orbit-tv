package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CricketSeriesTest {
    private val map = CricketSeries.parseHeader(fixture("espn_cricket_header.json")).toMap()
    @Test fun `series and format come from the header feed by match number`() {
        assertEquals(Series("Hong Kong International Cricket Sixes", "Sixes"), map["1559204"])
        assertEquals("Test", map["1525661"]!!.format)
        assertEquals("T20", map["1551890"]!!.format)
        assertEquals("ODI", map["1559566"]!!.format)
        assertEquals("First-class", map["1544871"]!!.format)
    }
    @Test fun `familiar names are shortened`() {
        assertEquals("IPL", CricketSeries.competition("Indian Premier League 2027"))
        assertEquals("Women's T20 World Cup", CricketSeries.competition("ICC Women's T20 World Cup 2026"))
        assertEquals("Hong Kong International Cricket Sixes", CricketSeries.competition("Hong Kong International Cricket Sixes"))
    }
    @Test fun `cricinfo links give the match number`() {
        assertEquals("1553805", CricinfoLive.matchId("http://www.cricinfo.com/ci/engine/match/1553805.html?CMP=OTC-RSS "))
        assertNull(CricinfoLive.matchId("http://www.cricinfo.com"))
    }
    @Test fun `junk gives nothing`() { assertTrue(CricketSeries.parseHeader("<html>").isEmpty()) }
}
