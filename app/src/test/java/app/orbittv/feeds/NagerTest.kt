package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NagerTest {
    @Test fun `url names the year and the country`() {
        assertEquals("https://date.nager.at/api/v3/PublicHolidays/2026/NG", Nager.url(2026, "NG"))
    }

    @Test fun `nigerian holidays parse in date order with english and local names`() {
        val f = Nager.parse(fixture("nager_ng_2026.json"))!!
        assertEquals(10, f.size)
        assertEquals(f.map { it.date }.sorted(), f.map { it.date })
        assertTrue(f.any { it.date == "2026-06-12" && it.name == "Democracy Day" && it.local == "Democracy Day" })
    }

    @Test fun `regional holidays are left out and duplicates removed`() {
        val json = """[{"date":"2026-10-03","localName":"Tag der Deutschen Einheit","name":"German Unity Day","global":true,"counties":null},
            {"date":"2026-10-31","localName":"Reformationstag","name":"Reformation Day","global":false,"counties":["DE-BB"]},
            {"date":"2026-10-03","localName":"Tag der Deutschen Einheit","name":"German Unity Day","global":true,"counties":null}]"""
        assertEquals(listOf(Festival("2026-10-03", "German Unity Day", "Tag der Deutschen Einheit")), Nager.parse(json))
    }

    @Test fun `a country nager does not cover answers with no body and has no holidays, junk is unreadable`() {
        assertEquals(emptyList<Festival>(), Nager.parse(""))
        assertNull(Nager.parse("<html>busy</html>"))
    }

    @Test fun `the bundled india calendar gives national days plus the days of the person's languages, through 2027`() {
        val book = FestivalBook.parse(java.io.File("src/main/assets/festivals/IN.json").readText())
        val kn = book.forLanguages(listOf("kn", "en"))
        assertTrue(kn.any { it.date == "2026-11-01" && it.name == "Kannada Rajyotsava" && it.local == "ಕನ್ನಡ ರಾಜ್ಯೋತ್ಸವ" })
        assertTrue(kn.any { it.date == "2027-01-26" && it.name == "Republic Day" })
        assertTrue(kn.none { it.name == "Vinayaka Chavithi" })
        assertEquals(kn.map { it.date }.sorted(), kn.map { it.date })
        val te = book.forLanguages(listOf("te"))
        assertTrue(te.any { it.name == "Vinayaka Chavithi" }); assertTrue(te.none { it.name == "Kannada Rajyotsava" })
        // a day both languages keep appears once, in the main language's script
        assertEquals(listOf("ದೀಪಾವಳಿ"), book.forLanguages(listOf("kn", "te")).filter { it.date == "2026-11-08" }.map { it.local })
        assertTrue(book.forLanguages(emptyList()).maxOf { it.date } >= "2027-12-01")
    }
}
