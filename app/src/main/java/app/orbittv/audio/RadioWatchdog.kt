package app.orbittv.audio

/**
 * Decides when a radio stream has failed: no answer within the timeout, an error or end of stream, or buffering
 * for longer than the timeout after it started. Reports each failure once.
 */
class RadioWatchdog(val timeoutMs: Long = 10_000) {
    private var requestedAt = -1L; private var prepared = false; private var errored = false; private var stalledAt = -1L
    fun onPlayRequested(nowMs: Long) { requestedAt = nowMs; prepared = false; errored = false; stalledAt = -1L }
    fun onPrepared() { prepared = true }
    fun onError() { errored = true }
    fun onStall(nowMs: Long) { if (stalledAt < 0) stalledAt = nowMs }
    fun onRecovered() { stalledAt = -1L }
    fun check(nowMs: Long): String? {
        if (requestedAt < 0) return null
        val stalled = stalledAt >= 0 && nowMs - stalledAt > timeoutMs
        if (errored || stalled || (!prepared && nowMs - requestedAt > timeoutMs)) { requestedAt = -1; return "failed" }
        return null
    }
}
