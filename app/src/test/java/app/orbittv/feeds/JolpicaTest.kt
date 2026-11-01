package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JolpicaTest {
    @Test fun `next race has coordinates and start time`() {
        val r = Jolpica.nextRace(fixture("jolpica_next.json"))!!
        assertEquals("Mexico City Grand Prix", r.name); assertEquals(19, r.round)
        assertEquals(19.4042, r.lat, 1e-4); assertEquals(-99.0907, r.lon, 1e-4); assertEquals(1793563200000L, r.startMs)
        assertEquals("Mexico City", r.locality)
    }
    @Test fun `standings keep order, driver family name and team`() {
        val s = Jolpica.driverStandings(fixture("jolpica_standings.json"))
        assertEquals(listOf(1, 2, 3), s.take(3).map { it.position }); assertEquals("Antonelli", s[0].driver); assertEquals("Mercedes", s[0].team); assertEquals(365.0, s[0].points, 0.0)
    }
    @Test fun `empty race table yields null`() { assertNull(Jolpica.nextRace("{\"MRData\":{\"RaceTable\":{\"Races\":[]}}}")); assertNull(Jolpica.nextRace("junk")) }
}
