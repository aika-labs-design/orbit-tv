package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CricinfoLiveTest {
    @Test fun `full title parses both innings and the batting side`() {
        val s = CricinfoLive.parseTitle("South Africa 387/9 & 329/10 * v Australia 429/10", "l")!!
        assertEquals("South Africa", s.teamA); assertEquals("387/9 & 329/10", s.scoreA); assertTrue(s.battingA)
        assertEquals("Australia", s.teamB); assertEquals("429/10", s.scoreB); assertFalse(s.battingB)
    }
    @Test fun `title with one score keeps the other empty`() {
        val s = CricinfoLive.parseTitle("Tasmania 335/5 * v New South Wales", "l")!!
        assertEquals("", s.scoreB); assertEquals("New South Wales", s.teamB); assertTrue(s.battingA)
    }
    @Test fun `title before the toss has no scores`() {
        val s = CricinfoLive.parseTitle("India v Australia", "l")!!
        assertEquals("India", s.teamA); assertEquals("", s.scoreA); assertEquals("", s.scoreB)
    }
    @Test fun `team names with digits in scores do not bleed`() {
        val s = CricinfoLive.parseTitle("Hong Kong 107/4 v Australia 135/4", "l")!!
        assertEquals("Hong Kong", s.teamA); assertEquals("107/4", s.scoreA); assertEquals("135/4", s.scoreB)
    }
    @Test fun `followed keeps India and Mumbai Indians sides only, whole words`() {
        val all = listOf("India 187/3 * v England 180/7", "India Women 120/2 * v Australia Women", "West Indies 200/5 v Indiana 100/1", "Mumbai Indians 190/4 v Chennai Super Kings 150/8").map { CricinfoLive.parseTitle(it, "l")!! }
        val teams = listOf("India", "India Women", "Mumbai Indians", "MI")
        assertEquals(3, CricinfoLive.followed(all, teams).size)
    }
    @Test fun `india A and under-19 sides are not followed`() {
        val all = listOf("New Zealand A v India A", "India Under-19s 120/3 * v England Under-19s", "India U19 Women v Sri Lanka U19 Women", "India 88/5 * v Pakistan 91/2").map { CricinfoLive.parseTitle(it, "l")!! }
        assertEquals(listOf("India"), CricinfoLive.followed(all, listOf("India", "India Women")).map { it.teamA })
    }
    @Test fun `an all-out or completed innings is a bare number`() {
        val s = CricinfoLive.parseTitle("India v United Arab Emirates 97", "l")!!
        assertEquals("United Arab Emirates", s.teamB); assertEquals("97", s.scoreB); assertEquals("", s.scoreA)
        val t = CricinfoLive.parseTitle("England 124/2 * v India 121", "l")!!
        assertEquals("India", t.teamB); assertEquals("121", t.scoreB); assertEquals("124/2", t.scoreA); assertTrue(t.battingA)
    }
    @Test fun `garbage returns null`() { assertNull(CricinfoLive.parseTitle("Latest scores from Cricinfo", "l")) }
}
