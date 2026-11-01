package app.orbittv.feeds

import org.json.JSONObject
import java.net.URLEncoder

/** Photographs of venues (stadiums, circuits) from Wikipedia's REST summary, keyless and free. */
object Places {
    fun url(name: String) = "https://en.wikipedia.org/api/rest_v1/page/summary/" + URLEncoder.encode(name.trim().replace(' ', '_'), "UTF-8").replace("+", "_").replace("%2C", ",").replace("%28", "(").replace("%29", ")")
    fun fromWiki(json: String): String? = try {
        val o = JSONObject(json)
        if (o.optInt("status") == 404 || o.optString("type", "standard") != "standard") null else Tonight.wikiImage(json)
    } catch (e: Exception) { null }
}
