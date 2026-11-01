package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

data class TmdbTitle(
    val id: Int, val kind: String, val title: String, val year: String, val lang: String, val overview: String,
    val genres: List<Int>, val popularity: Double, val rating: Double, val poster: String?, val backdrop: String?,
    val originalTitle: String = "",
    /** The title shown is TMDB's English one (no translation in the person's main language). */
    val english: Boolean = false,
)

/** The Movie Database (free personal key): titles by original language, title logos, trailer keys and watch providers per country. */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3"

    // Films need a few votes to filter out stubs; regional series rarely get many, so the bar is lower.
    // [display]: the person's main language for titles and overviews (TMDB answers in English without it).
    fun discoverUrl(key: String, kind: String, lang: String, page: Int, display: String? = null): String =
        "$API/discover/$kind?api_key=$key&with_original_language=$lang&sort_by=popularity.desc&vote_count.gte=${if (kind == "tv") 3 else 20}&page=$page" +
            (if (display != null && display != "en") "&language=$display" else "")

    /** Titles that carry any of these TMDB keywords (or, with no keywords, any of these genres), most popular first. */
    fun discoverByUrl(key: String, kind: String, keywords: List<Int>, genres: List<Int>, langs: List<String>): String =
        "$API/discover/$kind?api_key=$key&" + (if (keywords.isNotEmpty()) "with_keywords=${keywords.joinToString("%7C")}" else "with_genres=${genres.joinToString("%7C")}") +
            "&with_original_language=${langs.joinToString("%7C")}&sort_by=popularity.desc&vote_count.gte=${if (kind == "tv") 3 else 20}"

    fun keywordSearchUrl(key: String, term: String): String = "$API/search/keyword?api_key=$key&query=${java.net.URLEncoder.encode(term, "UTF-8")}"

    /** The first few keyword ids of a keyword search, the best match first. */
    fun parseKeywordIds(json: String, max: Int = 3): List<Int> = runCatching {
        val a = JSONObject(json).getJSONArray("results"); (0 until minOf(a.length(), max)).map { a.getJSONObject(it).getInt("id") }
    }.getOrDefault(emptyList())

    /** Everything per title in one request: logos, videos, providers and running time. */
    /** [displays]: the household's main languages, whose logos are kept too. */
    fun detailAllUrl(key: String, kind: String, id: Int, lang: String, displays: List<String> = emptyList()): String =
        (listOf("en", "null", lang) + displays).distinct().joinToString(",").let { langs ->   // logos and trailers in these languages (TMDB lists English only otherwise)
            "$API/$kind/$id?api_key=$key&append_to_response=images,videos,watch/providers&include_image_language=$langs&include_video_language=$langs" }

    /**
     * Null when the answer could not be read; a readable answer is trusted as it is (an empty provider list means no longer streaming).
     * Providers are kept only for [countries] (the living countries), each one present even when empty, so a cached entry shows which countries were asked.
     */
    fun parseDetail(json: String, lang: String, countries: Collection<String>): Detail? = runCatching {
        val o = JSONObject(json)
        val runtime = if (o.has("runtime") && !o.isNull("runtime")) o.optInt("runtime").takeIf { it > 0 }
            else o.optJSONArray("episode_run_time")?.let { a -> if (a.length() > 0) a.optInt(0).takeIf { it > 0 } else null }
        Detail(o.optJSONObject("images")?.let { parseLogo(it.toString(), lang) }, o.optJSONObject("videos")?.let { parseTrailers(it.toString()) } ?: emptyList(),
            o.optJSONObject("watch/providers")?.toString().let { wp -> countries.associateWith { c -> wp?.let { parseProviders(it, c) } ?: emptyList() } }, runtime, o.optJSONObject("images")?.let { cleanBackdrop(it) },
            o.optJSONObject("images")?.let { logos(it) } ?: emptyMap())
    }.getOrNull()

    /** The best-rated backdrop with no language (no text burnt in), like the art a premium TV home uses. */
    private fun cleanBackdrop(images: JSONObject): String? {
        val a = images.optJSONArray("backdrops") ?: return null
        return (0 until a.length()).map { a.getJSONObject(it) }.filter { it.isNull("iso_639_1") }.maxByOrNull { it.optDouble("vote_average", 0.0) }?.optString("file_path")
    }

    fun image(path: String, size: String): String = "https://image.tmdb.org/t/p/$size$path"

    fun parseDiscover(json: String, kind: String): List<TmdbTitle> = runCatching {
        val a = JSONObject(json).getJSONArray("results")
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val date = o.optString(if (kind == "tv") "first_air_date" else "release_date")
            TmdbTitle(o.getInt("id"), kind, o.optString(if (kind == "tv") "name" else "title"), date.take(4).takeIf { it.length == 4 } ?: "",
                o.optString("original_language"), o.optString("overview"), ints(o.optJSONArray("genre_ids")),
                o.optDouble("popularity", 0.0), o.optDouble("vote_average", 0.0), str(o, "poster_path"), str(o, "backdrop_path"),
                o.optString(if (kind == "tv") "original_name" else "original_title"))
        }.filter { it.title.isNotBlank() }
    }.getOrDefault(emptyList())

    /** The first logo per language ("" for a language-neutral one). */
    private fun logos(images: JSONObject): Map<String, String> {
        val a = images.optJSONArray("logos") ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        (0 until a.length()).map { a.getJSONObject(it) }.forEach { l -> out.putIfAbsent(if (l.isNull("iso_639_1")) "" else l.optString("iso_639_1"), l.optString("file_path")) }
        return out
    }

    /** English logo first, then one in the title's language, then a language-neutral one. */
    fun parseLogo(json: String, lang: String): String? = runCatching {
        val a = JSONObject(json).optJSONArray("logos") ?: return null
        val logos = (0 until a.length()).map { a.getJSONObject(it) }
        val by = { code: String? -> logos.firstOrNull { (if (it.isNull("iso_639_1")) null else it.optString("iso_639_1")) == code }?.optString("file_path") }
        by("en") ?: by(lang) ?: by(null)
    }.getOrNull()

    /** Every YouTube trailer in order, then teasers: the player falls back to the next key when YouTube blocks one. */
    fun parseTrailers(json: String): List<String> = runCatching {
        val a = JSONObject(json).getJSONArray("results")
        val yt = (0 until a.length()).map { a.getJSONObject(it) }.filter { it.optString("site") == "YouTube" }
        yt.filter { it.optString("type") == "Trailer" }.map { it.getString("key") } + yt.filter { it.optString("type") == "Teaser" }.map { it.getString("key") }
    }.getOrDefault(emptyList())

    /** Providers that need no purchase: subscription, free and ad-supported tiers. */
    fun parseProviders(json: String, country: String): List<String> = runCatching {
        val c = JSONObject(json).getJSONObject("results").optJSONObject(country) ?: return emptyList()
        listOf("flatrate", "free", "ads").flatMap { k -> c.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("provider_name") } } ?: emptyList() }.distinct()
    }.getOrDefault(emptyList())

    private fun ints(a: JSONArray?): List<Int> = if (a == null) emptyList() else List(a.length()) { a.getInt(it) }
    private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.optString(k).ifBlank { null }
}
