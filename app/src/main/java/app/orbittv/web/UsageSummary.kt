package app.orbittv.web

import app.orbittv.usage.UsageEvent
import java.util.Calendar
import java.util.TimeZone

/**
 * Per-app usage facts the page personalises with.
 * [hours]: foreground starts per hour of day. [days]: per day of week, index 0 = Sunday.
 * [streak]: consecutive days with a start, ending today or yesterday.
 */
data class AppUsage(val minutesWeek: Int, val minutesToday: Int, val lastUsedMs: Long, val hours: IntArray, val days: IntArray, val streak: Int)

object UsageSummary {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val WEEK_MS = 7 * DAY_MS
    private const val MAX_SESSION_MS = 4L * 60 * 60 * 1000

    /** Usage facts and ring order are rebuilt when Orbit comes back to the front this long after they were built. */
    fun refreshDue(builtAtMs: Long, nowMs: Long): Boolean = nowMs - builtAtMs > 10 * 60_000L

    fun build(apps: List<String>, events: List<UsageEvent>, nowMs: Long, zone: TimeZone): Map<String, AppUsage> {
        val cal = Calendar.getInstance(zone)
        val sorted = events.sortedBy { it.timestampMs }
        val today = dayIndex(cal, nowMs)
        return apps.associateWith { pkg ->
            val mine = sorted.filter { it.packageName == pkg }
            var minutes = 0L; var minutesToday = 0L; var openAt = -1L; var lastUsed = 0L
            val hours = IntArray(24); val days = IntArray(7); val usedDays = HashSet<Long>()
            for (e in mine) {
                if (e.foreground) {
                    openAt = e.timestampMs; lastUsed = maxOf(lastUsed, e.timestampMs)
                    cal.timeInMillis = e.timestampMs
                    hours[cal.get(Calendar.HOUR_OF_DAY)]++
                    days[cal.get(Calendar.DAY_OF_WEEK) - 1]++
                    usedDays.add(dayIndex(cal, e.timestampMs))
                } else if (openAt >= 0) {
                    val session = minOf(e.timestampMs - openAt, MAX_SESSION_MS) / 60_000
                    if (e.timestampMs > nowMs - WEEK_MS) minutes += session
                    if (dayIndex(cal, e.timestampMs) == today) minutesToday += session
                    openAt = -1
                }
            }
            var streak = 0
            var d = if (usedDays.contains(today)) today else today - 1
            while (usedDays.contains(d)) { streak++; d-- }
            AppUsage(minutes.toInt(), minutesToday.toInt(), lastUsed, hours, days, streak)
        }
    }

    /** Whole days since the epoch in the given zone, so consecutive calendar days differ by one. */
    private fun dayIndex(cal: Calendar, ms: Long): Long {
        cal.timeInMillis = ms
        return (ms + cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / DAY_MS
    }
}
