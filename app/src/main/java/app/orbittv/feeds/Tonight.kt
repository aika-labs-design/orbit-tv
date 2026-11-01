package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class TopTitle(val rank: Int, val title: String, val kind: String, val week: String)
data class Poster(val image: String?, val blurb: String?)

/**
 * Tonight on Netflix: the public Top 10 data for the living country (netflix.com/tudum/top10), with posters and blurbs looked up
 * on TVmaze (series) and Wikipedia (films). All free and keyless.
 */
object Tonight {
    const val TOP10_URL = "https://www.netflix.com/tudum/top10/data/all-weeks-countries.tsv"
    const val NETFLIX = "com.netflix.ninja"
    private const val BLURB_MAX = 160

    /** Latest week for the country (Netflix's own country name), films then series, rank order. Works whatever the row order of the file. */
    fun parseTop10(tsvLines: Sequence<String>, country: String): List<TopTitle> {
        val rows = ArrayList<TopTitle>()
        for (line in tsvLines) {
            val f = line.split('\t'); if (f.size < 6 || f[0] != country) continue
            val kind = if (f[3].equals("Films", true)) "film" else "tv"
            val rank = f[4].toIntOrNull() ?: continue
            rows += TopTitle(rank, f[5].trim(), kind, f[2])
        }
        val week = rows.maxOfOrNull { it.week } ?: return emptyList()
        return rows.filter { it.week == week }.sortedWith(compareBy({ if (it.kind == "film") 0 else 1 }, { it.rank }))
    }

    /** TVmaze /search/shows: only the show whose name is exactly the title and which Netflix carries; fuzzy hits are wrong art. */
    fun posterFromTvmazeSearch(json: String, title: String): Poster? = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("show") }.firstOrNull { show ->
            show.optString("name").equals(title, ignoreCase = true) &&
                (show.optJSONObject("webChannel")?.optString("name").equals("Netflix", true) || show.optJSONObject("network")?.optString("name").equals("Netflix", true))
        }?.let { show -> Poster(show.optJSONObject("image")?.optString("original")?.ifBlank { null }, stripHtml(show.optString("summary")).ifBlank { null }) }
    } catch (e: Exception) { null }

    /** Wikipedia thumbnails accept any width in the /NNNpx- segment; 1280 px is plenty for a TV backdrop and spares the WebView's memory. */
    fun wikiImage(json: String): String? = try {
        val o = JSONObject(json)
        val thumb = o.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
        thumb?.replace(Regex("/\\d+px-"), "/1280px-") ?: o.optJSONObject("originalimage")?.optString("source")?.ifBlank { null }
    } catch (e: Exception) { null }

    fun isFresh(week: String, nowMs: Long): Boolean = try { val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(week)!!.time; nowMs - d < 21L * 24 * 3600_000L } catch (e: Exception) { false }
    fun shouldRetry(checkedMs: Long, nowMs: Long): Boolean = checkedMs == 0L || nowMs - checkedMs > 3L * 24 * 3600_000L

    private val MEDIA = Regex("\\b(film|movie|television|TV|series|miniseries|documentary|Netflix|sitcom|anime|drama)\\b", RegexOption.IGNORE_CASE)

    /** A Wikipedia summary is accepted only when it reads like a film or series page, so "Animals" does not become the animal kingdom. */
    fun posterFromWiki(json: String): Poster? = try {
        val o = JSONObject(json)
        val extract = o.optString("extract")
        if (o.optString("type", "standard") != "standard" || o.has("status") && o.optInt("status") == 404 || !MEDIA.containsMatchIn(extract)) null
        else Poster(wikiImage(json), extract.ifBlank { null })
    } catch (e: Exception) { null }

    fun wikiTitleCandidates(title: String, kind: String, year: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)): List<String> =
        if (kind == "film") listOf("$title ($year film)", "$title (${year - 1} film)", "$title (film)", title) else listOf("$title (TV series)", title)
    fun wikiUrl(pageTitle: String) = "https://en.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(pageTitle.replace(' ', '_'), "UTF-8").replace("+", "_")
    fun tvmazeUrl(title: String) = "https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(title, "UTF-8")

    fun toJson(items: List<Pair<TopTitle, Poster?>>): JSONArray = JSONArray().also { arr ->
        items.forEach { (t, p) ->
            arr.put(JSONObject().put("rank", t.rank).put("title", t.title).put("kind", t.kind).put("week", t.week)
                .put("image", p?.image ?: JSONObject.NULL).put("blurb", p?.blurb?.take(BLURB_MAX) ?: JSONObject.NULL).put("pkg", NETFLIX))
        }
    }

    private fun stripHtml(s: String) = Rss.decodeEntities(s.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim()
}
