package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LiveCardsTest {
    private val config = FeedConfig.parse(File("src/main/assets/feeds.json").readText())
    private val handoffs = Handoffs.parse(File("src/main/assets/handoffs.json").readText())
    private val catalog = Interests.parse(File("src/main/assets/interests.json").readText())
    private val cork = Living("Cork", 51.90, -8.48, "Europe/Dublin", "IE")
    private fun prefs(vararg loves: Love, home: String = "IN") = SportPrefs.of(listOf(Profile("p", "P", Home(home, listOf(Place("B", 1.0, 2.0, "UTC"))), listOf("kn"), cork, loves.toList(), 0L)), catalog)
    /** Today's followed sport, now expressed as loves. */
    private val fan = prefs(Love("cricket", listOf("india-cricket")), Love("formula-1", emptyList()), Love("motogp", emptyList()), Love("tennis", listOf("indian-tennis")))
    /** Cricket with no team chosen: any live match. */
    private val anyMatch = prefs(Love("cricket", emptyList()))
    private fun build(inp: LiveInputs, p: SportPrefs = fan, at: Long = now, h: Handoffs = handoffs) = LiveCards.build(inp, config, p, h, at)
    private val now = 1_793_500_000_000L // 2026-11-01T02:26:40Z
    private val h = 3600_000L
    private fun inputs(
        cricketLive: List<CricketScore> = emptyList(), cricketEvents: Map<String, List<SportEvent>> = emptyMap(), f1Next: Race? = null,
        f1Sessions: List<SportEvent> = emptyList(), f1Standings: List<Standing> = emptyList(), motogpNews: List<RssItem> = emptyList(), tennis: List<SportEvent> = emptyList(),
    ) = LiveInputs(cricketLive, cricketEvents, f1Next, f1Sessions, f1Standings, motogpNews, tennis, country = "IE")
    private fun ev(name: String, state: EventState, start: Long, comps: List<String>, venue: String? = null, round: String? = null, detail: String = "") =
        SportEvent("id", name, start, state, detail, comps.map { Competitor(it, null, null, false) }, venue, round)
    private val india = CricketScore("India", "187/3", "England", "180/7", true, false, "l")

    @Test fun `live india match comes first and carries the away hand-off when the venue is unknown`() {
        val cards = build(inputs(cricketLive = listOf(india)))
        val c = cards.first()
        assertEquals("cricket", c.sport); assertEquals(CardState.LIVE, c.state); assertEquals("India v England", c.title)
        assertEquals("India 187/3 batting, England 180/7", c.line1); assertEquals("", c.line2)
        assertNull(c.handoffPkg); assertEquals("Check SonyLIV or discovery+", c.handoffLabel)
    }
    @Test fun `a matching espn event at an indian venue gives the home hand-off and coordinates`() {
        val events = mapOf("9999" to listOf(ev("India v England", EventState.IN, now - h, listOf("India", "England"), venue = "Wankhede Stadium")))
        val c = build(inputs(cricketLive = listOf(india), cricketEvents = events)).first()
        assertEquals("com.discoveryplus.tv.fire", c.handoffPkg); assertEquals(18.9389, c.lat!!, 1e-4)
    }
    @Test fun `ipl league match hands off to sky even at an indian venue`() {
        val events = mapOf("8048" to listOf(ev("Mumbai Indians v Chennai Super Kings", EventState.IN, now - h, listOf("Mumbai Indians", "Chennai Super Kings"), venue = "Wankhede Stadium")))
        val mi = CricketScore("Mumbai Indians", "190/4", "Chennai Super Kings", "", false, true, "l")
        val c = build(inputs(cricketLive = listOf(mi), cricketEvents = events), anyMatch).first()
        assertNull(c.handoffPkg); assertEquals("On Sky Sports / NOW", c.handoffLabel)
    }
    @Test fun `upcoming followed fixture without a live score is NEXT`() {
        val events = mapOf("9999" to listOf(ev("India v Australia", EventState.PRE, now + 5 * h, listOf("India", "Australia"), venue = "Wankhede Stadium")))
        val c = build(inputs(cricketEvents = events)).first { it.sport == "cricket" }
        assertEquals(CardState.NEXT, c.state); assertEquals("India v Australia", c.title); assertEquals("in 5 h 0 m", c.line1); assertEquals("Wankhede Stadium", c.line2)
    }
    @Test fun `f1 live session shows the session, a pre-session weekend shows countdown and leader`() {
        val race = Race("Mexico City Grand Prix", 19, "Autodromo Hermanos Rodriguez", "Mexico City", "Mexico", 19.4042, -99.0907, now + 3 * h + 20 * 60_000)
        val live = listOf(ev("Mexico City Grand Prix · FP1", EventState.IN, now - h, emptyList()))
        val c1 = build(inputs(f1Next = race, f1Sessions = live)).first { it.sport == "f1" }
        assertEquals(CardState.LIVE, c1.state); assertEquals("Mexico City Grand Prix", c1.title); assertEquals("FP1 live", c1.line1)
        val pre = listOf(ev("Mexico City Grand Prix · Qual", EventState.POST, now - 5 * h, emptyList()), ev("Mexico City Grand Prix · Race", EventState.PRE, now + 3 * h + 20 * 60_000, emptyList()))
        val c2 = build(inputs(f1Next = race, f1Sessions = pre, f1Standings = listOf(Standing(1, "Antonelli", "Mercedes", 365.0)))).first { it.sport == "f1" }
        assertEquals(CardState.NEXT, c2.state); assertEquals("Race in 3 h 20 m", c2.line1); assertEquals("Antonelli leads, 365 pts", c2.line2); assertEquals(-99.0907, c2.lon!!, 1e-4)
        assertEquals("On Sky Sports / NOW", c2.handoffLabel)
    }
    @Test fun `motogp next round within ten days is NEXT, otherwise NONE with the latest headline`() {
        val news = listOf(RssItem("Sepang asphalt row", "l", now - h, null, null))
        val c = build(inputs(motogpNews = news)).first { it.sport == "motogp" }
        assertEquals(CardState.NEXT, c.state); assertEquals("Malaysian Grand Prix", c.title); assertEquals("Race in 4 h 33 m", c.line1); assertEquals("Sepang asphalt row", c.line2)
        assertEquals("com.discoveryplus.tv.fire", c.handoffPkg)
        val far = build(inputs(motogpNews = news), at = now + 40L * 24 * h).first { it.sport == "motogp" }
        assertEquals(CardState.NONE, far.state); assertEquals("MotoGP", far.title); assertEquals("Sepang asphalt row", far.line1)
    }
    @Test fun `tennis rule keeps slam rounds, finals and indian players only, live first`() {
        val m = listOf(
            ev("Erste Bank Open · Men's Singles", EventState.POST, now - 3 * h, listOf("Jacob Fearnley", "Pablo Carreno Busta"), round = "Final"),
            ev("Australian Open · Men's Singles", EventState.PRE, now + 5 * h, listOf("A", "B"), round = "2nd Round"),
            ev("Challenger Bergamo · Men's Singles", EventState.IN, now - h, listOf("Sumit Nagal", "C"), round = "1st Round"),
            ev("Moselle Open · Men's Singles", EventState.IN, now - h, listOf("D", "E"), round = "Quarterfinal"),
        )
        val kept = LiveCards.tennisPick(m, fan.tennisRule(config.tennisRounds), now)
        assertEquals(listOf("Challenger Bergamo · Men's Singles", "Australian Open · Men's Singles", "Erste Bank Open · Men's Singles"), kept.map { it.name })
        val c = build(inputs(tennis = m)).first { it.sport == "tennis" }
        assertEquals(CardState.LIVE, c.state); assertEquals("Challenger Bergamo", c.title); assertEquals("Sumit Nagal v C", c.line1); assertEquals("1st Round · Men's Singles", c.line2)
    }
    @Test fun `tennis matches without player names are skipped`() {
        val m = listOf(
            ev("Valle D'Aosta Ladies Open · Women's Doubles", EventState.POST, now - h, listOf("", ""), round = "Final"),
            ev("Erste Bank Open · Men's Singles", EventState.POST, now - 2 * h, listOf("Jacob Fearnley", "Pablo Carreno Busta"), round = "Final"),
        )
        assertEquals(listOf("Erste Bank Open · Men's Singles"), LiveCards.tennisPick(m, fan.tennisRule(config.tennisRounds), now).map { it.name })
    }
    @Test fun `a cricinfo title without an asterisk and both scores is a finished match`() {
        val done = CricinfoLive.parseTitle("India 88/5 v Pakistan 91/2", "l")!!
        val c = build(inputs(cricketLive = listOf(done))).first { it.sport == "cricket" }
        assertEquals(CardState.RESULT, c.state); assertEquals("India 88/5, Pakistan 91/2", c.line1); assertEquals("IND 88/5 v PAK 91/2", c.short)
    }
    @Test fun `a cricinfo title without an asterisk and one score is an innings break, still live`() {
        val mid = CricinfoLive.parseTitle("India 159/4 v England", "l")!!
        val c = build(inputs(cricketLive = listOf(mid))).first()
        assertEquals(CardState.LIVE, c.state); assertEquals("India 159/4, England yet to bat", c.line1)
    }
    @Test fun `fetchedMs comes from the source fetch time, not from build time`() {
        val c = build(inputs(cricketLive = listOf(india)).copy(fetchedAt = mapOf("cricket" to now - 20 * 60_000))).first()
        assertEquals(now - 20 * 60_000, c.fetchedMs)
    }
    @Test fun `cricket hands off to jiohotstar when the tv is seen in india`() {
        val c = build(inputs(cricketLive = listOf(india)).copy(country = "IN")).first()
        assertEquals("in.startv.hotstar", c.handoffPkg); assertEquals("JioHotstar", c.handoffLabel)
        val events = mapOf("8048" to listOf(ev("Mumbai Indians v Chennai Super Kings", EventState.IN, now - h, listOf("Mumbai Indians", "Chennai Super Kings"), venue = "Wankhede Stadium")))
        val mi = CricinfoLive.parseTitle("Mumbai Indians 190/4 * v Chennai Super Kings", "l")!!
        assertEquals("in.startv.hotstar", build(inputs(cricketLive = listOf(mi), cricketEvents = events).copy(country = "IN"), anyMatch).first().handoffPkg)
    }
    @Test fun `cards carry structured team lines, abbreviations and a short scoreline`() {
        val s = CricinfoLive.parseTitle("England 124/2 * v India 121", "l")!!
        val c = build(inputs(cricketLive = listOf(s))).first()
        assertEquals(listOf("ENG", "IND"), c.teams.map { it.abbr }); assertEquals(listOf(true, false), c.teams.map { it.batting })
        assertEquals("ENG 124/2 v IND 121", c.short); assertEquals("England 124/2 batting, India 121", c.line1); assertEquals("", c.line2)
        assertEquals("UAE", LiveCards.abbr("United Arab Emirates")); assertEquals("MI", LiveCards.abbr("Mumbai Indians")); assertEquals("NAM", LiveCards.abbr("Namibia"))
    }
    @Test fun `venue names ride on the card for photographs`() {
        val events = mapOf("9999" to listOf(ev("India v Australia", EventState.PRE, now + 5 * h, listOf("India", "Australia"), venue = "Wankhede Stadium")))
        val c = build(inputs(cricketEvents = events)).first { it.sport == "cricket" }
        assertEquals("Wankhede Stadium", c.venue)
        val race = Race("Mexico City Grand Prix", 19, "Autodromo Hermanos Rodriguez", "Mexico City", "Mexico", 19.4, -99.1, now + 3 * h)
        assertEquals("Autodromo Hermanos Rodriguez", build(inputs(f1Next = race)).first { it.sport == "f1" }.venue)
        assertEquals("Sepang International Circuit", build(inputs()).first { it.sport == "motogp" }.venue)
        val j = LiveCards.toJson(listOf(c.copy(image = "https://img"))).getJSONObject(0); assertEquals("https://img", j.getString("image")); assertTrue(j.has("teams"))
    }
    @Test fun `cards order live then next then result then none`() {
        val race = Race("Mexico City Grand Prix", 19, "c", "Mexico City", "Mexico", 1.0, 2.0, now + 3 * h)
        val tennis = listOf(ev("US Open · Men's Singles", EventState.POST, now - 3 * h, listOf("A", "B"), round = "Final"))
        val cards = build(inputs(cricketLive = listOf(india), f1Next = race, f1Sessions = listOf(ev("Mexico City Grand Prix · Race", EventState.PRE, now + 3 * h, emptyList())), tennis = tennis), at = now + 40L * 24 * h * 0)
        assertEquals(listOf("cricket", "f1", "motogp", "tennis"), cards.map { it.sport })
        assertEquals(listOf(CardState.LIVE, CardState.NEXT, CardState.NEXT, CardState.RESULT), cards.map { it.state })
    }
    @Test fun `toJson stays compact`() {
        val cards = build(inputs(cricketLive = listOf(india), tennis = listOf(ev("US Open · Men's Singles", EventState.POST, now - h, listOf("A", "B"), round = "Final"))))
        assertTrue(LiveCards.toJson(cards).toString().length < 3000)
    }
    @Test fun `a live match carries its competition and format`() {
        val score = CricinfoLive.parseTitle("India 94/6 * v United Arab Emirates 97", "http://www.cricinfo.com/ci/engine/match/1559204.html?CMP=OTC-RSS ")!!
        val inp = LiveInputs(listOf(score), emptyMap(), null, emptyList(), emptyList(), emptyList(), emptyList(), country = "IE", cricketSeries = mapOf("1559204" to Series("Hong Kong International Cricket Sixes", "Sixes")))
        val c = build(inp, at = 1_793_500_000_000L).first { it.sport == "cricket" }
        org.junit.Assert.assertEquals("Hong Kong International Cricket Sixes", c.competition); org.junit.Assert.assertEquals("Sixes", c.format)
        val j = LiveCards.toJson(listOf(c)).getJSONObject(0); org.junit.Assert.assertEquals("Sixes", j.getString("format"))
    }

    @Test fun `only loved sports get a card and no loves give no cards`() {
        val race = Race("Mexico City Grand Prix", 19, "c", "Mexico City", "Mexico", 1.0, 2.0, now + 3 * h)
        assertEquals(listOf("f1"), build(inputs(cricketLive = listOf(india), f1Next = race), prefs(Love("formula-1", emptyList()))).map { it.sport })
        assertEquals(emptyList<LiveCard>(), build(inputs(cricketLive = listOf(india), f1Next = race), SportPrefs.of(emptyList(), catalog)))
    }
    @Test fun `a living country with no hand-off data gives cards without an app`() {
        val race = Race("Mexico City Grand Prix", 19, "c", "Mexico City", "Mexico", 1.0, 2.0, now + 3 * h)
        val cards = build(inputs(cricketLive = listOf(india), f1Next = race, motogpNews = listOf(RssItem("x", "l", now, null, null))).copy(country = "NG"))
        assertEquals(listOf("cricket", "f1", "motogp", "tennis"), cards.map { it.sport }.distinct())
        assertTrue(cards.all { it.handoffPkg == null && it.handoffLabel == "" })
        assertTrue(build(inputs(cricketLive = listOf(india)).copy(country = "")).all { it.handoffPkg == null })
    }
    @Test fun `cricket with no team chosen follows any live match`() {
        val other = CricketScore("South Africa", "12/0", "Australia", "", true, false, "l")
        assertEquals(listOf("South Africa v Australia"), build(inputs(cricketLive = listOf(other)), prefs(Love("cricket", emptyList()))).map { it.title })
        assertTrue(build(inputs(cricketLive = listOf(other))).none { it.title == "South Africa v Australia" })   // India only
    }
    @Test fun `tennis with no players chosen keeps slams and finals only`() {
        val m = listOf(ev("Challenger Bergamo · Men's Singles", EventState.IN, now - h, listOf("Sumit Nagal", "C"), round = "1st Round"),
            ev("US Open · Men's Singles", EventState.PRE, now + 2 * h, listOf("A", "B"), round = "2nd Round"))
        assertEquals(listOf("US Open · Men's Singles"), LiveCards.tennisPick(m, prefs(Love("tennis", emptyList())).tennisRule(config.tennisRounds), now).map { it.name })
    }
    @Test fun `a football love gives a card from its leagues, narrowed to the chosen club, handed off by country and league`() {
        val events = mapOf("soccer/eng.1" to listOf(ev("Tottenham v Liverpool", EventState.PRE, now + 2 * h, listOf("Tottenham Hotspur", "Liverpool")),
            ev("Arsenal v Chelsea", EventState.PRE, now + 5 * h, listOf("Arsenal", "Chelsea"), venue = "Emirates Stadium")))
        val de = Handoffs.parse("""{"DE":{"soccer/eng.1":{"pkg":"de.sky.go","label":"Sky Go"}}}""")
        val c = build(inputs().copy(espnEvents = events, country = "DE"), prefs(Love("football", listOf("arsenal"))), h = de).single()
        assertEquals("football", c.sport); assertEquals("Arsenal v Chelsea", c.title); assertEquals(CardState.NEXT, c.state); assertEquals("in 5 h 0 m", c.line1)
        assertEquals("de.sky.go", c.handoffPkg); assertEquals("Emirates Stadium", c.venue)
        assertEquals("Tottenham Hotspur v Liverpool", build(inputs().copy(espnEvents = events), prefs(Love("football", emptyList()))).single().title)
        val quiet = build(inputs().copy(espnEvents = emptyMap()), prefs(Love("football", listOf("arsenal")))).single()
        assertEquals(CardState.NONE, quiet.state); assertEquals("Football", quiet.title); assertNull(quiet.handoffPkg)
    }
    @Test fun `loves list the scoreboards they need`() {
        val p = prefs(Love("football", listOf("arsenal")), Love("tennis", listOf("atp")), Love("cricket", emptyList()))
        assertEquals(setOf("cricket", "tennis", "football"), p.sports)
        assertEquals(listOf("soccer/eng.1"), p.espnLeagues)
        assertEquals(listOf("tennis/atp"), p.tennisLeagues)
    }

    @Test fun `a cricket match is at home when the venue is in a cricket lover's home country`() {
        val events = mapOf("9999" to listOf(ev("India v Australia", EventState.IN, now - h, listOf("India", "Australia"), venue = "Wankhede Stadium, Mumbai")))
        val score = CricketScore("India", "120/2", "Australia", "", true, false, "l")
        val atHome = build(inputs(cricketLive = listOf(score), cricketEvents = events)).first()
        assertEquals("com.discoveryplus.tv.fire", atHome.handoffPkg)   // cricket.home in IE
        val nigerian = prefs(Love("cricket", listOf("india-cricket")), home = "NG")
        val away = build(inputs(cricketLive = listOf(score), cricketEvents = events), nigerian).first()
        assertNull(away.handoffPkg); assertEquals("Check SonyLIV or discovery+", away.handoffLabel)
        assertEquals(18.9389, away.lat!!, 1e-4)   // the venue still places the card
    }
    @Test fun `a chosen club narrows only its own league`() {
        val events = mapOf(
            "soccer/eng.1" to listOf(ev("Tottenham v Liverpool", EventState.PRE, now + h, listOf("Tottenham Hotspur", "Liverpool")), ev("Arsenal v Chelsea", EventState.PRE, now + 5 * h, listOf("Arsenal", "Chelsea"))),
            "soccer/ger.1" to listOf(ev("Bayern v Dortmund", EventState.PRE, now + 3 * h, listOf("Bayern Munich", "Borussia Dortmund"))))
        val p = prefs(Love("football", listOf("arsenal", "bundesliga")))
        assertEquals(listOf("soccer/eng.1", "soccer/ger.1"), p.espnLeagues)
        assertEquals("Bayern Munich v Borussia Dortmund", build(inputs().copy(espnEvents = events), p).single().title)
        assertEquals("Arsenal v Chelsea", build(inputs().copy(espnEvents = events - "soccer/ger.1"), p).single().title)
    }
    @Test fun `a match that has not kicked off shows no score, though ESPN sends 0`() {
        val pre = EspnScoreboard.events(fixture("espn_soccer_eng1.json")).filter { it.state == EventState.PRE }
        val c = build(inputs().copy(espnEvents = mapOf("soccer/eng.1" to pre)), prefs(Love("football", emptyList()))).single()
        assertEquals(CardState.NEXT, c.state)
        assertTrue(c.short, c.short.none { it.isDigit() }); assertTrue(c.teams.all { it.score.isEmpty() })
    }
}
