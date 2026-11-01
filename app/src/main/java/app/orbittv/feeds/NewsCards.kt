package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class Headline(val title: String, val source: String, val publishedMs: Long)
data class TopicCard(val id: String, val label: String, val headlines: List<Headline>)

/** The Today rail: a few fresh, deduplicated headlines per topic, newest first. Google News titles end with " - Source". */
object NewsCards {
    fun build(topics: List<Topic>, itemsByTopic: Map<String, List<RssItem>>, nowMs: Long, maxPerTopic: Int = 8, windowMs: Long = 24 * 3600_000L): List<TopicCard> =
        topics.map { t ->
            val seen = HashSet<String>()
            val heads = (itemsByTopic[t.id] ?: emptyList())
                .filter { it.publishedMs == 0L || nowMs - it.publishedMs <= windowMs }
                .map { toHeadline(it) }
                .filter { it.title.isNotBlank() && seen.add(it.title.lowercase()) }
                .sortedWith(compareBy<Headline>({ if (it.publishedMs == 0L) 1 else 0 }, { -it.publishedMs }))
                .take(maxPerTopic)
            TopicCard(t.id, t.label, heads)
        }

    /**
     * Home news: headlines that name a home place, its region or the home country in any of their names ([names], title or description;
     * [Relevance.keep]), the person's first language's first (each language newest first), then the next language's. [perLanguage] is in
     * the person's language order, [languages] the same languages (their common words tell what a headline is written in). The names
     * are in Latin letters, so a headline mostly in another script (Telugu, Kannada) is kept as Google matched it. When fewer than
     * [least] headlines name the home and the feed is the home country's own edition ([countryScoped]), the best ranked of the others in
     * the person's languages fill up to it: some home news rather than none. A global search (no edition) is never filled: empty is better than wrong.
     */
    fun home(id: String, label: String, perLanguage: List<List<RssItem>>, names: List<String>, nowMs: Long, max: Int = 8, windowMs: Long = 24 * 3600_000L,
             languages: List<Language> = emptyList(), least: Int = 3, countryScoped: Boolean = false): TopicCard {
        val seen = HashSet<String>()
        val all = perLanguage.mapIndexed { i, items ->
            items.filter { (it.publishedMs == 0L || nowMs - it.publishedMs <= windowMs) && it.title.isNotBlank() && !Relevance.listing(it.title, it.source) }.map { item ->
                val h = toHeadline(item)
                Triple(h, writtenIn(h.title, languages) ?: i, !mostlyLatin(h.title) || Relevance.keep(h.title + " " + plain(item.description), names))   // an edition carries other languages' stories too
            }
        }.flatten().sortedWith(compareBy<Triple<Headline, Int, Boolean>>({ it.second }, { if (it.first.publishedMs == 0L) 1 else 0 }, { -it.first.publishedMs }))
            .filter { it.first.title.isNotBlank() && seen.add(it.first.title.lowercase()) }
        val named = all.filter { it.third }.map { it.first }
        // the fill is in the person's languages: their own script, or at least one of their common words
        val fill = all.filter { !it.third && (!mostlyLatin(it.first.title) || writtenIn(it.first.title, languages, 1) != null) }.map { it.first }
        val heads = if (named.size >= least || !countryScoped) named else named + fill.take(least - named.size)
        return TopicCard(id, label, heads.take(max))
    }
    private fun plain(html: String?) = html?.replace(Regex("<[^>]+>"), " ") ?: ""

    /** The index in [languages] of the language [title] reads as, by its common words (languages.json); null when it cannot tell. */
    private fun writtenIn(title: String, languages: List<Language>, atLeast: Int = 2): Int? {
        val words = title.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val scores = languages.mapIndexedNotNull { i, l -> l.common.takeIf { it.isNotEmpty() }?.toSet()?.let { s -> i to words.count { it in s } } }
        val best = scores.maxByOrNull { it.second } ?: return null
        return if (best.second >= atLeast && scores.count { it.second == best.second } == 1) best.first else null
    }

    private fun mostlyLatin(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        return letters.isNotEmpty() && letters.count { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN } * 2 >= letters.length
    }

    /** Google News titles end with " - Source" and carry a matching <source>; only that known suffix is stripped. Other feeds keep their titles whole. */
    fun toHeadline(item: RssItem): Headline {
        val known = item.source?.takeIf { it.isNotBlank() && item.title.endsWith(" - $it") }
        val title = if (known != null) item.title.removeSuffix(" - $known").trim() else item.title.trim()
        val source = item.source?.ifBlank { null } ?: host(item.link)
        return Headline(title, source, item.publishedMs)
    }

    private fun host(link: String): String = try { URI(link).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }

    fun toJson(cards: List<TopicCard>): JSONArray = JSONArray().also { arr ->
        cards.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("label", c.label).put("headlines", JSONArray().also { hs ->
                c.headlines.forEach { h -> hs.put(JSONObject().put("title", h.title).put("source", h.source).put("publishedMs", h.publishedMs)) }
            }))
        }
    }
}
