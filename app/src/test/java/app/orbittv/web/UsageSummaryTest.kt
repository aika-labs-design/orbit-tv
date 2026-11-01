package app.orbittv.web

import app.orbittv.usage.UsageEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class UsageSummaryTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val now = 1793392200000L // 2026-10-30 20:30 UTC, a Friday

    @Test fun `minutes this week come from foreground to background pairs`() {
        val ev = listOf(
            UsageEvent("a", now - 3_600_000L, foreground = true), UsageEvent("a", now - 3_000_000L, foreground = false), // 10 min
            UsageEvent("b", now - 8 * 86_400_000L, foreground = true), UsageEvent("b", now - 8 * 86_400_000L + 600_000L, foreground = false), // last week, excluded
        )
        val s = UsageSummary.build(listOf("a", "b"), ev, now, utc)
        assertEquals(10, s.getValue("a").minutesWeek); assertEquals(0, s.getValue("b").minutesWeek)
    }
    @Test fun `hour histogram counts foreground starts per hour of day`() {
        val ev = listOf(UsageEvent("a", now, foreground = true), UsageEvent("a", now - 86_400_000L, foreground = true), UsageEvent("a", now - 6 * 3_600_000L, foreground = true))
        val h = UsageSummary.build(listOf("a"), ev, now, utc).getValue("a").hours
        assertEquals(2, h[20]); assertEquals(1, h[14]); assertEquals(24, h.size)
    }
    @Test fun `weekday histogram counts foreground starts per day of week`() {
        // now is Friday 20:30 UTC; yesterday Thursday; Friday last week
        val ev = listOf(UsageEvent("a", now, foreground = true), UsageEvent("a", now - 86_400_000L, foreground = true), UsageEvent("a", now - 7 * 86_400_000L, foreground = true))
        val d = UsageSummary.build(listOf("a"), ev, now, utc).getValue("a").days
        assertEquals(7, d.size); assertEquals(2, d[5]); assertEquals(1, d[4]) // Calendar.FRIDAY = 6 -> index 5
    }
    @Test fun `streak counts consecutive days ending today or yesterday`() {
        val day = 86_400_000L
        val three = listOf(UsageEvent("a", now, foreground = true), UsageEvent("a", now - day, foreground = true), UsageEvent("a", now - 2 * day, foreground = true), UsageEvent("a", now - 5 * day, foreground = true))
        assertEquals(3, UsageSummary.build(listOf("a"), three, now, utc).getValue("a").streak)
        val stale = listOf(UsageEvent("a", now - 3 * day, foreground = true), UsageEvent("a", now - 4 * day, foreground = true))
        assertEquals(0, UsageSummary.build(listOf("a"), stale, now, utc).getValue("a").streak)
        val yesterdayOnly = listOf(UsageEvent("a", now - day, foreground = true), UsageEvent("a", now - 2 * day, foreground = true))
        assertEquals(2, UsageSummary.build(listOf("a"), yesterdayOnly, now, utc).getValue("a").streak)
    }
    @Test fun `minutes today count only sessions that ended today`() {
        val ev = listOf(
            UsageEvent("a", now - 2 * 3_600_000L, foreground = true), UsageEvent("a", now - 1 * 3_600_000L, foreground = false),            // 60 min today (Friday)
            UsageEvent("a", now - 30 * 3_600_000L, foreground = true), UsageEvent("a", now - 29 * 3_600_000L, foreground = false),          // yesterday
        )
        val s = UsageSummary.build(listOf("a"), ev, now, utc).getValue("a")
        assertEquals(60, s.minutesToday); assertEquals(120, s.minutesWeek)
    }
    @Test fun `last used is the newest foreground event`() {
        val ev = listOf(UsageEvent("a", 5, foreground = true), UsageEvent("a", 9, foreground = true), UsageEvent("a", 12, foreground = false))
        assertEquals(9L, UsageSummary.build(listOf("a"), ev, now, utc).getValue("a").lastUsedMs)
    }

    @Test fun `usage facts are rebuilt on return after ten minutes`() {
        assertEquals(false, UsageSummary.refreshDue(builtAtMs = now - 9 * 60_000L, nowMs = now))
        assertEquals(true, UsageSummary.refreshDue(builtAtMs = now - 11 * 60_000L, nowMs = now))
        assertEquals(true, UsageSummary.refreshDue(builtAtMs = 0L, nowMs = now))
    }
}
