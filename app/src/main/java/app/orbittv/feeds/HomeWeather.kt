package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class PlaceWeather(val name: String, val tempC: Int, val desc: String, val isDay: Boolean, val wet: Boolean)

/** Current weather at home from Open-Meteo (free, keyless). One place answers with an object, several with an array. */
object HomeWeather {
    // Same words as web/sky.js CODES.
    private val CODES = mapOf(0 to "clear", 1 to "mostly clear", 2 to "partly cloudy", 3 to "overcast", 45 to "fog", 48 to "fog", 51 to "drizzle", 53 to "drizzle", 55 to "drizzle",
        61 to "rain", 63 to "rain", 65 to "heavy rain", 71 to "snow", 73 to "snow", 75 to "snow", 80 to "showers", 81 to "showers", 82 to "heavy showers", 95 to "thunder", 96 to "thunder", 99 to "thunder")

    fun describe(code: Int): String = CODES[code] ?: "cloudy"

    fun url(places: List<HomePlace>): String =
        "https://api.open-meteo.com/v1/forecast?latitude=" + places.joinToString(",") { it.lat.toString() } +
            "&longitude=" + places.joinToString(",") { it.lon.toString() } + "&current=temperature_2m,weather_code,is_day&timezone=auto"

    fun parse(json: String, places: List<HomePlace>): List<PlaceWeather> = runCatching {
        val t = json.trim()
        val items = if (t.startsWith("[")) JSONArray(t).let { a -> List(a.length()) { a.getJSONObject(it) } } else listOf(JSONObject(t))
        items.zip(places).mapNotNull { (o, p) ->
            val c = o.optJSONObject("current") ?: return@mapNotNull null
            val code = c.optInt("weather_code", -1)
            PlaceWeather(p.name, c.getDouble("temperature_2m").roundToInt(), CODES[code] ?: "cloudy", c.optInt("is_day") == 1, code in 51..67 || code in 80..82 || code in 95..99)
        }
    }.getOrDefault(emptyList())

    fun toJson(w: List<PlaceWeather>): JSONArray = JSONArray().also { a ->
        w.forEach { a.put(JSONObject().put("name", it.name).put("tempC", it.tempC).put("desc", it.desc).put("isDay", it.isDay).put("wet", it.wet)) }
    }
}
