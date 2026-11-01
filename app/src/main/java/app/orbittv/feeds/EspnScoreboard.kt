package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

enum class EventState { PRE, IN, POST }
data class Competitor(val name: String, val abbr: String?, val score: String?, val winner: Boolean)
data class SportEvent(val id: String, val name: String, val startMs: Long, val state: EventState, val detail: String, val competitors: List<Competitor>, val venue: String?, val round: String?)

/** ESPN's public site scoreboard JSON. Cricket uses events[].competitions[0], F1 one entry per session, tennis flattens groupings. */
object EspnScoreboard {
    fun events(json: String): List<SportEvent> = safely(json) { root ->
        root.optJSONArray("events").objects().mapNotNull { e ->
            try {
                val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
                val sides = competitors(c)
                if (sides.any { unknown(it.name) }) return@mapNotNull null   // "TBC v TBC": nothing anyone can follow yet
                SportEvent(e.optString("id"), e.optString("name"), isoMs(e.optString("date")), state(c, e), detail(c, e), competitors(c), c.optJSONObject("venue")?.optString("fullName"), null)
            } catch (ex: Exception) { null }
        }
    }

    fun f1Sessions(json: String): List<SportEvent> = safely(json) { root ->
        root.optJSONArray("events").objects().flatMap { e ->
            val gp = e.optString("name"); val circuit = e.optJSONObject("circuit")?.optString("fullName")
            e.optJSONArray("competitions").objects().mapNotNull { c ->
                try { SportEvent(c.optString("id"), "$gp · ${c.optJSONObject("type")?.optString("abbreviation") ?: "Session"}", isoMs(c.optString("date")), state(c, e), detail(c, e), competitors(c), circuit, null) } catch (ex: Exception) { null }
            }
        }
    }

    fun tennisMatches(json: String): List<SportEvent> = safely(json) { root ->
        root.optJSONArray("events").objects().flatMap { e ->
            val tournament = e.optString("name")
            e.optJSONArray("groupings").objects().flatMap { g ->
                val group = g.optJSONObject("grouping")?.optString("displayName") ?: ""
                g.optJSONArray("competitions").objects().mapNotNull { c ->
                    try {
                        val short = c.optJSONObject("status")?.optJSONObject("type")?.optString("shortDetail") ?: ""
                        val note = c.optJSONArray("notes")?.optJSONObject(0)?.let { it.optString("headline").ifBlank { it.optString("text") } }
                        val round = if (short.isNotBlank() && !short.contains(Regex("\\d")) && short != "Scheduled") short else note
                        val comps = c.optJSONArray("competitors").objects().map { x ->
                            val sets = x.optJSONArray("linescores").objects().joinToString(" ") { ls -> ls.optDouble("value", 0.0).toInt().toString() }
                            val who = x.optJSONObject("athlete")?.let { a -> a.optString("displayName").ifBlank { a.optString("shortName") } } ?: x.optJSONObject("team")?.optString("displayName") ?: ""
                            Competitor(who, null, sets.ifBlank { null }, x.optBoolean("winner", false))
                        }
                        if (comps.size < 2 || comps.any { unknown(it.name) }) return@mapNotNull null   // "TBD v TBD": not a match anyone can follow yet
                        SportEvent(c.optString("id"), "$tournament · $group", isoMs(c.optString("date")), state(c, e), detail(c, e), comps, c.optJSONObject("venue")?.optString("fullName"), round)
                    } catch (ex: Exception) { null }
                }
            }
        }
    }

    private val PLACEHOLDER = Regex("^(TBD|TBA|TBC)$", RegexOption.IGNORE_CASE)
    private fun unknown(name: String) = name.isBlank() || PLACEHOLDER.matches(name.trim())

    /** A tennis score as sets, "6-3 4-6 2-1", from two players' games per set ([tennisMatches] keeps them space-separated). */
    fun setScore(players: List<Competitor>): String {
        if (players.size != 2) return ""
        val (a, b) = players.map { (it.score ?: "").split(' ').filter { s -> s.isNotBlank() } }
        return a.zip(b).joinToString(" ") { (x, y) -> "$x-$y" }
    }

    private fun safely(json: String, f: (JSONObject) -> List<SportEvent>): List<SportEvent> = try { f(JSONObject(json)) } catch (e: Exception) { emptyList() }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun statusType(c: JSONObject, e: JSONObject): JSONObject? = (c.optJSONObject("status") ?: e.optJSONObject("status"))?.optJSONObject("type")
    private fun state(c: JSONObject, e: JSONObject): EventState = when (statusType(c, e)?.optString("state")) { "in" -> EventState.IN; "post" -> EventState.POST; else -> EventState.PRE }
    private fun detail(c: JSONObject, e: JSONObject): String = statusType(c, e)?.optString("shortDetail") ?: ""
    private fun competitors(c: JSONObject): List<Competitor> = c.optJSONArray("competitors").objects().map { x ->
        val team = x.optJSONObject("team")
        Competitor(team?.optString("displayName") ?: x.optJSONObject("athlete")?.optString("displayName") ?: "", team?.optString("abbreviation"), x.optString("score").ifBlank { null }, x.optString("winner") == "true" || x.optBoolean("winner", false))
    }

    private val ISO = listOf("yyyy-MM-dd'T'HH:mm'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'").map { SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") } }
    fun isoMs(s: String): Long { for (f in ISO) try { return f.parse(s)!!.time } catch (e: Exception) { }; return 0L }
}
