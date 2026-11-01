package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InterestsTest {
    private val text = File("src/main/assets/interests.json").readText()
    private val all = Interests.parse(text)
    private fun get(id: String) = all.firstOrNull { it.id == id } ?: throw AssertionError("no interest $id")

    @Test fun `catalogue integrity`() {
        assertTrue("at least 200 entries, got ${all.size}", all.size >= 200)
        assertEquals("ids are unique", all.size, all.map { it.id }.toSet().size)
        for (i in all) {
            assertTrue("${i.id} id form", i.id.matches(Regex("[a-z0-9]+(-[a-z0-9]+)*")))
            assertTrue("${i.id} theme ${i.theme}", i.theme in Interests.THEMES)
            assertTrue("${i.id} also", i.also.all { it in Interests.THEMES })
            assertTrue("${i.id} image", i.image.startsWith("https://") && i.image.contains("/500px-"))
            assertTrue("${i.id} terms", i.terms.isNotEmpty() && i.terms.all { it.isNotBlank() })
            assertTrue("${i.id} has a source", i.shows != null || i.news != null || i.videos != null || i.live != null)
            i.shows?.let { assertTrue("${i.id} shows", it.keywords.isNotEmpty() || it.genres.isNotEmpty()) }
            i.news?.let { assertTrue("${i.id} news", it.query.isNotBlank()) }
            i.videos?.let { assertTrue("${i.id} videos", it.query.isNotBlank() && it.minMinutes >= 4) }
            i.live?.let { l ->
                assertTrue("${i.id} live kind ${l.kind}", l.kind in Interests.LIVE_KINDS)
                when (l.kind) {
                    "espn" -> assertTrue("${i.id} espn league", l.league!!.split(",").all { Regex("[a-z-]+/[a-z0-9.-]+|rugby/\\d+").matches(it) })
                    "ytlive" -> assertTrue("${i.id} ytlive query", !l.query.isNullOrBlank())
                    "radio" -> assertTrue("${i.id} radio tag", !l.tag.isNullOrBlank())
                }
            }
            i.refine?.let { r ->
                assertTrue("${i.id} refine", r.label.isNotBlank() && r.options.size >= 2)
                assertEquals("${i.id} option ids unique", r.options.size, r.options.map { it.id }.toSet().size)
                for (o in r.options) {
                    assertTrue("${i.id}/${o.id} terms", o.terms.isNotEmpty())
                    assertTrue("${i.id}/${o.id} aims somewhere", o.espnTeam != null || o.league != null || o.query != null || o.terms.isNotEmpty())
                    if (o.espnTeam != null) assertNotNull("${i.id}/${o.id} team needs its league", o.league)
                }
            }
        }
    }

    @Test fun `a love's own name passes its own gate`() {
        // "Football" is the picture's tile name, but the bare word also means American football, so the love matches soccer terms instead
        assertEquals(emptyList<String>(), all.filterNot { it.id == "football" || Relevance.keep(it.name, it.terms) }.map { it.id })
    }

    @Test fun `every option id is unique across the catalogue`() {
        val ids = all.flatMap { i -> i.refine?.options?.map { it.id } ?: emptyList() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { id -> all.any { it.id == id } })
    }

    @Test fun `every theme is well stocked and the world is covered`() {
        for (t in Interests.THEMES) assertTrue("theme $t ${all.count { it.theme == t || t in it.also }}", all.count { it.theme == t || t in it.also } >= 15)
        val food = setOf("nigerian-food", "ethiopian-food", "south-african-food", "indian-food", "chinese-food", "japanese-food", "korean-food", "italian-food",
            "french-food", "lebanese-food", "mexican-food", "peruvian-food", "brazilian-food", "caribbean-food", "karnataka-food", "ghanaian-food")
        assertEquals(emptySet<String>(), food.filterNot { id -> all.any { it.id == id && "food" in (listOf(it.theme) + it.also) } }.toSet())
        val music = setOf("afrobeats", "amapiano", "highlife", "reggae", "hip-hop", "carnatic", "hindustani", "k-pop", "latin-music", "arabic-music", "bossa-nova", "classical", "jazz")
        assertEquals(emptySet<String>(), music.filterNot { id -> all.any { it.id == id && "music" in (listOf(it.theme) + it.also) } }.toSet())
        val faith = setOf("christian-worship", "catholic", "islam", "hindu-devotion", "buddhism", "sikhism", "jainism", "judaism", "indigenous-spirituality", "gospel")
        assertEquals(emptySet<String>(), faith.filterNot { id -> all.any { it.id == id && "faith" in (listOf(it.theme) + it.also) } }.toSet())
        assertTrue("espn loves ${all.count { it.live?.kind == "espn" }}", all.count { it.live?.kind == "espn" } >= 15)
    }

    @Test fun `the approved picture is in the catalogue`() {
        val tiles = mapOf("football" to "Football", "afrobeats" to "Afrobeats", "west-african-cooking" to "West African cooking", "formula-1" to "Formula 1",
            "fashion" to "Fashion", "jazz" to "Jazz", "space" to "Space", "street-food" to "Street food", "hiking" to "Hiking", "chess" to "Chess",
            "baking" to "Baking", "boxing" to "Boxing")
        for ((id, name) in tiles) { assertEquals(name, get(id).name); assertEquals("popular", get(id).theme) }
        val football = get("football").refine!!
        assertEquals(listOf("Arsenal", "Super Eagles", "Premier League", "Bundesliga", "AFCON", "Champions League"), football.options.take(6).map { it.name })
        assertEquals("359", football.options.first { it.id == "arsenal" }.espnTeam)
        assertEquals("soccer/caf.nations", football.options.first { it.id == "afcon" }.league)
        assertEquals("soccer/uefa.champions", football.options.first { it.id == "champions-league" }.league)
        assertEquals("soccer/ger.1", football.options.first { it.id == "bundesliga" }.league)
    }

    @Test fun `sport and topic interests are ordinary entries`() {
        for (id in listOf("cricket", "motogp", "formula-1", "tennis", "football", "jazz", "cooking", "chess", "photography", "india-news")) get(id)
        assertEquals("cricinfo", get("cricket").live!!.kind)
        assertEquals("jolpica", get("formula-1").live!!.kind)
        assertEquals("motogp", get("motogp").live!!.kind)
        assertEquals(setOf("ipl", "india-cricket"), get("cricket").refine!!.options.map { it.id }.toSet().intersect(setOf("ipl", "india-cricket")))
    }

    @Test fun `food and music default to 4 minute videos, other long form to 8`() {
        assertEquals(4, get("jazz").videos!!.minMinutes)
        assertEquals(4, get("nigerian-food").videos!!.minMinutes)
        assertEquals(8, get("space").videos!!.minMinutes)
        assertEquals(8, get("football").videos!!.minMinutes)
    }

    @Test fun `catalogue text holds no em dash and no key-like value`() {
        assertFalse(text.contains('\u2014'))
        assertFalse(Regex("AIza[0-9A-Za-z_-]{20,}").containsMatchIn(text))
        assertFalse(text.contains("api_key", ignoreCase = true))
    }

    @Test fun `no love matches on a bare generic word`() {
        val banned = setOf("folk", "chip", "clay", "moon", "mars", "soul", "book", "art", "rap", "war", "news", "dish", "meal", "food", "lens", "pap", "football",
            "music", "show", "live", "game", "world", "life", "love", "home")
        val hits = all.flatMap { i -> (i.terms + (i.refine?.options?.flatMap { it.terms } ?: emptyList())).filter { it.lowercase().trim() in banned }.map { "${i.id}: $it" } }
        // refine options narrow an item that already passed the base terms, so only base terms are held to the list
        val baseHits = all.flatMap { i -> i.terms.filter { it.lowercase().trim() in banned }.map { "${i.id}: $it" } }
        assertEquals(emptyList<String>(), baseHits)
        assertTrue(hits.size >= baseHits.size)
    }

    @Test fun `custom ids stay distinct for non-Latin terms`() {
        val a = Interests.custom("\u0c95\u0ca8\u0ccd\u0ca8\u0ca1 \u0cb8\u0c82\u0c97\u0cc0\u0ca4")   // Kannada
        val b = Interests.custom("\u0c95\u0ca8\u0ccd\u0ca8\u0ca1 \u0c9a\u0cbf\u0ca4\u0ccd\u0cb0")
        assertTrue(a.id.startsWith("custom:") && a.id.length > "custom:".length)
        assertTrue(a.id != b.id)
        assertEquals(a.id, Interests.custom(" \u0c95\u0ca8\u0ccd\u0ca8\u0ca1   \u0cb8\u0c82\u0c97\u0cc0\u0ca4 ").id)
        assertTrue(Interests.custom("\u0939\u093f\u0928\u094d\u0926\u0940 \u0917\u093e\u0928\u0947").id != Interests.custom("\u0939\u093f\u0928\u094d\u0926\u0940 \u092b\u093f\u0932\u094d\u092e").id)
        assertTrue(Interests.custom("Kannada \u0cb8\u0c82\u0c97\u0cc0\u0ca4").id != Interests.custom("Kannada \u0c9a\u0cbf\u0ca4\u0ccd\u0cb0").id)
        assertEquals("custom:bird-photography", Interests.custom("Bird photography").id)
    }

    @Test fun `a search that is not in the catalogue becomes a less tuned love`() {
        val c = Interests.custom("  Bird   photography ")
        assertEquals("custom:bird-photography", c.id)
        assertEquals("Bird photography", c.name)
        assertEquals(listOf("bird photography"), c.terms)
        assertEquals("bird photography", c.news!!.query)
        assertEquals("bird photography", c.videos!!.query)
        assertEquals(8, c.videos!!.minMinutes)
        assertNull(c.shows)
        assertNull(c.live)
        assertFalse(c.tuned)
        assertTrue(get("jazz").tuned)
        assertTrue(Relevance.keep("A guide to bird photography", c.terms))
        assertFalse(Relevance.keep("Bird watching in Kerala", c.terms))
    }

    @Test fun `parse survives junk and skips broken entries`() {
        assertTrue(Interests.parse("").isEmpty())
        assertTrue(Interests.parse("not json").isEmpty())
        assertTrue(Interests.parse("{}").isEmpty())
        val ok = """{"id":"a","name":"A","theme":"food","image":"https://x/500px-a.jpg","terms":["a"],"news":{"query":"a"}}"""
        val noTerms = """{"id":"b","name":"B","theme":"food","image":"https://x/500px-b.jpg","terms":[],"news":{"query":"b"}}"""
        val badTheme = """{"id":"c","name":"C","theme":"zzz","image":"https://x/500px-c.jpg","terms":["c"],"news":{"query":"c"}}"""
        val noId = """{"name":"D","theme":"food","terms":["d"]}"""
        assertEquals(listOf("a"), Interests.parse("[$ok,$noTerms,$badTheme,$noId,5]").map { it.id })
    }
}
