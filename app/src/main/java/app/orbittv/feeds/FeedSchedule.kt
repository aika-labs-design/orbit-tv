package app.orbittv.feeds

/** When to fetch: sport every 2 min while something followed is live, else 15 min; news every 30 min; nothing 23:00 to 06:59 unless forced (wake). */
object FeedSchedule {
    private const val MIN = 60_000L
    fun quiet(localHour: Int) = localHour >= 23 || localHour < 7
    fun liveDue(lastMs: Long, nowMs: Long, anyLive: Boolean, localHour: Int, forced: Boolean): Boolean {
        if (forced) return true
        if (quiet(localHour)) return false
        return nowMs - lastMs > (if (anyLive) 2 else 15) * MIN
    }
    fun newsDue(lastMs: Long, nowMs: Long, localHour: Int, forced: Boolean): Boolean {
        if (forced) return true
        if (quiet(localHour)) return false
        return nowMs - lastMs > 30 * MIN
    }
    fun channelsDue(lastMs: Long, nowMs: Long, localHour: Int, forced: Boolean): Boolean {
        if (forced) return true
        if (quiet(localHour)) return false
        return nowMs - lastMs > 30 * MIN
    }
    /** Once a day outside quiet hours; the very first fetch after install runs whenever the service starts. */
    fun tonightDue(lastMs: Long, nowMs: Long, localHour: Int, cached: Boolean): Boolean = (lastMs == 0L && !cached) || (!quiet(localHour) && nowMs - lastMs > 24 * 60 * MIN)
    fun stationsDue(lastMs: Long, nowMs: Long, localHour: Int): Boolean = lastMs == 0L || (!quiet(localHour) && nowMs - lastMs > 24 * 60 * MIN)
    fun catalogDue(lastMs: Long, nowMs: Long, localHour: Int): Boolean = lastMs == 0L || (!quiet(localHour) && nowMs - lastMs > 24 * 60 * MIN)
    /** The time to record after a catalogue refresh: a failure is recorded 23 h back so it is tried again in about an hour. */
    /**
     * The country the TV is seen from (ipwho.is) is looked up on a forced pass (wake, start, profiles saved) or a network change.
     * A failed lookup ([failedAtMs], null after a success) is tried again no sooner than 30 minutes later.
     */
    fun countryDue(forced: Boolean, networkChanged: Boolean, failedAtMs: Long?, nowMs: Long): Boolean =
        forced || networkChanged || (failedAtMs != null && nowMs - failedAtMs >= 30 * MIN)
    fun catalogMark(ok: Boolean, nowMs: Long): Long = if (ok) nowMs else nowMs - 23 * 60 * MIN
    fun nextCheckDelayMs(anyLive: Boolean, localHour: Int): Long = if (quiet(localHour)) 20 * MIN else MIN
}
