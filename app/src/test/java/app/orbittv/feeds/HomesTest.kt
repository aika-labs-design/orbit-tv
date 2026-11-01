package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomesTest {
    private val countries = World.countries(File("src/main/assets/countries.json").readText())
    private val languages = World.languages(File("src/main/assets/languages.json").readText())
    private fun profile(id: String, cc: String, places: List<Place>, langs: List<String>, living: Living) = Profile(id, "Ada", Home(cc, places), langs, living, emptyList(), 0L)
    private val berlin = Living("Berlin", 52.52, 13.41, "Europe/Berlin", "DE")
    private val lagos = Place("Lagos", 6.45, 3.39, "Africa/Lagos")

    @Test fun `a nigeria profile gives a pack named after its home, main language and places`() {
        val fest = listOf(Festival("2026-06-12", "Democracy Day", "Democracy Day"))
        val p = Homes.fromProfile(profile("p-ada", "NG", listOf(lagos, Place("Ibadan", 7.38, 3.91, "Africa/Lagos")), listOf("yo", "en"), berlin), countries, languages, fest)
        assertEquals("p-ada", p.id); assertEquals("Nigeria", p.label); assertEquals("Yoruba", p.langName)
        assertEquals("yoruba", p.radioLanguage); assertEquals("yo", p.tmdbLang)
        assertEquals(listOf("Lagos", "Ibadan"), p.places.map { it.name })
        assertEquals(listOf("yo", "en"), p.queries.map { it.lang }); assertEquals("Lagos OR Ibadan", p.queries[0].q)
        assertEquals(fest, p.festivals)
        // the page reads the same shape it read from homes.json
        assertEquals("Nigeria", p.raw.getString("label")); assertEquals("Yoruba", p.raw.getString("langName"))
        assertEquals("Ẹ kú àárọ̀", p.raw.getJSONObject("greetings").getString("morning"))
        assertEquals("Lagos", p.raw.getJSONArray("places").getJSONObject(0).getString("name"))
        assertEquals("Democracy Day", p.raw.getJSONArray("festivals").getJSONObject(0).getString("name"))
        assertTrue(p.raw.getString("accent").startsWith("#"))
    }

    @Test fun `a pidgin-first home asks news in the next language Google knows`() {
        val p = Homes.fromProfile(profile("p-t", "NG", listOf(lagos), listOf("pcm", "en", "yo"), berlin), countries, languages)
        assertEquals("pcm", p.tmdbLang); assertEquals("Nigerian Pidgin", p.langName)
        assertEquals(listOf("en", "yo"), p.queries.map { it.lang })
    }

    @Test fun `channel tag is the language where a curated list exists, otherwise the country`() {
        val ravi = profile("p-ravi", "IN", listOf(Place("Mysuru", 12.30, 76.64, "Asia/Kolkata")), listOf("kn", "en"), berlin)
        assertEquals("Kannada", Homes.fromProfile(ravi, countries, languages, curatedChannels = true).channelTag)
        assertEquals("Nigeria", Homes.fromProfile(profile("p-ada", "NG", listOf(lagos), listOf("yo"), berlin), countries, languages).channelTag)
    }

    @Test fun `a language with no greeting gives none rather than a wrong one`() {
        val p = Homes.fromProfile(profile("p-is", "IS", listOf(Place("Reykjavik", 64.14, -21.94, "Atlantic/Reykjavik")), listOf("is"), berlin), countries, languages)
        assertEquals(0, p.raw.getJSONObject("greetings").length())
        assertEquals("Iceland", p.label)
    }

    @Test fun `homes json is gone and no native code path names Ireland or a fixed news edition`() {
        assertFalse(File("src/main/assets/homes.json").exists())
        val hits = File("src/main/java").walk().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (!l.trimStart().let { it.startsWith("*") || it.startsWith("/") } && (l.contains("Ireland") || l.contains("\"IE\"") || l.contains("gl=IN"))) "${f.name}:${i + 1}" else null } }.toList()
        assertEquals(emptyList<String>(), hits)
    }
}
