package app.orbittv.web

/**
 * Journey answers for the hosted page. Each request is tagged with the page [generation] it was asked from; an answer for another
 * generation (an earlier page) is dropped, whenever it arrives. Answers for the current page that arrive before it says it is ready
 * wait here and are run when it is (at most [max] wait).
 */
class ReadyQueue(private val max: Int = 50) {
    private val waiting = ArrayList<String>()
    private var ready = false
    /** The current page; a request records it and its answer brings it back to [offer]. */
    @Volatile var generation = 0L; private set

    /** The script to run now, or null when it waits for the page or belongs to an earlier page. */
    @Synchronized fun offer(js: String, gen: Long): String? {
        if (gen != generation) return null
        if (ready) return js
        if (waiting.size < max) waiting += js
        return null
    }
    /** The page is ready: the scripts that waited, in order. */
    @Synchronized fun ready(): List<String> { ready = true; return waiting.toList().also { waiting.clear() } }
    /** Another page is loading: nothing waits and earlier requests' answers no longer count. */
    @Synchronized fun newPage() { generation++; ready = false; waiting.clear() }
}
