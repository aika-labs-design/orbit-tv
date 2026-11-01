package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

data class HomePlace(val name: String, val lat: Double, val lon: Double)
data class HomeQuery(val q: String, val lang: String)
data class Festival(val date: String, val name: String, val local: String)

/** One profile's home for Home Mode. `raw` goes to the page in the shape it read from the old homes.json (label, greetings, accent, festivals). */
data class HomePack(
    val id: String, val label: String, val langName: String, val channelTag: String, val radioLanguage: String, val tmdbLang: String,
    val country: Country?, val places: List<HomePlace>, val queries: List<HomeQuery>, val festivals: List<Festival>,
    val raw: JSONObject,
)

/** Home packs derived from a profile (home country, places, languages in order) and the bundled world data. */
object Homes {
    private val ACCENTS = listOf("#ff8f3f", "#2ec4b6", "#ffd166", "#c77dff", "#6cffb8", "#3df2ff")

    /**
     * [curatedChannels]: the home country has a channel file, whose tags name languages ("Kannada · News"); otherwise its channels come from a
     * live search tagged with the country's name. A language with no greeting gets none.
     */
    fun fromProfile(p: Profile, countries: List<Country>, languages: List<Language>, festivals: List<Festival> = emptyList(), curatedChannels: Boolean = false): HomePack {
        val country = countries.firstOrNull { it.code == p.home.country }
        val label = country?.name ?: p.home.country
        val main = p.languages.first()
        val lang = languages.firstOrNull { it.code == main }
        val langName = lang?.name ?: main
        val places = p.home.places.map { HomePlace(it.name, it.lat, it.lon) }
        val query = places.joinToString(" OR ") { it.name }
        val channelTag = if (curatedChannels) langName else label
        val g = lang?.greeting
        val raw = JSONObject().put("id", p.id).put("label", label).put("name", p.name).put("country", p.home.country).put("langName", langName).put("channelTag", channelTag)
            .put("accent", ACCENTS[Math.floorMod(p.id.hashCode(), ACCENTS.size)])
            .put("greetings", if (g == null) JSONObject() else JSONObject().put("morning", g.morning).put("day", g.afternoon).put("evening", g.evening).put("night", g.evening))
            .put("places", JSONArray().also { a -> places.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon)) } })
            .put("festivals", JSONArray().also { a -> festivals.forEach { a.put(JSONObject().put("date", it.date).put("name", it.name).put("local", it.local)) } })
        return HomePack(p.id, label, langName, channelTag, langName.lowercase(), main, country, places, World.newsLanguages(p.languages, country).take(2).map { HomeQuery(query, it) }, festivals, raw)
    }
}
