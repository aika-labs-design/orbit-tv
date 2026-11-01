package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class Station(val name: String, val url: String, val codec: String, val bitrate: Int, val country: String = "")

/** Radio stations by language from the Radio Browser community directory (free, keyless), most voted first. */
object RadioBrowser {
    // UNKNOWN covers the All India Radio HLS relays; the player's watchdog drops any that do not answer.
    private val PLAYABLE = setOf("MP3", "AAC", "AAC+", "UNKNOWN")

    fun url(language: String) = "https://de1.api.radio-browser.info/json/stations/bylanguage/" + URLEncoder.encode(language, "UTF-8") + "?hidebroken=true&order=votes&reverse=true&limit=30"

    /** Stations for a music tag, optionally in one language (null for any). */
    fun tagUrl(tag: String, language: String?) = "https://de1.api.radio-browser.info/json/stations/search?tag=" + URLEncoder.encode(tag, "UTF-8") +
        (if (language != null) "&language=" + URLEncoder.encode(language, "UTF-8") else "") + "&hidebroken=true&order=votes&reverse=true&limit=30"

    fun parse(json: String, max: Int = 8): List<Station> = runCatching {
        val a = JSONArray(json); val seen = HashSet<String>()
        (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.optInt("lastcheckok") == 1 && it.optString("url_resolved").startsWith("https://") && it.optString("codec").uppercase() in PLAYABLE }
            .map { Station(it.optString("name").trim(), it.getString("url_resolved"), it.optString("codec").uppercase(), it.optInt("bitrate"), it.optString("countrycode")) }
            .filter { it.name.isNotBlank() && seen.add(it.url) }
            .take(max)
    }.getOrDefault(emptyList())

    fun toJson(s: List<Station>): JSONArray = JSONArray().also { a -> s.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) } }
}
