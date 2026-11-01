package app.orbittv.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWeatherTest {
    private val ann = listOf(HomePlace("Mysuru", 12.2958, 76.6394), HomePlace("Mangaluru", 12.9141, 74.856))
    private val bo = listOf(HomePlace("Vijayawada", 16.5062, 80.648))

    @Test fun `several places come back as an array in order`() {
        val w = HomeWeather.parse(fixture("meteo_multi.json"), ann)
        assertEquals(listOf("Mysuru", "Mangaluru"), w.map { it.name })
        assertTrue(w.all { it.tempC in -10..50 && it.desc.isNotBlank() })
    }

    @Test fun `a single place comes back as an object`() {
        val w = HomeWeather.parse(fixture("meteo_single.json"), bo)
        assertEquals(listOf("Vijayawada"), w.map { it.name })
    }

    @Test fun `rain codes are wet and clear is not`() {
        val rain = """{"current":{"temperature_2m":23.6,"weather_code":61,"is_day":0}}"""
        val r = HomeWeather.parse(rain, bo).single()
        assertTrue(r.wet); assertEquals("rain", r.desc); assertEquals(24, r.tempC); assertFalse(r.isDay)
        val clear = HomeWeather.parse("""{"current":{"temperature_2m":30,"weather_code":0,"is_day":1}}""", bo).single()
        assertFalse(clear.wet); assertEquals("clear", clear.desc)
    }

    @Test fun `junk gives nothing`() {
        assertEquals(emptyList<PlaceWeather>(), HomeWeather.parse("<html>busy</html>", ann))
        assertEquals(emptyList<PlaceWeather>(), HomeWeather.parse("""{"error":true,"reason":"x"}""", ann))
    }

    @Test fun `url asks for all places, each in its own time zone`() {
        val u = HomeWeather.url(ann)
        assertTrue(u, u.contains("latitude=12.2958,12.9141") && u.contains("longitude=76.6394,74.856") && u.contains("timezone=auto") && !u.contains("Kolkata"))
    }
}
