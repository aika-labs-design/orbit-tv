package app.orbittv.feeds

import org.json.JSONObject

/** The series a match belongs to and its format (T20, ODI, Test, Sixes, First-class), when known. */
data class Series(val name: String, val format: String?)

/** ESPN's cricket header feed lists every current match with its series and class, keyed by the same number Cricinfo links use. */
object CricketSeries {
    const val URL = "https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&lang=en&region=gb"

    fun parseHeader(json: String): List<Pair<String, Series>> = runCatching {
        val out = ArrayList<Pair<String, Series>>()
        val sports = JSONObject(json).getJSONArray("sports")
        for (i in 0 until sports.length()) {
            val leagues = sports.getJSONObject(i).optJSONArray("leagues") ?: continue
            for (j in 0 until leagues.length()) {
                val l = leagues.getJSONObject(j); val name = l.optString("name").ifBlank { l.optString("abbreviation") }
                val events = l.optJSONArray("events") ?: continue
                for (k in 0 until events.length()) {
                    val e = events.getJSONObject(k)
                    val cls = e.optJSONObject("class")?.optString("generalClassCard") ?: e.optString("description")
                    out += e.optString("id") to Series(name, format(cls, name))
                }
            }
        }
        out.filter { it.first.isNotBlank() && it.second.name.isNotBlank() }
    }.getOrDefault(emptyList())

    fun format(cls: String?, name: String): String? = when {
        name.contains("Sixes", true) -> "Sixes"
        cls == null -> null
        cls.contains("Twenty20", true) || cls.startsWith("T20", true) -> "T20"
        cls.equals("ODI", true) || cls.contains("One-Day International", true) -> "ODI"
        cls.equals("Test", true) -> "Test"
        cls.contains("First-class", true) -> "First-class"
        cls.contains("List A", true) || cls.contains("Other OD", true) -> "One-day"
        else -> null
    }

    /** Familiar short names: the Indian Premier League is "IPL"; ICC events lose the "ICC" and the year. */
    fun competition(name: String): String = when {
        name.contains("Indian Premier League", true) -> "IPL"
        else -> name.removePrefix("ICC ").replace(Regex("\\s+\\d{4}(/\\d{2})?$"), "").trim()
    }
}
