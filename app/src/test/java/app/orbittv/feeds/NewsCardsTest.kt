package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NewsCardsTest {
    private val topic = Topic("region", "Regional news", listOf("Karnataka"), emptyList())
    private val langs = World.languages(File("src/main/assets/languages.json").readText()).associateBy { it.code }
    private val countries = World.countries(File("src/main/assets/countries.json").readText())
    private val placeNames = PlaceNames.parse(File("src/main/assets/place-names.json").readText(), File("src/main/assets/web/aliases.json").readText())
    private fun home(cc: String, vararg places: Place) = placeNames.forHome(countries.first { it.code == cc }, places.map { HomePlace(it.name, it.lat, it.lon) })

    @Test fun `a regional fixture yields at most eight fresh deduplicated headlines without source suffix`() {
        val items = Rss.parse(fixture("gnews_karnataka.xml")); val now = items.maxOf { it.publishedMs } + 60_000
        val card = NewsCards.build(listOf(topic), mapOf("region" to items), now).single()
        assertEquals(8, card.headlines.size)
        assertFalse(card.headlines.any { it.title.endsWith(" - The Hindu") })
        assertTrue(card.headlines.all { it.source.isNotBlank() && !it.title.endsWith(" - " + it.source) })
        assertTrue(card.headlines.zipWithNext().all { (a, b) -> a.publishedMs >= b.publishedMs })
        assertEquals(card.headlines.size, card.headlines.map { it.title.lowercase() }.toSet().size)
    }
    // QA (Ana, Spanish first, home Mexico City): the home news hero was an English F1 story
    @Test fun `home news names the home place or country and follows the person's language order`() {
        val now = 1_793_500_000_000L; fun item(t: String, agoH: Int) = RssItem("$t - Src", "l$t", now - agoH * 3600_000L, "Src", null)
        val es = listOf(item("Receta de tacos al pastor", 1), item("Lluvias en la Ciudad de México", 5), item("México gana en Lima", 3))
        val en = listOf(item("Mexico City Grand Prix: Verstappen on pole", 0), item("Tokyo weather turns cold", 0))
        val card = NewsCards.home("home_p", "Mexico", listOf(es, en), listOf("Mexico City", "Mexico"), now)
        assertEquals(listOf("México gana en Lima", "Lluvias en la Ciudad de México", "Mexico City Grand Prix: Verstappen on pole"), card.headlines.map { it.title })
    }
    // TV check: the Spanish edition itself carried an English F1 story about Mexico City, still ahead of Spanish ones
    @Test fun `a headline goes with the language it is written in, not the edition it came from`() {
        val now = 1_793_500_000_000L; fun item(t: String, agoH: Int) = RssItem("$t - Src", "l$t", now - agoH * 3600_000L, "Src", null)
        val es = listOf(item("Antonelli on brink of history at Mexico City GP, needing only ninth-place finish", 0), item("Lluvias en la Ciudad de México dejan inundaciones en el sur", 4))
        val card = NewsCards.home("h", "Mexico", listOf(es, emptyList()), listOf("Mexico City", "Mexico"), now, languages = listOf(langs.getValue("es"), langs.getValue("en")))
        assertEquals(listOf("Lluvias en la Ciudad de México dejan inundaciones en el sur", "Antonelli on brink of history at Mexico City GP, needing only ninth-place finish"), card.headlines.map { it.title })
    }
    @Test fun `home news in a script the place names are not written in is kept as Google matched it`() {
        val now = 1_793_500_000_000L
        val te = listOf(RssItem("ఏపీలో కేంద్ర బృందాల వరుస పర్యటనలు - Oneindia Telugu", "l1", now - 3600_000L, "Oneindia Telugu", null), RssItem("Cricket scores today - X", "l2", now, "X", null))
        assertEquals(listOf("ఏపీలో కేంద్ర బృందాల వరుస పర్యటనలు"), NewsCards.home("h", "India", listOf(te), listOf("Vijayawada", "India"), now).headlines.map { it.title })
    }
    @Test fun `stale items are dropped and topics with nothing still appear empty`() {
        val now = 1_793_500_000_000L
        val items = listOf(RssItem("Old story - Source", "l", now - 30 * 3600_000L, "Source", null), RssItem("Fresh story - Source", "l", now - 3600_000L, "Source", null))
        val cards = NewsCards.build(listOf(topic, Topic("ai", "AI", listOf("ai"), emptyList())), mapOf("region" to items), now)
        assertEquals(2, cards.size)
        assertEquals(listOf("Fresh story"), cards[0].headlines.map { it.title })
        assertTrue(cards[1].headlines.isEmpty()); assertEquals("AI", cards[1].label)
    }
    @Test fun `source falls back to the link host and undated items sort last`() {
        val now = 1_793_500_000_000L
        val items = listOf(RssItem("Plain", "https://www.thehindu.com/x", 0L, null, null), RssItem("Dated story", "https://www.deccanherald.com/x", now - 60_000, null, null))
        val card = NewsCards.build(listOf(topic), mapOf("region" to items), now).single()
        assertEquals(listOf("Dated story", "Plain"), card.headlines.map { it.title })
        assertEquals("thehindu.com", card.headlines[1].source); assertEquals("deccanherald.com", card.headlines[0].source)
    }
    @Test fun `a headline with a dash but no source tag is left intact`() {
        val h = NewsCards.toHeadline(RssItem("Why 'motocross' Sepang wasn't fixed - and is bruising riders", "https://www.the-race.com/x", 1L, null, null))
        assertEquals("Why 'motocross' Sepang wasn't fixed - and is bruising riders", h.title); assertEquals("the-race.com", h.source)
    }
    @Test fun `a known source suffix is stripped even when it contains a dot`() {
        val h = NewsCards.toHeadline(RssItem("CBDT plans to buy land in Delhi, Pune, and Mumbai - BigInfo.in", "l", 1L, "BigInfo.in", null))
        assertEquals("CBDT plans to buy land in Delhi, Pune, and Mumbai", h.title); assertEquals("BigInfo.in", h.source)
    }
    @Test fun `toJson keeps topic id, label and headline fields`() {
        val now = 1_793_500_000_000L
        val card = NewsCards.build(listOf(topic), mapOf("region" to listOf(RssItem("A - S", "l", now, "S", null))), now)
        val j = NewsCards.toJson(card).getJSONObject(0)
        assertEquals("region", j.getString("id")); assertEquals("A", j.getJSONArray("headlines").getJSONObject(0).getString("title"))
    }

    // review: the gate read English exonyms only, so German, Brazilian and Indian homes lost their own-language news
    @Test fun `home names include the place's own names, its region and the country's names in its languages`() {
        val de = home("DE", Place("Munich", 48.14, 11.58, "Europe/Berlin"))
        assertTrue(de.containsAll(listOf("Munich", "München", "Bavaria", "Germany", "Deutschland")))
        assertTrue(home("BR", Place("Sao Paulo", -23.55, -46.64, "America/Sao_Paulo")).contains("Brasil"))
        val bom = home("IN", Place("Mumbai", 19.08, 72.88, "Asia/Kolkata"))
        assertTrue(bom.containsAll(listOf("Bombay", "Maharashtra")))
        // a place not in the list takes the region of the nearest listed city of its country
        assertTrue(home("IN", Place("Thane", 19.2, 72.97, "Asia/Kolkata")).contains("Maharashtra"))
    }
    @Test fun `german, brazilian and mumbai headlines in their own words count as home news`() {
        val now = 1_793_500_000_000L; fun item(t: String, agoH: Int, d: String? = null) = RssItem("$t - Src", "l$t", now - agoH * 3600_000L, "Src", d)
        val de = listOf(item("Neue U-Bahn-Linie in München eröffnet", 1), item("Wetter in Deutschland wird kälter", 2), item("Bundesliga: Spieltag kompakt", 3, "Der FC Bayern gewinnt in München"))
        assertEquals(3, NewsCards.home("h", "Germany", listOf(de), home("DE", Place("Munich", 48.14, 11.58, "Europe/Berlin")), now, languages = listOf(langs.getValue("de"))).headlines.size)
        val br = listOf(item("Chuva forte atinge o Brasil", 1))
        assertEquals(1, NewsCards.home("h", "Brazil", listOf(br), home("BR", Place("Sao Paulo", -23.55, -46.64, "America/Sao_Paulo")), now).headlines.size)
        val bom = listOf(item("Bombay traffic eases as metro line opens", 1), item("Maharashtra cabinet meets today", 2))
        assertEquals(2, NewsCards.home("h", "India", listOf(bom), home("IN", Place("Mumbai", 19.08, 72.88, "Asia/Kolkata")), now).headlines.size)
    }
    @Test fun `with fewer than three named headlines the best ranked ones in the person's languages fill up to three`() {
        val now = 1_793_500_000_000L; fun item(t: String, agoH: Int) = RssItem("$t - Src", "l$t", now - agoH * 3600_000L, "Src", null)
        val es = listOf(item("Lluvias en la Ciudad de México", 3), item("El peso sube frente al dólar", 1), item("Receta de tacos al pastor", 2), item("Nuevo museo abre sus puertas", 4))
        val card = NewsCards.home("h", "Mexico", listOf(es), listOf("Mexico City", "Mexico", "México"), now, languages = listOf(langs.getValue("es")), countryScoped = true)
        assertEquals(listOf("Lluvias en la Ciudad de México", "El peso sube frente al dólar", "Receta de tacos al pastor"), card.headlines.map { it.title })
    }
}
