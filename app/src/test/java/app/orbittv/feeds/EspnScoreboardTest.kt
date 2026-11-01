package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EspnScoreboardTest {
    @Test fun `f1 sessions list every session of the weekend with the circuit`() {
        val s = EspnScoreboard.f1Sessions(fixture("espn_f1.json"))
        assertEquals(5, s.size)
        assertEquals("Mexico City Grand Prix · FP1", s[0].name); assertEquals(EventState.IN, s[0].state)
        assertEquals("Mexico City Grand Prix · Race", s[4].name); assertEquals(EventState.PRE, s[4].state)
        assertEquals("Autodromo Hermanos Rodriguez", s[4].venue)
        assertEquals(1793563200000L, s[4].startMs)
    }
    @Test fun `tennis matches flatten groupings and read set scores`() {
        val m = EspnScoreboard.tennisMatches(fixture("espn_atp.json"))
        val first = m.first()
        assertEquals("Erste Bank Open · Men's Singles", first.name)
        assertEquals(EventState.POST, first.state); assertEquals("Final", first.round)
        assertEquals("Jacob Fearnley", first.competitors.first { it.winner }.name)
        assertEquals("6 3 6", first.competitors.first { it.winner }.score)
    }
    // QA: "TBD v TBD" and "v" cards, and a live score shown as "3 0-6 0"
    private val tbd = """{"events":[{"name":"Paris Masters","groupings":[{"grouping":{"displayName":"Men's Singles"},"competitions":[
        {"id":"1","date":"2026-11-01T12:00Z","status":{"type":{"state":"pre","shortDetail":"Scheduled"}},"competitors":[{"athlete":{"displayName":"TBD"}},{"athlete":{"displayName":"TBD"}}]},
        {"id":"2","date":"2026-11-01T12:00Z","status":{"type":{"state":"pre","shortDetail":"Scheduled"}},"competitors":[{},{}]},
        {"id":"3","date":"2026-11-01T10:00Z","status":{"type":{"state":"in","shortDetail":"3rd Set"}},"competitors":[
          {"athlete":{"displayName":"Ana Uno"},"linescores":[{"value":6},{"value":4},{"value":2}]},{"athlete":{"displayName":"Bea Dos"},"linescores":[{"value":3},{"value":6},{"value":1}]}]}]}]}]}"""
    @Test fun `tennis drops matches with unknown players and reads the score as sets`() {
        val m = EspnScoreboard.tennisMatches(tbd)
        assertEquals(listOf("3"), m.map { it.id })
        assertEquals("6-3 4-6 2-1", EspnScoreboard.setScore(m[0].competitors))
        assertEquals("", EspnScoreboard.setScore(m[0].competitors.take(1)))
    }
    // review: "TBD v TBD" was filtered for tennis only
    @Test fun `a fixture whose sides are not known yet is not an event`() {
        assertTrue(EspnScoreboard.events(fixture("espn_cricket_8039.json")).isEmpty())   // "TBC v TBC"
        assertTrue(EspnScoreboard.events(fixture("espn_soccer_eng1.json")).isNotEmpty())
    }
    @Test fun `malformed json yields no events`() {
        assertTrue(EspnScoreboard.events("{\"code\":404}").isEmpty()); assertTrue(EspnScoreboard.events("not json").isEmpty())
    }
}
