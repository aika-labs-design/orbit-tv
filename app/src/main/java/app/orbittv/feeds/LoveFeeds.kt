package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** The two personal keys. They only ever go into request URLs: never into a log, a cache body or the page. */
data class LoveKeys(val tmdb: String, val youtube: String) { override fun toString() = "LoveKeys(redacted)" }

/** One love of one profile, resolved against the catalogue. [id] is the profile's own love id (the page key). */
data class LoveRef(val id: String, val interest: Interest, val refine: List<String> = emptyList(), val weight: Double? = null)

/** Plain JSON items so they cache and render without a second shape: shows, news and videos have a "title", live items a "kind". */
data class LoveItems(val shows: List<JSONObject>, val news: List<JSONObject>, val videos: List<JSONObject>, val live: List<JSONObject>, val why: String) {
    fun toJson(): JSONObject = JSONObject().put("shows", JSONArray(shows)).put("news", JSONArray(news)).put("videos", JSONArray(videos)).put("live", JSONArray(live)).put("why", why)
}

/** One profile's loves with the languages (main first) and country they are fetched for. */
data class LoveBatch(val key: String, val loves: List<LoveRef>, val langs: List<String>, val country: Country?)

/**
 * Shows, news, videos and live events for each love. Every item passes [Relevance] (keep for the love and its chosen refine options, and not
 * clickbait for videos and news) or it is dropped: a love can be empty, never padded. Caches hold parsed items only, keyed by love, languages,
 * country and chosen refine ids, so profiles with the same choices share the work. Shows 24 h, news 30 min, videos 24 h, live 15 min
 * (sport), 24 h (YouTube live, one search a day) or 1 h (radio). Loves take at most [YouTubeQuota.LOVES_LIMIT] searches a day, highest weight first; an old cache beats nothing.
 */
class LoveFeeds(
    private val fetch: (String) -> String?, private val store: FeedStore, private val keys: LoveKeys, private val quota: YouTubeQuota,
    private val languageName: (String) -> String = { it }, private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val MIN = 60_000L
        private const val DAY = 86_400_000L
        private const val MAX_ITEMS = 8
        private val SPORT_KINDS = setOf("espn", "cricinfo", "jolpica", "motogp")

        /** A profile's love against the bundled catalogue; a free-text love gets an untuned recipe, an unknown id is skipped. */
        fun resolve(love: Love, catalog: List<Interest>): LoveRef? {
            val i = if (love.custom != null) Interests.custom(love.custom) else catalog.firstOrNull { it.id == love.id } ?: return null
            return LoveRef(love.id, i, love.refine, love.weight)
        }
    }

    /** Every profile's loves in one global order (weight, then profile and pick order), so a short allowance goes to the most used loves in the household. */
    fun refreshAll(batches: List<LoveBatch>): Map<String, Map<String, LoveItems>> {
        val done = HashMap<Pair<String, String>, LoveItems>()
        batches.flatMap { b -> b.loves.map { b to it } }.sortedByDescending { it.second.weight ?: 1.0 }.forEach { (b, l) ->   // no saved weight is 1, as on the page
            done[b.key to l.id] = runCatching { one(l, b.langs.ifEmpty { listOf("en") }, b.country) }.getOrElse { LoveItems(emptyList(), emptyList(), emptyList(), emptyList(), why(l)) }
        }
        return batches.associate { b -> b.key to b.loves.associate { it.id to done.getValue(b.key to it.id) } }
    }

    private fun why(l: LoveRef): String {
        val chosen = chosen(l)
        return if (chosen.isEmpty()) "Because you love ${l.interest.name}" else "Because you follow ${chosen.joinToString(", ") { it.name }} in ${l.interest.name}"
    }
    private fun chosen(l: LoveRef) = l.interest.refine?.options?.filter { it.id in l.refine } ?: emptyList()

    private fun one(l: LoveRef, langs: List<String>, country: Country?): LoveItems {
        val lang = World.newsLanguages(langs, country).first(); val i = l.interest; val chosen = chosen(l)   // news, videos and live search
        val terms = i.terms + chosen.flatMap { it.terms }
        val key = listOf(l.id, langs.joinToString(","), country?.code ?: "", l.refine.sorted().joinToString("+")).joinToString("|")
        return LoveItems(
            items("shows", key, DAY) { shows(i, terms, langs) },
            items("news", key, 30 * MIN) { news(i, chosen, terms, lang, country) },
            items("videos", key, DAY) { videos(i, chosen, terms, lang) },
            items("live", key, when (i.live?.kind) { in SPORT_KINDS -> 15 * MIN; "ytlive" -> DAY; else -> 60 * MIN }) { live(i, chosen, terms, lang) },
            why(l),
        )
    }

    // ---- cache: {"fetchedMs": n, "items": [...]}. A loader returns null when it could not answer (no key, no quota, no network): nothing is saved then.
    private fun items(kind: String, key: String, maxAge: Long, load: () -> List<JSONObject>?): List<JSONObject> {
        val name = "love_${kind}_" + key.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(50) + "_" + Integer.toHexString(key.hashCode()) + ".json"
        val old = runCatching { JSONObject(store.load(name) ?: "") }.getOrNull()
        val oldItems = old?.optJSONArray("items")?.let { a -> List(a.length()) { a.getJSONObject(it) } }
        if (old != null && oldItems != null && now() - old.optLong("fetchedMs") in 0 until maxAge) return oldItems
        val fresh = load()
        if (fresh != null) { store.save(name, JSONObject().put("fetchedMs", now()).put("items", JSONArray(fresh)).toString()); return fresh }
        return oldItems ?: emptyList()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun plain(html: String?) = html?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim() ?: ""

    // ---- shows: TMDB titles by keyword (or genre); a free-text love finds its keywords first
    private fun shows(i: Interest, terms: List<String>, langs: List<String>): List<JSONObject>? {
        if (keys.tmdb.isBlank()) return null
        val kw = i.shows?.keywords ?: if (!i.tuned) (fetch(Tmdb.keywordSearchUrl(keys.tmdb, i.terms.first()))?.let { Tmdb.parseKeywordIds(it) } ?: return null) else emptyList()
        val genres = i.shows?.genres ?: emptyList()
        if (kw.isEmpty() && genres.isEmpty()) return emptyList()
        val bodies = listOf("tv", "movie").map { it to fetch(Tmdb.discoverByUrl(keys.tmdb, it, kw, genres, langs)) }
        if (bodies.all { it.second == null }) return null
        return bodies.flatMap { (kind, b) -> b?.let { Tmdb.parseDiscover(it, kind) } ?: emptyList() }
            .filter { it.lang in langs && Relevance.keep(it.title + " " + it.originalTitle + " " + it.overview, terms) }
            .sortedByDescending { it.popularity }.distinctBy { it.title }.take(MAX_ITEMS)
            .map { JSONObject().put("id", it.id).put("kind", it.kind).put("title", it.title).put("year", it.year).put("overview", it.overview)
                .put("poster", it.poster?.let { p -> Tmdb.image(p, "w342") } ?: "").put("backdrop", it.backdrop?.let { p -> Tmdb.image(p, "w780") } ?: "") }
    }

    // ---- news: Google News in the person's country edition
    private fun news(i: Interest, chosen: List<RefineOption>, terms: List<String>, lang: String, country: Country?): List<JSONObject>? {
        val base = i.news?.query ?: return emptyList()
        val q = if (chosen.isEmpty()) base else "($base) (" + chosen.joinToString(" OR ") { "\"${it.name}\"" } + ")"
        val body = fetch(World.newsUrl(q, country, lang)) ?: return null
        return Rss.parse(body).filter { Relevance.keep(it.title + " " + plain(it.description), terms) && !Relevance.clickbait(it.title, terms) && !Relevance.listing(it.title, it.source) }
            .distinctBy { it.title }.take(MAX_ITEMS)
            .map { JSONObject().put("title", it.title).put("link", it.link).put("source", it.source ?: "").put("publishedMs", it.publishedMs) }
    }

    // ---- videos: one YouTube search per love, then details; embeddable and long enough via Journeys.parseVideos
    private fun videos(i: Interest, chosen: List<RefineOption>, terms: List<String>, lang: String): List<JSONObject>? {
        val v = i.videos ?: return emptyList()
        if (keys.youtube.isBlank()) return null
        val first = chosen.firstOrNull()
        val q = first?.query ?: if (first != null) "${v.query} ${first.name}" else v.query
        return youtube(q, "&relevanceLanguage=${enc(lang)}", live = false, min = v.minMinutes, terms = terms)?.map { it.put("kind", "video") }
    }

    private fun youtube(q: String, extra: String, live: Boolean, min: Int, terms: List<String>): List<JSONObject>? {
        val key = "love|$live|$q|$extra"   // a failed search rests an hour: the next refresh (every few minutes) must not spend another
        if (quota.resting(key) || !quota.take(YouTubeQuota.LOVES_LIMIT)) return null
        val ids = fetch(Journeys.searchUrl(keys.youtube, q, live) + extra)?.let { Journeys.parseSearch(it) } ?: return null.also { quota.failed(key) }
        val found = if (ids.isEmpty()) emptyList() else fetch(Journeys.videosUrl(keys.youtube, ids))?.let { Journeys.parseVideos(it, min, live) } ?: return null.also { quota.failed(key) }
        quota.succeeded(key)
        return found.filter { Relevance.keep(it.title, terms) && !Relevance.clickbait(it.title, terms) }.take(MAX_ITEMS)
            .map { JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel).put("minutes", it.minutes).put("live", it.live).put("thumb", it.thumb) }
    }

    // ---- live: sport scoreboards, YouTube live streams, radio by tag
    private fun live(i: Interest, chosen: List<RefineOption>, terms: List<String>, lang: String): List<JSONObject>? {
        val l = i.live ?: return emptyList()
        return when (l.kind) {
            "espn" -> espn(i, l, chosen)
            "cricinfo" -> fetch(FeedUrls.CRICINFO)?.let { b -> stories(Rss.parse(b), i, chosen, "sport", i.name) }
            "motogp" -> fetch(FeedUrls.THERACE_MOTOGP)?.let { b -> stories(Rss.parse(b), i, chosen, "story", "") }
            "jolpica" -> fetch(FeedUrls.JOLPICA_NEXT)?.let { b -> listOfNotNull(Jolpica.nextRace(b)).filter { Relevance.keep("${i.name} ${it.name} ${it.circuit}", terms) }
                .map { JSONObject().put("kind", "sport").put("title", it.name).put("detail", it.circuit).put("state", "pre").put("startMs", it.startMs) } }
            "ytlive" -> if (keys.youtube.isBlank()) null else youtube(l.query ?: i.videos?.query ?: i.name, "&relevanceLanguage=${enc(lang)}", live = true, min = 0, terms = terms)?.map { it.put("kind", "stream") }
            "radio" -> radio(i, l, chosen, terms, lang)
            else -> emptyList()
        }
    }

    /** Feed items (cricket scores, MotoGP stories): the love's name leads the text when the source is sport-specific, then refine terms narrow it. */
    private fun stories(items: List<RssItem>, i: Interest, chosen: List<RefineOption>, kind: String, lead: String): List<JSONObject> {
        val terms = i.terms + chosen.flatMap { it.terms }; val narrow = chosen.flatMap { it.terms }
        return items.filter { Relevance.keep("$lead ${it.title} ${plain(it.description)}", terms) && (narrow.isEmpty() || Relevance.keep(it.title + " " + plain(it.description), narrow)) }
            .take(MAX_ITEMS).map { JSONObject().put("kind", kind).put("title", it.title).put("link", it.link).put("state", "in").put("startMs", it.publishedMs) }
    }

    private fun espn(i: Interest, l: Live, chosen: List<RefineOption>): List<JSONObject>? {
        val withLeague = chosen.filter { it.league != null }
        val leagues = (if (withLeague.isNotEmpty()) withLeague.map { it.league!! } else (l.league ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }).distinct()
        var any = false
        val events = leagues.flatMap { lg ->
            val body = fetch("https://site.api.espn.com/apis/site/v2/sports/$lg/scoreboard") ?: return@flatMap emptyList<Triple<String, String, SportEvent>>()
            any = true
            (if (lg.startsWith("tennis/")) EspnScoreboard.tennisMatches(body) else EspnScoreboard.events(body)).map { Triple(lg, leagueName(body), it) }
        }
        if (!any) return null
        val teamTerms = chosen.filter { it.espnTeam != null }
        return events.filter { (lg, league, e) ->
            val text = "$league ${e.name} ${e.competitors.joinToString(" ") { it.name }}"
            val mine = chosen.filter { it.league == lg }
            val allowed = chosen.isEmpty() || (withLeague.isEmpty() && teamTerms.isEmpty()) || mine.any { it.espnTeam == null } ||
                teamTerms.any { o -> Relevance.keep(text, o.terms) && (o.league == null || o.league == lg) }
            allowed && Relevance.keep(text, i.terms + chosen.flatMap { it.terms })
        }.map { it.third }.distinctBy { it.id }
            .sortedWith(compareBy<SportEvent>({ it.state.ordinal.let { s -> if (s == 1) 0 else if (s == 0) 1 else 2 } }, { if (it.state == EventState.POST) -it.startMs else it.startMs })).take(6)
            .map { e ->
                val two = e.competitors.size == 2; val sets = l.scoreStyle == "sets"   // the catalogue says how the sport's score reads
                JSONObject().put("kind", "sport").put("title", if (two) "${e.competitors[0].name} v ${e.competitors[1].name}" else e.name)
                    .put("detail", e.detail).put("state", e.state.name.lowercase()).put("startMs", e.startMs).put("venue", e.venue ?: "")
                    .put("score", if (!two || e.state == EventState.PRE || e.competitors.any { it.score == null }) "" else if (sets) EspnScoreboard.setScore(e.competitors) else "${e.competitors[0].score}-${e.competitors[1].score}")
            }
    }

    /** The scoreboard's own league name ("English Premier League"), which is what makes a match between two unlisted clubs about the love. */
    private fun leagueName(body: String): String = runCatching { JSONObject(body).getJSONArray("leagues").getJSONObject(0).optString("name") }.getOrDefault("")

    private fun radio(i: Interest, l: Live, chosen: List<RefineOption>, terms: List<String>, lang: String): List<JSONObject>? {
        val tag = chosen.firstNotNullOfOrNull { it.tag } ?: l.tag ?: return emptyList()   // the chosen style's own tag (Latin jazz), else the love's
        val name = languageName(lang)
        val stations = fetch(RadioBrowser.tagUrl(tag, name))?.let { RadioBrowser.parse(it) }?.takeIf { it.isNotEmpty() } ?: fetch(RadioBrowser.tagUrl(tag, null))?.let { RadioBrowser.parse(it) } ?: return null
        return stations.filter { Relevance.keep(it.name, terms + tag) }.map { JSONObject().put("kind", "radio").put("title", it.name).put("url", it.url).put("detail", it.codec) }
    }
}
