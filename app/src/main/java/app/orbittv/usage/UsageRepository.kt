package app.orbittv.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** Foreground and background events for the past 28 days from UsageStatsManager; empty when usage access is not granted. */
class UsageRepository(private val context: Context) {
    fun recentForegroundEvents(nowMs: Long = System.currentTimeMillis()): List<UsageEvent> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyList()
        val out = ArrayList<UsageEvent>()
        try {
            val events = usm.queryEvents(nowMs - WINDOW_MS, nowMs)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> out.add(UsageEvent(e.packageName, e.timeStamp, foreground = true))
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> out.add(UsageEvent(e.packageName, e.timeStamp, foreground = false))
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return out
    }

    companion object { const val WINDOW_MS = 28L * 24 * 60 * 60 * 1000 }
}
