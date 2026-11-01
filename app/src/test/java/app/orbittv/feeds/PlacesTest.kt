package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    @Test fun `a wikipedia summary gives the venue photograph`() {
        val img = Places.fromWiki(fixture("wiki_chinnaswamy.json"))!!; assertTrue(img.contains("Chinnaswamy")); assertTrue(img.contains("/1280px-"))
        assertNull(Places.fromWiki(fixture("wiki_missing.json"))); assertNull(Places.fromWiki("junk"))
    }
    @Test fun `venue names become wikipedia page urls`() {
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/M._Chinnaswamy_Stadium", Places.url("M. Chinnaswamy Stadium"))
        assertEquals("https://en.wikipedia.org/api/rest_v1/page/summary/Aut%C3%B3dromo_Hermanos_Rodr%C3%ADguez", Places.url("Autódromo Hermanos Rodríguez"))
    }
}
