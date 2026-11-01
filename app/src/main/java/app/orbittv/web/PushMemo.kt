package app.orbittv.web

/**
 * What was last pushed to the page, per kind (length and hash, not the body), so an unchanged body is not parsed and redrawn again.
 * Every feeds update re-sent the unchanged catalogue, and each push makes Cinema rebuild. Cleared when a page loads.
 */
class PushMemo {
    private val last = HashMap<String, Pair<Int, Int>>()
    fun fresh(kind: String, body: String): Boolean = (body.length to body.hashCode()).let { k -> if (last[kind] == k) false else { last[kind] = k; true } }
    /** The feeds page is rewritten with a new top-level fetchedMs (its last key) on every refresh; that stamp alone is not a change. */
    fun freshFeeds(body: String): Boolean = fresh("feeds", body.replace(STAMP, "}"))
    fun clear() = last.clear()
    private companion object { val STAMP = Regex(",\"fetchedMs\":\\d+\\}$") }
}
