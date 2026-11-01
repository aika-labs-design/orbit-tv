package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScheduleTest {
    private val m = 60_000L
    @Test fun `live is due after two minutes when something is live`() { assertTrue(FeedSchedule.liveDue(0L, 2 * m + 1, anyLive = true, localHour = 20, forced = false)) }
    @Test fun `live is not due after ten minutes when nothing is live`() { assertFalse(FeedSchedule.liveDue(0L, 10 * m, anyLive = false, localHour = 20, forced = false)) }
    @Test fun `live is due after fifteen minutes when nothing is live`() { assertTrue(FeedSchedule.liveDue(0L, 15 * m + 1, anyLive = false, localHour = 20, forced = false)) }
    @Test fun `quiet hours block both unless forced`() {
        assertFalse(FeedSchedule.liveDue(0L, 60 * m, anyLive = true, localHour = 23, forced = false))
        assertFalse(FeedSchedule.newsDue(0L, 60 * m, localHour = 3, forced = false))
        assertTrue(FeedSchedule.liveDue(0L, 60 * m, anyLive = true, localHour = 23, forced = true))
        assertTrue(FeedSchedule.newsDue(0L, 60 * m, localHour = 6, forced = true))
    }
    @Test fun `news is due after thirty minutes`() { assertFalse(FeedSchedule.newsDue(0L, 29 * m, localHour = 12, forced = false)); assertTrue(FeedSchedule.newsDue(0L, 30 * m + 1, localHour = 12, forced = false)) }
    @Test fun `check delay is a minute by day and twenty minutes in quiet hours`() { assertEquals(60_000L, FeedSchedule.nextCheckDelayMs(anyLive = false, localHour = 12)); assertEquals(1_200_000L, FeedSchedule.nextCheckDelayMs(anyLive = true, localHour = 2)) }
    @Test fun `stations refresh once a day outside quiet hours`() {
        val h = 3_600_000L
        assertTrue(FeedSchedule.stationsDue(0L, 5 * h, localHour = 2))
        assertFalse(FeedSchedule.stationsDue(10 * h, 12 * h, localHour = 10))
        assertTrue(FeedSchedule.stationsDue(10 * h, 35 * h, localHour = 10))
        assertFalse(FeedSchedule.stationsDue(10 * h, 35 * h, localHour = 2))
    }
    @Test fun `catalogue refreshes once a day outside quiet hours and on first run`() {
        val h = 3_600_000L
        assertTrue(FeedSchedule.catalogDue(0L, 5 * h, localHour = 2))
        assertFalse(FeedSchedule.catalogDue(10 * h, 20 * h, localHour = 12))
        assertTrue(FeedSchedule.catalogDue(10 * h, 35 * h, localHour = 12))
        assertFalse(FeedSchedule.catalogDue(10 * h, 35 * h, localHour = 23))
    }
    @Test fun `a failed catalogue refresh is retried in about an hour`() {
        val now = 100 * 3_600_000L
        assertEquals(now, FeedSchedule.catalogMark(ok = true, nowMs = now))
        val mark = FeedSchedule.catalogMark(ok = false, nowMs = now)
        assertFalse(FeedSchedule.catalogDue(mark, now + 30 * 60_000L, localHour = 12))
        assertTrue(FeedSchedule.catalogDue(mark, now + 61 * 60_000L, localHour = 12))
    }

    @Test fun `the country is looked up on wake or a network change, and a failure rests thirty minutes`() {
        val m = 60_000L; val t = 1_000_000_000L
        assertTrue(FeedSchedule.countryDue(forced = true, networkChanged = false, failedAtMs = null, nowMs = t))
        assertTrue(FeedSchedule.countryDue(forced = false, networkChanged = true, failedAtMs = null, nowMs = t))
        assertFalse(FeedSchedule.countryDue(forced = false, networkChanged = false, failedAtMs = null, nowMs = t))   // known: nothing to do
        assertFalse(FeedSchedule.countryDue(forced = false, networkChanged = false, failedAtMs = t - 1 * m, nowMs = t))
        assertFalse(FeedSchedule.countryDue(forced = false, networkChanged = false, failedAtMs = t - 29 * m, nowMs = t))
        assertTrue(FeedSchedule.countryDue(forced = false, networkChanged = false, failedAtMs = t - 30 * m, nowMs = t))
        assertTrue(FeedSchedule.countryDue(forced = true, networkChanged = false, failedAtMs = t - 1 * m, nowMs = t))   // a wake still asks
    }
}
