package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.Normalizer

data class RefineOption(val id: String, val name: String, val terms: List<String>, val espnTeam: String? = null, val league: String? = null, val query: String? = null,
                        /** A Radio Browser tag of its own (Latin jazz), asked instead of the love's. */ val tag: String? = null)
data class Refine(val label: String, val options: List<RefineOption>)
data class Shows(val keywords: List<Int>, val genres: List<Int>)
data class News(val query: String)
data class Videos(val query: String, val minMinutes: Int)
/** [league] may name several ESPN leagues separated by commas ("soccer/eng.1,soccer/esp.1"). */
data class Live(val kind: String, val league: String? = null, val query: String? = null, val tag: String? = null,
                /** How the score reads: "sets" (6-3 4-6) or, when null, both sides' totals. */ val scoreStyle: String? = null)

/** One thing a person can love. [also] lists extra tabs it appears under; [tuned] is false for a generated recipe. */
data class Interest(
    val id: String, val name: String, val theme: String, val also: List<String>, val image: String, val terms: List<String>,
    val shows: Shows?, val news: News?, val videos: Videos?, val live: Live?, val refine: Refine?, val tuned: Boolean = true,
)

/** The bundled catalogue (assets/interests.json). Every love a person picks comes from here or from [custom]. */
object Interests {
    const val ASSET = "interests.json"
    val THEMES = listOf("popular", "food", "music", "sport", "ideas", "outdoors", "style", "faith")
    val LIVE_KINDS = setOf("espn", "cricinfo", "jolpica", "motogp", "ytlive", "radio")

    /** Broken entries are skipped, never fatal: an entry needs an id, a known theme, an image and at least one term. */
    fun parse(json: String): List<Interest> {
        val arr = try { JSONArray(json) } catch (e: Exception) { return emptyList() }
        val out = ArrayList<Interest>()
        for (i in 0 until arr.length()) {
            val one = try { arr.optJSONObject(i)?.let(::entry) } catch (e: Exception) { null }
            if (one != null && out.none { it.id == one.id }) out.add(one)
        }
        return out
    }

    /** Anything not in the catalogue: the phrase is the match term and the news and video query; shows come from a TMDB keyword search at refresh time. */
    fun custom(term: String): Interest {
        val words = term.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        val folded = Normalizer.normalize(words.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val slug = folded.replace(Regex("[^a-z0-9]+"), "-").trim('-')
        // Latin words keep a readable id; anything else (Kannada, Hindi, ...) also gets a stable hash so different terms never share an id
        val latin = folded.all { it in 'a'..'z' || it in '0'..'9' || it == ' ' || it == '-' }
        val hash = MessageDigest.getInstance("SHA-1").digest(Normalizer.normalize(words.lowercase(), Normalizer.Form.NFC).toByteArray()).take(5).joinToString("") { "%02x".format(it) }
        val id = if (latin && slug.isNotEmpty()) slug else (if (slug.isEmpty()) hash else "$slug-$hash")
        return Interest("custom:$id", words.replaceFirstChar { it.uppercase() }, "custom", emptyList(), "", listOf(words.lowercase()),
            null, News(words.lowercase()), Videos(words.lowercase(), 8), null, null, tuned = false)
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optString(it).trim().ifEmpty { null } }
    private fun ints(a: JSONArray?): List<Int> = if (a == null) emptyList() else (0 until a.length()).map { a.getInt(it) }
    private fun str(o: JSONObject, k: String): String? = if (o.has(k) && !o.isNull(k)) o.optString(k).ifBlank { null } else null

    private fun entry(o: JSONObject): Interest? {
        val id = str(o, "id") ?: return null
        val theme = str(o, "theme")?.takeIf { it in THEMES } ?: return null
        val image = str(o, "image") ?: return null
        val terms = strings(o.optJSONArray("terms")).ifEmpty { return null }
        return Interest(
            id, str(o, "name") ?: return null, theme, strings(o.optJSONArray("also")).filter { it in THEMES }, image, terms,
            o.optJSONObject("shows")?.let { Shows(ints(it.optJSONArray("keywords")), ints(it.optJSONArray("genres"))) },
            o.optJSONObject("news")?.let { n -> str(n, "query")?.let(::News) },
            o.optJSONObject("videos")?.let { v -> str(v, "query")?.let { Videos(it, v.optInt("minMinutes", 8)) } },
            o.optJSONObject("live")?.let { l -> str(l, "kind")?.takeIf { it in LIVE_KINDS }?.let { Live(it, str(l, "league"), str(l, "query"), str(l, "tag"), str(l, "scoreStyle")) } },
            o.optJSONObject("refine")?.let { r ->
                val opts = r.optJSONArray("options")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::option) } } ?: emptyList()
                if (opts.isEmpty()) null else Refine(str(r, "label") ?: "", opts)
            },
        )
    }

    private fun option(o: JSONObject): RefineOption? {
        val id = str(o, "id") ?: return null
        return RefineOption(id, str(o, "name") ?: return null, strings(o.optJSONArray("terms")).ifEmpty { return null }, str(o, "espnTeam"), str(o, "league"), str(o, "query"), str(o, "tag"))
    }
}
