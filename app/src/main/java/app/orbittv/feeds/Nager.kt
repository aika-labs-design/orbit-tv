package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

/** Public holidays from Nager.Date (free, keyless), for festival days in Home Mode. */
object Nager {
    fun url(year: Int, country: String) = "https://date.nager.at/api/v3/PublicHolidays/$year/$country"

    /**
     * Nationwide holidays in date order, with the English and the local name. A country Nager does not cover answers 204 with no body,
     * which is an empty list; anything unreadable is null so the caller keeps what it had.
     */
    fun parse(json: String): List<Festival>? {
        if (json.isBlank()) return emptyList()
        val a = try { JSONArray(json) } catch (e: Exception) { return null }
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optBoolean("global", true) }
            .mapNotNull { o -> val d = o.optString("date"); val n = o.optString("name"); if (d.isBlank() || n.isBlank()) null else Festival(d, n, o.optString("localName").ifBlank { n }) }
            .distinct().sortedBy { it.date }
    }
}

/**
 * Bundled public holidays for a country Nager.Date does not cover (assets/festivals/<CC>.json): national days for everyone plus regional days
 * by language. A day two of the person's languages share appears once, named in the earlier (main) language.
 */
class FestivalBook(private val national: List<Festival>, private val byLanguage: Map<String, List<Festival>>) {
    fun forLanguages(langs: List<String>): List<Festival> =
        (langs.flatMap { byLanguage[it] ?: emptyList() } + national).distinctBy { it.date to it.name }.sortedBy { it.date }

    companion object {
        private fun list(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.getJSONObject(it) }
            .map { o -> Festival(o.getString("date"), o.getString("name"), o.optString("local").ifBlank { o.getString("name") }) }
        fun parse(json: String): FestivalBook {
            val o = JSONObject(json); val l = o.optJSONObject("languages") ?: JSONObject()
            return FestivalBook(list(o.optJSONArray("national")), l.keys().asSequence().associateWith { list(l.getJSONArray(it)) })
        }
    }
}
