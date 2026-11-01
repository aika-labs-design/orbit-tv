package app.orbittv.usage

import java.util.Calendar
import java.util.TimeZone

data class UsageEvent(val packageName: String, val timestampMs: Long, val foreground: Boolean = true)

/** Orders apps by how often they are used at this hour of day, then overall, then by the given order. */
object UsageRanker {
    fun order(apps: List<String>, events: List<UsageEvent>, nowMs: Long, zone: TimeZone): List<String> {
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = nowMs
        val hourNow = cal.get(Calendar.HOUR_OF_DAY)
        val sameHour = HashMap<String, Int>()
        val total = HashMap<String, Int>()
        for (e in events) {
            if (!e.foreground) continue
            total[e.packageName] = (total[e.packageName] ?: 0) + 1
            cal.timeInMillis = e.timestampMs
            if (cal.get(Calendar.HOUR_OF_DAY) == hourNow) sameHour[e.packageName] = (sameHour[e.packageName] ?: 0) + 1
        }
        return apps.withIndex().sortedWith(
            compareByDescending<IndexedValue<String>> { sameHour[it.value] ?: 0 }
                .thenByDescending { total[it.value] ?: 0 }
                .thenBy { it.index },
        ).map { it.value }
    }

    fun lastUsed(events: List<UsageEvent>): String? = events.filter { it.foreground }.maxByOrNull { it.timestampMs }?.packageName
}
