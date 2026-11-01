package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

/** Per-title extras looked up once and cached: title logo path, trailer keys in fallback order, providers by country. */
data class Detail(val logo: String?, val trailers: List<String>, val providers: Map<String, List<String>>, val runtime: Int? = null, val backdrop: String? = null,
                  /** Logos by language, "" for a language-neutral one. */ val logos: Map<String, String> = emptyMap())

/** TMDB provider names mapped to Fire TV apps: apps offered across countries plus extras per country. Data in assets/providers.json. */
class ProviderApps(private val global: Map<String, String>, private val byCountry: Map<String, Map<String, String>>) {
    /** Provider name to package for one living country. A provider missing here shows its name without an app button. */
    fun forCountry(country: String): Map<String, String> = global + (byCountry[country] ?: emptyMap())

    companion object {
        val NONE = ProviderApps(emptyMap(), emptyMap())
        fun parse(json: String): ProviderApps {
            val o = JSONObject(json)
            fun map(m: JSONObject) = m.keys().asSequence().associateWith { m.getString(it) }
            return ProviderApps(o.optJSONObject("global")?.let(::map) ?: emptyMap(),
                o.keys().asSequence().filter { it != "global" }.mapNotNull { k -> o.optJSONObject(k)?.let { k to map(it) } }.toMap())
        }
    }
}

/** The Cinema catalogue for one profile: TMDB titles with full image URLs, labelled with the apps on this TV that carry them where the profile lives. */
object Catalog {
    fun key(t: TmdbTitle) = "${t.kind}:${t.id}"

    private val GENRES = mapOf(28 to "Action", 12 to "Adventure", 18 to "Drama", 53 to "Thriller", 35 to "Comedy", 10749 to "Romance", 80 to "Crime", 27 to "Horror",
        14 to "Fantasy", 36 to "History", 10751 to "Family", 878 to "Science Fiction", 9648 to "Mystery", 99 to "Documentary", 10402 to "Music", 10752 to "War", 37 to "Western", 16 to "Animation",
        10759 to "Action", 10765 to "Fantasy", 10768 to "War", 10764 to "Reality", 10766 to "Drama", 10767 to "Talk")
    /** The title in its own script (ಕಾಂತಾರ, అమృతం) when TMDB's original title is not in Latin letters. */
    fun localTitle(original: String): String? = original.takeIf { s -> s.isNotBlank() && s.any { it.isLetter() && it.code > 0x24F } }

    /**
     * [providerPkgs] is [ProviderApps.forCountry] for [country]; providers come from that country only. [display] is the person's main
     * language: when it is not English, the logo is the one in that language or a language-neutral one; the English one only for a
     * title in another script (its own-script line shows beside it), else the title shows as text in that language.
     */
    fun build(titles: List<TmdbTitle>, details: Map<String, Detail>, country: String, providerPkgs: Map<String, String>, installed: Set<String>, display: String = "en"): JSONArray = JSONArray().also { out ->
        titles.distinctBy { key(it) }.sortedByDescending { it.popularity }.forEach { t ->
            val d = details[key(t)]
            val native = localTitle(t.originalTitle) != null
            // not English: the logo in the main language or a neutral one; an English logo only for a title whose own script shows beside it
            val shownInEnglish = t.english || (t.lang == "en" && t.title == t.originalTitle)   // an English title keeps its English logo
            val logo = if (display == "en" || shownInEnglish) d?.logo else d?.logos?.get(display) ?: d?.logos?.get("") ?: d?.logo?.takeIf { native }
            val providers = d?.providers?.get(country) ?: emptyList()
            val apps = providers.mapNotNull { name -> providerPkgs[name]?.takeIf { it in installed }?.let { name to it } }.distinctBy { it.second }
            out.put(JSONObject().put("id", t.id).put("kind", t.kind).put("title", t.title).put("year", t.year).put("lang", t.lang).put("overview", t.overview.take(280))
                .put("genres", JSONArray(t.genres)).put("genreNames", JSONArray(t.genres.mapNotNull { GENRES[it] }.distinct())).put("popularity", t.popularity).put("rating", t.rating)
                .put("localTitle", localTitle(t.originalTitle) ?: JSONObject.NULL).put("runtime", d?.runtime ?: JSONObject.NULL)
                .put("poster", t.poster?.let { Tmdb.image(it, "w342") } ?: JSONObject.NULL)
                .put("backdrop", (d?.backdrop ?: t.backdrop)?.let { Tmdb.image(it, "w1280") } ?: JSONObject.NULL)   // text-free art first
                .put("logo", logo?.let { Tmdb.image(it, "w500") } ?: JSONObject.NULL)
                .put("trailers", JSONArray(d?.trailers ?: emptyList<String>()))
                .put("providers", JSONArray(providers))
                .put("apps", JSONArray().also { a -> apps.forEach { (n, p) -> a.put(JSONObject().put("name", n).put("pkg", p)) } }))
        }
    }
}
