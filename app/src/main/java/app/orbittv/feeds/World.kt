package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class WorldCity(val name: String, val lat: Double, val lon: Double, val tz: String)
/** A Google News edition that exists (checked by tools/data/verify_news.py). */
data class NewsEdition(val hl: String, val gl: String, val ceid: String)
data class Country(
    val code: String, val name: String, val languages: List<String>, val timezones: List<String>,
    val cities: List<WorldCity>, val news: NewsEdition?, val netflixName: String?,
    /** The country's name in its own languages (Deutschland, Brasil), from CLDR. */
    val names: List<String> = emptyList(),
)
data class Greeting(val morning: String, val afternoon: String, val evening: String)
data class Language(val code: String, val name: String, val native: String, val film: String?, val greeting: Greeting?, val rtl: Boolean,
                    /** Common short words, enough to tell which language a headline is written in (empty where none are listed). */
                    val common: List<String> = emptyList())

/** Countries and languages from assets/countries.json and languages.json (built by tools/data/build_countries.py). */
object World {
    private fun <T> JSONArray.map(f: (JSONObject) -> T) = List(length()) { f(getJSONObject(it)) }
    private fun JSONArray.strings() = List(length()) { getString(it) }

    fun countries(json: String): List<Country> = JSONArray(json).map { o ->
        Country(
            o.getString("code"), o.getString("name"), o.getJSONArray("languages").strings(), o.getJSONArray("timezones").strings(),
            o.getJSONArray("cities").map { WorldCity(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.getString("tz")) },
            o.optJSONObject("news")?.let { NewsEdition(it.getString("hl"), it.getString("gl"), it.getString("ceid")) },
            if (o.isNull("netflixName")) null else o.getString("netflixName"),
            o.optJSONArray("names")?.strings() ?: emptyList(),
        )
    }

    fun languages(json: String): List<Language> = JSONArray(json).map { o ->
        Language(
            o.getString("code"), o.getString("name"), o.getString("native"), if (o.isNull("film")) null else o.getString("film"),
            o.optJSONObject("greeting")?.let { Greeting(it.getString("morning"), it.getString("afternoon"), it.getString("evening")) },
            o.getBoolean("rtl"), o.optJSONArray("common")?.strings() ?: emptyList(),
        )
    }

    /**
     * Google News search for the last day. A country with a verified edition uses it when [lang] is the edition's own language; for another
     * language of that country (Kannada in India) it asks that language's edition there (hl=kn&gl=IN&ceid=IN:kn). With no edition only the language is sent.
     */
    fun newsUrl(query: String, country: Country?, lang: String): String {
        val base = "https://news.google.com/rss/search?q=" + URLEncoder.encode("$query when:1d", "UTF-8")
        val e = country?.news ?: return "$base&hl=$lang"
        if (lang == e.ceid.substringAfter(':')) return "$base&hl=${e.hl}&gl=${e.gl}&ceid=${e.ceid}"
        return "$base&hl=$lang&gl=${e.gl}&ceid=${e.gl}:$lang"
    }

    /**
     * The person's languages that Google News and YouTube know (ISO 639-1), in order. Language codes may be ISO 639-3 where no 639-1
     * code exists (Nigerian Pidgin, pcm); those are skipped. With none left, the country edition's language, else English.
     */
    fun newsLanguages(langs: List<String>, country: Country?): List<String> =
        langs.filter { it.length == 2 }.distinct().ifEmpty { listOf(country?.news?.ceid?.substringAfter(':') ?: "en") }
}
