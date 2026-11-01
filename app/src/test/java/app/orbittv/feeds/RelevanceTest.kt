package app.orbittv.feeds

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RelevanceTest {
    // QA: a custom love "Day of the Dead" showed "Monster High Catrina... Stock Photo Adobe Stock"
    @Test fun `stock photo libraries and product listings are not news`() {
        assertTrue(Relevance.listing("Monster High Catrina Day of the Dead doll Stock Photo", "Adobe Stock"))
        listOf("Shutterstock", "Getty Images", "Alamy", "iStock", "Dreamstime", "123RF", "Adobe Stock").forEach { assertTrue(it, Relevance.listing("Day of the Dead parade", it)) }
        assertTrue(Relevance.listing("Catrina skull mask, royalty-free stock image", "Pinterest"))
        assertTrue(Relevance.listing("Amazon.com: Day of the Dead Catrina Figurine : Home & Kitchen", "Amazon.com"))
        assertTrue(Relevance.listing("Catrina doll for sale | eBay", "eBay"))
        assertFalse(Relevance.listing("Day of the Dead parade fills Mexico City streets", "Reuters"))
        assertFalse(Relevance.listing("Stock markets rally as Mexico City celebrates Day of the Dead", "Bloomberg"))
    }

    private fun catalogue() = Interests.parse(File("src/main/assets/interests.json").readText())
    private fun terms(id: String) = catalogue().first { it.id == id }.terms

    @Test fun `a car advert in a jazz search is dropped and the Village Vanguard set is kept`() {
        val jazz = terms("jazz")
        val items = JSONObject(fixture("rel_jazz_videos.json")).getJSONArray("items")
        val kept = (0 until items.length()).map { items.getJSONObject(it).getJSONObject("snippet") }.filter { sn ->
            val tags = sn.optJSONArray("tags")?.let { a -> (0 until a.length()).joinToString(" ") { a.getString(it) } } ?: ""
            Relevance.keep(sn.getString("title") + " " + sn.optString("description") + " " + tags, jazz)
        }.map { it.getString("title") }
        assertTrue(kept.contains("Live at the Village Vanguard"))
        assertTrue(kept.contains("John Coltrane Quartet, Live in Antibes 1965"))
        assertFalse(kept.any { it.contains("Civic") })
    }

    @Test fun `clickbait is caught`() {
        assertTrue(Relevance.clickbait("YOU WON'T BELIEVE this jollof!!!"))
        assertTrue(Relevance.clickbait("You won’t believe this jollof"))
        assertTrue(Relevance.clickbait("Chef reacts to street food"))
        assertTrue(Relevance.clickbait("Kitchen prank gone wrong"))
        assertTrue(Relevance.clickbait("Chef exposed"))
        assertTrue(Relevance.clickbait("Best goals #shorts"))
        assertTrue(Relevance.clickbait("Goals of the week (Shorts)"))
        assertTrue(Relevance.clickbait("SIX NATIONS FULL HIGHLIGHTS"))
        assertTrue(Relevance.clickbait("What? Why? How? Really"))
        assertTrue(Relevance.clickbait("Poison in the pot"))     // the Journeys list is included
        assertTrue(Relevance.clickbait("The worst scam in town"))
        assertFalse(Relevance.clickbait("Live at the Village Vanguard"))
        assertFalse(Relevance.clickbait("Who won the final?"))
        assertFalse(Relevance.clickbait("NBA finals: Game 7 highlights"))
        assertFalse(Relevance.clickbait("F1 2026 season review"))
        assertFalse(Relevance.clickbait("Why we say shortsighted"))   // shorts only as a word
        assertFalse(Relevance.clickbait(""))
    }

    @Test fun `whole words only, so legitimate titles are not flagged`() {
        assertFalse(Relevance.clickbait("Scampi linguine recipe"))
        assertFalse(Relevance.clickbait("Astrophotography exposure tips"))
        assertFalse(Relevance.clickbait("Cargo shorts for men: how to style"))
        assertFalse(Relevance.clickbait("ATP Challenger Pune"))
        assertFalse(Relevance.clickbait("Lancashire Challengers vs Yorkshire highlights"))
        assertFalse(Relevance.clickbait("Magnus Carlsen chess challenge"))
        assertFalse(Relevance.clickbait("Fakery in art: a history"))
        assertFalse(Relevance.clickbait("Poisonwood Bible review"))
        assertTrue(Relevance.clickbait("YOU WON'T BELIEVE this jollof!!!"))
        assertTrue(Relevance.clickbait("PRANK gone wrong"))
        assertTrue(Relevance.clickbait("This is a scam"))
        assertTrue(Relevance.clickbait("Fake chef caught"))
        assertTrue(Relevance.clickbait("Poisoned by a pie"))
        assertTrue(Relevance.clickbait("24 hour challenge in the kitchen"))
        assertTrue(Relevance.clickbait("Dance moves #challenge"))
        assertTrue(Relevance.clickbait("Best goals #Shorts"))
        assertTrue(Relevance.clickbait("Shorts"))
    }

    @Test fun `reaction is only flagged in the reaction-video sense`() {
        assertFalse(Relevance.clickbait("Chemical reaction rates explained"))
        assertFalse(Relevance.clickbait("Nuclear reaction in the sun"))
        assertFalse(Relevance.clickbait("Allergic reaction: what to do"))
        assertTrue(Relevance.clickbait("Rapper REACTS to Coltrane"))
        assertTrue(Relevance.clickbait("Drummer reacting to a solo"))
        assertTrue(Relevance.clickbait("Reaction video: first listen"))
        assertTrue(Relevance.clickbait("My reaction to the finale"))
        assertTrue(Relevance.clickbait("First time hearing Fela Kuti reaction"))
    }

    @Test fun `football titles that name only clubs, players or cups are kept`() {
        val football = terms("football")
        for (t in listOf("Arsenal vs Chelsea highlights", "Real Madrid vs Barcelona", "Messi hat-trick", "FA Cup final", "Haaland scores again", "Bafana Bafana v Nigeria",
            "Super Eagles squad named", "Liverpool beat Manchester United", "Bayern v PSG: Champions League"))
            assertTrue(t, Relevance.keep(t, football))
        for (t in listOf("NFL football week 5", "American football draft picks", "Chiefs v Eagles Super Bowl", "Euros and dollars: exchange rates", "Cricket World Cup final"))
            assertFalse(t, Relevance.keep(t, football))
    }

    @Test fun `a flagged word is allowed when it is part of the love's own terms`() {
        val boxing = terms("boxing"); val mma = terms("mma"); val ipl = Interests.parse(File("src/main/assets/interests.json").readText()).first { it.id == "cricket" }.refine!!.options.first { it.id == "ipl" }.terms
        assertTrue(Relevance.clickbait("Usyk vs Fury fight highlights"))                 // no love given
        assertFalse(Relevance.clickbait("Usyk vs Fury fight highlights", boxing))
        assertFalse(Relevance.clickbait("UFC 300 full fight", mma))
        assertTrue(Relevance.clickbait("Kitchen fight goes wrong", terms("cooking")))
        assertTrue(Relevance.clickbait("SHOCKING scam fight", boxing))                    // only the love's own words are exempt
        assertFalse(Relevance.clickbait("Titans fight back to win", listOf("fight back")))
        assertTrue(ipl.isNotEmpty())
    }

    @Test fun `no love's own queries read as clickbait for that love`() {
        val bad = catalogue().flatMap { i -> listOfNotNull(i.news?.query, i.videos?.query).filter { Relevance.clickbait(it, i.terms) }.map { "${i.id}: $it" } }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun `generic words no longer let unrelated items through`() {
        assertFalse(Relevance.keep("Bruno Mars live in concert", terms("space")))
        assertFalse(Relevance.keep("Fish and chips on the pier", terms("technology")))
        assertFalse(Relevance.keep("Clay court final in Paris", terms("ceramics")))
        assertFalse(Relevance.keep("Martial arts for kids", terms("art")))
        assertFalse(Relevance.keep("How to fit a contact lens", terms("photography")))
        assertFalse(Relevance.keep("NFL football week 5", terms("football")))
        assertFalse(Relevance.keep("Rap battle night at the pub", terms("celtic-folk")))
        assertFalse(Relevance.keep("A folk tale for kids", terms("celtic-folk")))
        assertTrue(Relevance.keep("Soccer: Arsenal win again", terms("football")))
        assertTrue(Relevance.keep("Mars rover finds new rock", terms("space")))
        assertTrue(Relevance.keep("A new camera lens for portraits", terms("photography")))
    }

    @Test fun `a cooking news item that never names cooking or a food term is dropped`() {
        val west = terms("west-african-cooking")
        val items = Rss.parse(fixture("rel_cooking_news.xml"))
        assertEquals(6, items.size)
        val kept = items.filter { Relevance.keep(it.title + " " + (it.description ?: ""), west) && !Relevance.clickbait(it.title) }.map { it.title }
        assertEquals(3, kept.size)
        assertTrue(kept.any { it.startsWith("Lagos restaurant serves jollof") })
        assertTrue(kept.any { it.startsWith("How to make egusi soup") })
        assertTrue(kept.any { it.startsWith("Senegal's thieboudienne") })
        assertFalse(kept.any { it.contains("Naira") || it.contains("Election") || it.contains("Cocoa") })
    }

    @Test fun `matching is by whole word, ignoring case and accents`() {
        assertTrue(Relevance.keep("The Café Racer story", listOf("cafe racer")))
        assertTrue(Relevance.keep("CAFE RACER weekend", listOf("Café Racer")))
        assertTrue(Relevance.keep("Arsenal beat Spurs", listOf("arsenal")))
        assertFalse(Relevance.keep("Venice Beach sunset", listOf("beach volleyball")))
        assertFalse(Relevance.keep("Parsenal discount", listOf("arsenal")))     // inside a word
        assertFalse(Relevance.keep("Chessboard coffee table", listOf("chess")))
        assertTrue(Relevance.keep("Best chefs of 2026", listOf("chef")))        // plain plural
        assertTrue(Relevance.keep("Watches and clocks", listOf("watch")))
        assertTrue(Relevance.keep("R&B and soul night", listOf("r&b")))
        assertTrue(Relevance.keep("Premier League: Arsenal v Spurs", listOf("zzz", "premier league")))
    }

    @Test fun `nothing matches no terms or blank terms`() {
        assertFalse(Relevance.keep("anything at all", emptyList()))
        assertFalse(Relevance.keep("anything at all", listOf("", "  ")))
        assertFalse(Relevance.keep("", listOf("jazz")))
    }
}
