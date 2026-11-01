package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CardState { LIVE, NEXT, RESULT, NONE }

data class TeamLine(val name: String, val abbr: String, val score: String, val batting: Boolean)

data class LiveCard(
    val id: String, val sport: String, val title: String, val state: CardState, val line1: String, val line2: String,
    val startMs: Long, val fetchedMs: Long, val lat: Double?, val lon: Double?, val handoffPkg: String?, val handoffLabel: String,
    val teams: List<TeamLine> = emptyList(), val short: String = "", val venue: String? = null, val image: String? = null,
    val competition: String? = null, val format: String? = null,
)

data class LiveInputs(
    val cricketLive: List<CricketScore>, val cricketEvents: Map<String, List<SportEvent>>, val f1Next: Race?, val f1Sessions: List<SportEvent>,
    val f1Standings: List<Standing>, val motogpNews: List<RssItem>, val tennis: List<SportEvent>,
    /** When each sport's data was really fetched (keys cricket, f1, motogp, tennis); a card's age comes from here, not from build time. */
    val fetchedAt: Map<String, Long> = emptyMap(),
    /** ISO country the hand-offs are for: where the TV is seen from, else where the household lives. Blank or unknown means no app buttons. */
    val country: String = "",
    /** Series and format per Cricinfo match number (ESPN header feed). */
    val cricketSeries: Map<String, Series> = emptyMap(),
    /** Scoreboard events per ESPN league path ("soccer/eng.1") for loves other than the four sports above. */
    val espnEvents: Map<String, List<SportEvent>> = emptyMap(),
)

/**
 * One sport the household follows. [sport] is cricket, f1, motogp, tennis or, for any other ESPN sport, the love id. Empty [terms] follows
 * everything. [teams] narrows one ESPN league to chosen clubs; a league with no entry shows all its matches. [homes]: the home countries of the people who follow it.
 */
data class Follow(val sport: String, val name: String, val leagues: List<String>, val terms: List<String>, val teams: Map<String, List<String>> = emptyMap(), val homes: Set<String> = emptySet())

/** Which sports get a Live card, from every profile's loves (live kind cricinfo, jolpica, motogp or espn) and their chosen refine options. */
data class SportPrefs(val follows: List<Follow>) {
    val sports: Set<String> get() = follows.map { it.sport }.toSet()
    fun follow(sport: String): Follow? = follows.firstOrNull { it.sport == sport }
    val cricketTeams: List<String> get() = follow("cricket")?.terms ?: emptyList()
    /** A cricket match is "home" when its venue is in one of these countries. */
    val cricketHomes: Set<String> get() = follow("cricket")?.homes ?: emptySet()
    val tennisLeagues: List<String> get() = follow("tennis")?.leagues ?: emptyList()
    /** Scoreboards for the loves other than the four sports with their own sources. */
    val espnLeagues: List<String> get() = follows.filter { it.sport !in FIXED }.flatMap { it.leagues }.distinct()
    /** Slam rounds and finals always; chosen players and events too. Nothing chosen matches no player. */
    fun tennisRule(rounds: List<String>) = TennisRule(rounds, follow("tennis")?.terms?.takeIf { it.isNotEmpty() }?.joinToString("|") { Regex.escape(it) } ?: "(?!)")

    companion object {
        val FIXED = listOf("cricket", "f1", "motogp", "tennis")

        fun of(profiles: List<Profile>, catalog: List<Interest>): SportPrefs {
            val each = profiles.flatMap { p -> p.loves.mapNotNull { love ->
                val i = if (love.custom != null) null else catalog.firstOrNull { it.id == love.id }
                val live = i?.live ?: return@mapNotNull null
                val chosen = i.refine?.options?.filter { it.id in love.refine } ?: emptyList()
                val all = (live.league ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val leagues = chosen.mapNotNull { it.league }.ifEmpty { all }.distinct()
                when (live.kind) {
                    "cricinfo" -> Follow("cricket", i.name, emptyList(), chosen.flatMap { it.terms }, homes = setOf(p.home.country))
                    "jolpica" -> Follow("f1", i.name, emptyList(), emptyList())
                    "motogp" -> Follow("motogp", i.name, emptyList(), emptyList())
                    "espn" -> if (all.isNotEmpty() && all.all { it.startsWith("tennis/") }) Follow("tennis", i.name, leagues, chosen.filter { it.league == null }.flatMap { it.terms })
                        // as LoveFeeds: a club narrows its own league (or every league when it names none); other leagues stay whole
                        else Follow(i.id, i.name, leagues, emptyList(), leagues.associateWith { lg -> chosen.filter { it.espnTeam != null && (it.league == null || it.league == lg) }.flatMap { it.terms } }.filterValues { it.isNotEmpty() })
                    else -> null
                }
            } }
            // several people loving one sport: one card, every league; a person who chose nothing (for a league) opens it to everything
            return SportPrefs(each.groupBy { it.sport }.map { (sport, fs) ->
                val leagues = fs.flatMap { it.leagues }.distinct()
                val teams = leagues.associateWith { lg -> fs.filter { lg in it.leagues }.let { has -> if (has.any { it.teams[lg].isNullOrEmpty() }) emptyList() else has.flatMap { it.teams.getValue(lg) }.distinct() } }
                Follow(sport, fs[0].name, leagues, if (fs.any { it.terms.isEmpty() }) emptyList() else fs.flatMap { it.terms }.distinct(), teams.filterValues { it.isNotEmpty() }, fs.flatMap { it.homes }.toSet())
            })
        }
    }
}

/**
 * Turns parsed sport data into the Live rail: one card per loved sport (cricket may have several), LIVE first, then NEXT by start, RESULT, NONE.
 * Hand-offs come from [Handoffs] for the inputs' country; a country without data gives cards with no app.
 */
object LiveCards {
    private const val DAY = 24 * 3600_000L
    private val SPORTS = SportPrefs.FIXED

    fun build(inputs: LiveInputs, config: FeedConfig, prefs: SportPrefs, handoffs: Handoffs, nowMs: Long): List<LiveCard> {
        val cards = ArrayList<LiveCard>()
        if ("cricket" in prefs.sports) cards += cricket(inputs, config, prefs, handoffs, nowMs)
        if ("f1" in prefs.sports) cards += f1(inputs, handoffs, nowMs)
        if ("motogp" in prefs.sports) cards += motogp(inputs, config, handoffs, nowMs)
        if ("tennis" in prefs.sports) cards += tennis(inputs, config, prefs, handoffs, nowMs)
        prefs.follows.filter { it.sport !in SPORTS }.forEach { cards += espn(it, inputs, handoffs, nowMs) }
        val stamped = cards.map { c -> c.copy(fetchedMs = inputs.fetchedAt[c.sport] ?: nowMs) }
        val teams = prefs.cricketTeams.map(::word)
        return stamped.sortedWith(compareBy<LiveCard>({ it.state.ordinal }, { if (it.state == CardState.NEXT) it.startMs else 0L },
            { SPORTS.indexOf(it.sport).let { i -> if (i < 0) SPORTS.size else i } }, { c -> if (teams.any { it.containsMatchIn(c.title) }) 0 else 1 }))
    }

    fun toJson(cards: List<LiveCard>): JSONArray = JSONArray().also { arr ->
        cards.forEach { c ->
            arr.put(JSONObject().put("id", c.id).put("sport", c.sport).put("title", c.title).put("state", c.state.name).put("line1", c.line1).put("line2", c.line2)
                .put("startMs", c.startMs).put("fetchedMs", c.fetchedMs).put("lat", c.lat ?: JSONObject.NULL).put("lon", c.lon ?: JSONObject.NULL)
                .put("handoffPkg", c.handoffPkg ?: JSONObject.NULL).put("handoffLabel", c.handoffLabel)
                .put("short", c.short).put("venue", c.venue ?: JSONObject.NULL).put("image", c.image ?: JSONObject.NULL)
                .put("competition", c.competition ?: JSONObject.NULL).put("format", c.format ?: JSONObject.NULL)
                .put("teams", JSONArray().also { ts -> c.teams.forEach { t -> ts.put(JSONObject().put("name", t.name).put("abbr", t.abbr).put("score", t.score).put("batting", t.batting)) } }))
        }
    }

    private val ABBR = mapOf(
        // national sides
        "india" to "IND", "england" to "ENG", "australia" to "AUS", "pakistan" to "PAK", "south africa" to "SA", "new zealand" to "NZ",
        "sri lanka" to "SL", "bangladesh" to "BAN", "west indies" to "WI", "afghanistan" to "AFG", "united arab emirates" to "UAE",
        "zimbabwe" to "ZIM", "ireland" to "IRE", "scotland" to "SCO", "netherlands" to "NED", "nepal" to "NEP", "oman" to "OMA",
        "united states of america" to "USA",
        // IPL franchises
        "royal challengers bengaluru" to "RCB", "mumbai indians" to "MI", "chennai super kings" to "CSK", "kolkata knight riders" to "KKR",
        "delhi capitals" to "DC", "sunrisers hyderabad" to "SRH", "rajasthan royals" to "RR", "punjab kings" to "PBKS", "gujarat titans" to "GT",
        "lucknow super giants" to "LSG",
    )
    fun abbr(name: String): String {
        val base = name.replace(Regex("\\b(Women|Men|A|U19|Under-19s?)\\b", RegexOption.IGNORE_CASE), "").trim().lowercase()
        return ABBR[base] ?: base.filter { it.isLetter() }.take(3).uppercase()
    }

    fun countdown(deltaMs: Long, nowMs: Long): String {
        if (deltaMs < 0) return "now"
        val m = deltaMs / 60_000
        return when {
            m < 60 -> "in $m m"
            m < 24 * 60 -> "in ${m / 60} h ${m % 60} m"
            else -> "starts " + SimpleDateFormat("EEE HH:mm", Locale.UK).format(Date(nowMs + deltaMs))
        }
    }

    // ---------- cricket ----------
    private fun word(team: String) = Regex("\\b" + Regex.escape(team) + "\\b", RegexOption.IGNORE_CASE)
    private fun eventMentions(e: SportEvent, team: String) = word(team).let { r -> r.containsMatchIn(e.name) || e.competitors.any { c -> r.containsMatchIn(c.name) } }

    /** [homes]: the cricket lovers' home countries; a match at a known venue in one of them is a home match. */
    private fun cricketHandoff(leagueId: String?, venue: String?, config: FeedConfig, handoffs: Handoffs, country: String, homes: Set<String>): Pair<Handoff, Venue?> {
        val league = config.cricketLeagues.firstOrNull { it.id == leagueId }?.name ?: ""
        val place = venue?.let { v -> config.venues.entries.firstOrNull { v.contains(it.key, ignoreCase = true) }?.value }
        val key = when {
            league.equals("IPL", true) -> "cricket.ipl"
            league.contains("World Cup", true) || league.contains("Champions Trophy", true) -> "cricket.icc"
            place != null && place.country in homes -> "cricket.home"
            else -> "cricket.away"
        }
        return handoffs.find(country, key, "cricket") to place
    }

    private fun cricket(inp: LiveInputs, config: FeedConfig, prefs: SportPrefs, handoffs: Handoffs, now: Long): List<LiveCard> {
        val out = ArrayList<LiveCard>()
        val teams = prefs.cricketTeams
        // nothing chosen follows any match; a chosen league ("IPL", "World Cup") follows every match in it
        fun followed(league: String, e: SportEvent) = teams.isEmpty() || teams.any { eventMentions(e, it) } ||
            config.cricketLeagues.firstOrNull { it.id == league }?.name?.let { n -> teams.any { word(it).containsMatchIn(n) } } == true
        val live = if (teams.isEmpty()) inp.cricketLive.take(3) else CricinfoLive.followed(inp.cricketLive, teams)
        live.forEach { s ->
            val match = inp.cricketEvents.entries.firstNotNullOfOrNull { (league, evs) -> evs.firstOrNull { eventMentions(it, s.teamA) && eventMentions(it, s.teamB) }?.let { league to it } }
            val (handoff, pos) = cricketHandoff(match?.first, match?.second?.venue, config, handoffs, inp.country, prefs.cricketHomes)
            // Cricinfo keeps finished matches in the live feed without an asterisk: both scores and no asterisk is a result.
            val finished = !s.battingA && !s.battingB && s.scoreA.isNotBlank() && s.scoreB.isNotBlank()
            val teams = listOf(TeamLine(s.teamA, abbr(s.teamA), s.scoreA, s.battingA), TeamLine(s.teamB, abbr(s.teamB), s.scoreB, s.battingB))
            val short = teams.joinToString(" v ") { t -> (t.abbr + " " + t.score).trim() }
            val sentence = if (teams.all { it.score.isBlank() && !it.batting }) "Before the toss" else teams.joinToString(", ") { t -> if (t.score.isBlank() && !t.batting) "${t.name} yet to bat" else (t.name + " " + t.score).trim() + (if (t.batting) " batting" else "") }
            val series = CricinfoLive.matchId(s.link)?.let { inp.cricketSeries[it] }
            val competition = series?.name?.let { CricketSeries.competition(it) } ?: match?.first?.let { id -> config.cricketLeagues.firstOrNull { it.id == id }?.name }
            out += LiveCard("cricket:${s.teamA} v ${s.teamB}", "cricket", "${s.teamA} v ${s.teamB}", if (finished) CardState.RESULT else CardState.LIVE, sentence, "", match?.second?.startMs ?: now, now, pos?.lat, pos?.lon, handoff.pkg, handoff.label, teams, short, match?.second?.venue, competition = competition, format = series?.format)
        }
        if (out.isEmpty()) {
            val all = inp.cricketEvents.flatMap { (league, evs) -> evs.filter { e -> followed(league, e) }.map { league to it } }
            val next = all.filter { it.second.state == EventState.PRE && it.second.startMs > now }.minByOrNull { it.second.startMs }
            val recent = all.filter { it.second.state == EventState.POST && now - it.second.startMs < 2 * DAY }.maxByOrNull { it.second.startMs }
            val pick = next ?: recent
            if (pick != null) {
                val (league, e) = pick; val (handoff, pos) = cricketHandoff(league, e.venue, config, handoffs, inp.country, prefs.cricketHomes)
                val state = if (e.state == EventState.PRE) CardState.NEXT else CardState.RESULT
                out += LiveCard("cricket:${e.name}", "cricket", e.name, state, if (state == CardState.NEXT) countdown(e.startMs - now, now) else e.detail.ifBlank { "Result" }, e.venue ?: "", e.startMs, now, pos?.lat, pos?.lon, handoff.pkg, handoff.label, venue = e.venue)
            } else {
                val h = handoffs.find(inp.country, "cricket.away", "cricket")
                out += LiveCard("cricket", "cricket", "Cricket", CardState.NONE, "No followed match listed", "", 0L, now, null, null, h.pkg, h.label)
            }
        }
        return out
    }

    // ---------- formula 1 ----------
    private fun points(p: Double) = if (p == Math.floor(p)) p.toLong().toString() else p.toString()
    private fun f1(inp: LiveInputs, handoffs: Handoffs, now: Long): LiveCard {
        val h = handoffs.find(inp.country, "f1")
        val leader = inp.f1Standings.firstOrNull()?.let { "${it.driver} leads, ${points(it.points)} pts" }
        val race = inp.f1Next
        fun gp(e: SportEvent) = e.name.substringBefore(" · ")
        fun session(e: SportEvent) = e.name.substringAfter(" · ", "Session")
        val live = inp.f1Sessions.firstOrNull { it.state == EventState.IN }
        val next = inp.f1Sessions.filter { it.state == EventState.PRE && it.startMs > now }.minByOrNull { it.startMs }
        val done = inp.f1Sessions.filter { it.state == EventState.POST && now - it.startMs < DAY }.maxByOrNull { it.startMs }
        return when {
            live != null -> LiveCard("f1", "f1", gp(live), CardState.LIVE, "${session(live)} live", leader ?: (live.venue ?: ""), live.startMs, now, race?.lat, race?.lon, h.pkg, h.label, venue = live.venue ?: race?.circuit)
            next != null -> LiveCard("f1", "f1", gp(next), CardState.NEXT, "${session(next)} ${countdown(next.startMs - now, now)}", leader ?: (next.venue ?: ""), next.startMs, now, race?.lat, race?.lon, h.pkg, h.label, venue = next.venue ?: race?.circuit)
            done != null -> LiveCard("f1", "f1", gp(done), CardState.RESULT, "${session(done)} finished", leader ?: (done.venue ?: ""), done.startMs, now, race?.lat, race?.lon, h.pkg, h.label, venue = done.venue ?: race?.circuit)
            race != null && race.startMs > now -> LiveCard("f1", "f1", race.name, CardState.NEXT, "Race ${countdown(race.startMs - now, now)}", leader ?: "${race.circuit}, ${race.locality}", race.startMs, now, race.lat, race.lon, h.pkg, h.label, venue = race.circuit)
            else -> LiveCard("f1", "f1", "Formula 1", CardState.NONE, leader ?: "Season paused", race?.let { "Next: ${it.name}" } ?: "", 0L, now, null, null, h.pkg, h.label, venue = race?.circuit)
        }
    }

    // ---------- motogp ----------
    private fun motogp(inp: LiveInputs, config: FeedConfig, handoffs: Handoffs, now: Long): LiveCard {
        val h = handoffs.find(inp.country, "motogp")
        val headline = inp.motogpNews.maxByOrNull { it.publishedMs }?.title ?: ""
        val round = config.motogp.map { it to EspnScoreboard.isoMs(it.raceUtc) }.filter { it.second > now - 2 * 3600_000L }.minByOrNull { it.second }
        return when {
            round != null && round.second <= now -> LiveCard("motogp", "motogp", round.first.name, CardState.LIVE, "Race live", headline, round.second, now, round.first.lat, round.first.lon, h.pkg, h.label, venue = round.first.circuit)
            round != null && round.second - now < 10 * DAY -> LiveCard("motogp", "motogp", round.first.name, CardState.NEXT, "Race ${countdown(round.second - now, now)}", headline, round.second, now, round.first.lat, round.first.lon, h.pkg, h.label, venue = round.first.circuit)
            else -> LiveCard("motogp", "motogp", "MotoGP", CardState.NONE, headline, round?.first?.let { "Next: ${it.name}" } ?: "", 0L, now, null, null, h.pkg, h.label, venue = round?.first?.circuit)
        }
    }

    // ---------- tennis ----------
    fun tennisPick(events: List<SportEvent>, rule: TennisRule, now: Long): List<SportEvent> {
        val players = Regex(rule.playersRegex, RegexOption.IGNORE_CASE)
        val named = events.filter { e -> e.competitors.size >= 2 && e.competitors.all { it.name.isNotBlank() } }
        val kept = named.filter { e -> rule.allRounds.any { e.name.contains(it, true) } || e.round.equals("Final", true) || players.containsMatchIn(e.name) || e.competitors.any { players.containsMatchIn(it.name) } }
        val live = kept.filter { it.state == EventState.IN }
        val soon = kept.filter { it.state == EventState.PRE && it.startMs > now && it.startMs - now < DAY }.sortedBy { it.startMs }
        val recent = kept.filter { it.state == EventState.POST && now - it.startMs < DAY }.sortedByDescending { it.startMs }
        return live + soon + recent
    }

    private fun tennis(inp: LiveInputs, config: FeedConfig, prefs: SportPrefs, handoffs: Handoffs, now: Long): LiveCard {
        val e = tennisPick(inp.tennis, prefs.tennisRule(config.tennisRounds), now).firstOrNull()
        val h = handoffs.find(inp.country, if (e != null && e.name.contains("Wimbledon", true)) "tennis.wimbledon" else null, "tennis")
        if (e == null) return LiveCard("tennis", "tennis", "Tennis", CardState.NONE, "Nothing followed today", "", 0L, now, null, null, h.pkg, h.label)
        val state = when (e.state) { EventState.IN -> CardState.LIVE; EventState.PRE -> CardState.NEXT; EventState.POST -> CardState.RESULT }
        val players = e.competitors.joinToString(" v ") { it.name }
        val group = e.name.substringAfter(" · ", "")
        val line2 = listOfNotNull(e.round, group.ifBlank { null }, if (state == CardState.NEXT) countdown(e.startMs - now, now) else null).joinToString(" · ")
        return LiveCard("tennis", "tennis", e.name.substringBefore(" · "), state, players, line2, e.startMs, now, null, null, h.pkg, h.label, venue = e.venue)
    }

    // ---------- any other ESPN sport a person loves (football, basketball, rugby...) ----------
    private fun espn(f: Follow, inp: LiveInputs, handoffs: Handoffs, now: Long): LiveCard {
        val all = f.leagues.flatMap { lg -> (inp.espnEvents[lg] ?: emptyList()).map { lg to it } }.filter { (lg, e) -> f.teams[lg]?.any { eventMentions(e, it) } ?: true }
        val live = all.firstOrNull { it.second.state == EventState.IN }
        val next = all.filter { it.second.state == EventState.PRE && it.second.startMs > now }.minByOrNull { it.second.startMs }
        val recent = all.filter { it.second.state == EventState.POST && now - it.second.startMs < 2 * DAY }.maxByOrNull { it.second.startMs }
        val (league, e) = live ?: next ?: recent ?: run {
            val h = handoffs.find(inp.country, *f.leagues.toTypedArray(), f.sport)
            return LiveCard(f.sport, f.sport, f.name, CardState.NONE, "Nothing followed today", "", 0L, now, null, null, h.pkg, h.label)
        }
        val h = handoffs.find(inp.country, league, f.sport)
        val state = when (e.state) { EventState.IN -> CardState.LIVE; EventState.PRE -> CardState.NEXT; EventState.POST -> CardState.RESULT }
        val two = e.competitors.size == 2
        val pre = e.state == EventState.PRE   // ESPN sends "0" before kick-off: no score until it starts
        val score = if (two && !pre && e.competitors.all { it.score != null }) "${e.competitors[0].score}-${e.competitors[1].score}" else ""
        val line1 = when (state) {
            CardState.NEXT -> countdown(e.startMs - now, now)
            CardState.LIVE -> listOf(score, e.detail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Live" }
            else -> listOf(score, e.detail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Result" }
        }
        val teams = if (two) e.competitors.map { TeamLine(it.name, it.abbr ?: abbr(it.name), if (pre) "" else it.score ?: "", false) } else emptyList()
        return LiveCard(f.sport, f.sport, if (two) "${e.competitors[0].name} v ${e.competitors[1].name}" else e.name, state, line1, e.venue ?: "", e.startMs, now, null, null, h.pkg, h.label,
            teams, if (two) teams.joinToString(" v ") { (it.abbr + " " + it.score).trim() } else "", e.venue)
    }
}
