package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class GeoPlace(val name: String, val country: String, val admin1: String?, val lat: Double, val lon: Double, val tz: String, val population: Long, val countryCode: String = "")
data class Weather(val tempC: Int, val desc: String, val sunrise: String, val sunset: String)
data class Video(val id: String, val title: String, val channel: String, val minutes: Int, val live: Boolean, val thumb: String, val hd: Boolean, val views: Long)

/** Journeys sources: Open-Meteo (places, weather), Wikidata (native name), Wikipedia (one line), YouTube Data API (footage). */
object Journeys {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    fun geocodeUrl(q: String) = "https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=6&language=en"
    fun forecastUrl(lat: Double, lon: Double, tz: String) = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&daily=sunrise,sunset&timezone=${enc(tz)}&forecast_days=1"
    fun wikidataUrl(name: String) = "https://www.wikidata.org/w/api.php?action=wbgetentities&sites=enwiki&titles=${enc(name)}&props=claims&format=json"
    fun summaryUrl(name: String) = "https://en.wikipedia.org/api/rest_v1/page/summary/${enc(name.replace(' ', '_'))}"
    // strict safe search returns no live streams at all, so live cams use moderate; titles are filtered either way
    fun searchUrl(key: String, q: String, live: Boolean) = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=${enc(q)}&type=video&videoEmbeddable=true&maxResults=12" +
        (if (live) "&eventType=live&safeSearch=moderate" else "&safeSearch=strict") + "&key=$key"
    fun videosUrl(key: String, ids: List<String>) = "https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails,statistics,status&id=${ids.joinToString(",")}&key=$key"

    fun parseGeocode(json: String): List<GeoPlace> = runCatching {
        val a = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until a.length()).map { a.getJSONObject(it) }.map { o -> GeoPlace(o.getString("name"), o.optString("country"), o.optString("admin1").ifBlank { null }, o.getDouble("latitude"), o.getDouble("longitude"), o.optString("timezone", "UTC"), o.optLong("population"), o.optString("country_code")) }
    }.getOrDefault(emptyList())

    fun parseForecast(json: String): Weather? = runCatching {
        val o = JSONObject(json); val c = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        Weather(Math.round(c.getDouble("temperature_2m")).toInt(), HomeWeather.describe(c.optInt("weather_code", -1)),
            d.getJSONArray("sunrise").getString(0).substringAfter('T').take(5), d.getJSONArray("sunset").getString(0).substringAfter('T').take(5))
    }.getOrNull()

    fun parseNativeName(json: String): String? = runCatching {
        val e = JSONObject(json).getJSONObject("entities"); val ent = e.getJSONObject(e.keys().next())
        ent.getJSONObject("claims").optJSONArray("P1705")?.getJSONObject(0)?.getJSONObject("mainsnak")?.getJSONObject("datavalue")?.getJSONObject("value")?.getString("text")
    }.getOrNull()

    fun parseSummary(json: String): String? = runCatching { JSONObject(json).optString("description").ifBlank { null } }.getOrNull()

    /** A 1280 px version of the article photograph (Wikipedia's "original" can be 3840 px, too heavy for this TV). */
    fun parseSummaryImage(json: String): String? = runCatching {
        val src = JSONObject(json).optJSONObject("thumbnail")?.optString("source")?.ifBlank { null } ?: return null
        src.replace(Regex("/\\d+px-"), "/1280px-")
    }.getOrNull()

    fun parseSearch(json: String): List<String> = runCatching {
        val a = JSONObject(json).getJSONArray("items"); (0 until a.length()).mapNotNull { a.getJSONObject(it).optJSONObject("id")?.optString("videoId")?.ifBlank { null } }
    }.getOrDefault(emptyList())

    private val BAD = Regex("poison|expos|challenge|prank|scam|worst|fake|fight|shocking|gone wrong", RegexOption.IGNORE_CASE)
    fun clean(title: String) = !BAD.containsMatchIn(title)
    private val DUR = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
    fun minutes(iso: String): Int = DUR.matchEntire(iso)?.groupValues?.let { (it[1].toIntOrNull() ?: 0) * 60 + (it[2].toIntOrNull() ?: 0) } ?: 0

    /** Embeddable, clean titles, long enough (live streams skip the length rule); HD first, then the most watched. */
    fun parseVideos(json: String, minMinutes: Int, live: Boolean): List<Video> = runCatching {
        val a = JSONObject(json).getJSONArray("items")
        (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { o ->
            val sn = o.getJSONObject("snippet"); val cd = o.optJSONObject("contentDetails"); val st = o.optJSONObject("status")
            val isLive = sn.optString("liveBroadcastContent") == "live"
            val mins = minutes(cd?.optString("duration") ?: "")
            if (st?.optBoolean("embeddable", true) == false || !clean(sn.optString("title"))) return@mapNotNull null
            if (live != isLive) return@mapNotNull null
            if (!live && mins < minMinutes) return@mapNotNull null
            val thumbs = sn.optJSONObject("thumbnails"); val thumb = (thumbs?.optJSONObject("maxres") ?: thumbs?.optJSONObject("high") ?: thumbs?.optJSONObject("medium"))?.optString("url") ?: ""
            Video(o.getString("id"), sn.optString("title"), sn.optString("channelTitle"), mins, isLive, thumb, cd?.optString("definition") == "hd", o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: 0L)
        }.sortedWith(compareByDescending<Video> { it.hd }.thenByDescending { it.views })
    }.getOrDefault(emptyList())

    fun toJson(v: List<Video>): JSONArray = JSONArray().also { a -> v.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel).put("minutes", it.minutes).put("live", it.live).put("thumb", it.thumb)) } }
}

/**
 * Google's free YouTube allowance: 80 searches per Pacific-time day (the quota resets at midnight Pacific), counted in j_quota.json.
 * Journeys and loves share it. Loves may only take up to [LOVES_LIMIT], so Journeys always keeps at least 20 of the day's 80.
 * One lock for every instance, because two workers search at once.
 */
class YouTubeQuota(private val store: FeedStore, private val now: () -> Long = System::currentTimeMillis) {
    private fun day() = java.time.Instant.ofEpochMilli(now()).atZone(java.time.ZoneId.of("America/Los_Angeles")).toLocalDate().toEpochDay()
    private fun load() = runCatching { JSONObject(store.load("j_quota.json") ?: "{}") }.getOrDefault(JSONObject())
    private fun save(o: JSONObject) = store.save("j_quota.json", o.toString())

    /** True and counted when fewer than [limit] searches have been used today (a key that failed rests separately, see [resting]). */
    fun take(limit: Int = DAY_LIMIT): Boolean = synchronized(LOCK) {
        val day = day(); val o = load()
        val used = if (o.optLong("day") == day) o.optInt("count") else 0
        if (used >= limit) return false
        save(o.put("day", day).put("count", used + 1)); true
    }
    /** Whether a search for [key] failed within the hour: it waits instead of spending another search. */
    fun resting(key: String): Boolean = synchronized(LOCK) { now() - (load().optJSONObject("fails")?.optLong(id(key), Long.MIN_VALUE / 2) ?: Long.MIN_VALUE / 2) in 0 until HOUR }
    /** A search for [key] failed after its quota was taken: [key] rests an hour, so a failing key spends at most one search an hour. */
    fun failed(key: String) = synchronized(LOCK) { val o = load(); save(o.put("fails", recent(o, now()).put(id(key), now()))) }
    fun succeeded(key: String) = synchronized(LOCK) { val o = load(); save(o.put("fails", recent(o, now()).apply { remove(id(key)) })) }

    private fun id(key: String) = Integer.toHexString(key.hashCode())
    private fun recent(o: JSONObject, t: Long) = JSONObject().also { out -> o.optJSONObject("fails")?.let { f -> f.keys().forEach { k -> val at = f.optLong(k); if (t - at in 0 until HOUR) out.put(k, at) } } }
    companion object { const val DAY_LIMIT = 80; const val LOVES_LIMIT = 60; private const val HOUR = 3_600_000L; private val LOCK = Any() }
}

/**
 * Answers page requests for Journeys on a worker thread: places, weather, native names, footage. Cached on disk (footage 7 days,
 * live cams 1 hour, weather 30 minutes, names 30 days) and limited to 80 YouTube searches per Pacific-time day ([YouTubeQuota]). The key never leaves here.
 */
class JourneyService(private val fetch: (String) -> String?, private val store: FeedStore, private val key: String, quota: YouTubeQuota? = null, private val now: () -> Long = System::currentTimeMillis) {
    private val quota = quota ?: YouTubeQuota(store, now)
    private val DAY = 86_400_000L
    private fun cached(name: String, maxAgeMs: Long): String? = store.savedAt(name)?.takeIf { now() - it < maxAgeMs }?.let { store.load(name) }
    private fun slug(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(60) + "_" + Integer.toHexString(s.hashCode())

    /** How long each kind of cache file is read for; the sweep deletes a file a day past that. The quota file is never swept. */
    private val maxAge = mapOf("j_geo_" to 30 * DAY, "j_wx_" to 30 * 60_000L, "j_native_" to 30 * DAY, "j_line_" to 30 * DAY, "j_vid_" to 7 * DAY)
    @Volatile private var sweptMs = Long.MIN_VALUE / 2
    /** Deletes cache files older than their max age plus a day. Returns how many went. */
    fun sweep(): Int = store.names().count { name ->
        val age = maxAge.entries.firstOrNull { name.startsWith(it.key) }?.value ?: return@count false
        val at = store.savedAt(name) ?: return@count false
        now() - at > age + DAY && store.delete(name)
    }

    fun handle(request: String): String {
        if (now() - sweptMs >= DAY) { sweptMs = now(); runCatching { sweep() } }   // once a day: places looked up once would otherwise stay for good
        val r = runCatching { JSONObject(request) }.getOrElse { return JSONObject().put("ok", false).put("error", "bad").toString() }
        val out = JSONObject().put("id", r.optString("id")).put("op", r.optString("op"))
        return runCatching {
            when (r.optString("op")) {
                "geocode" -> {
                    val q = r.optString("query").trim(); val name = "j_geo_${slug(q)}.json"
                    val body = cached(name, 30 * DAY) ?: fetch(Journeys.geocodeUrl(q))?.also { if (Journeys.parseGeocode(it).isNotEmpty()) store.save(name, it) }
                    val res = JSONArray().also { a -> Journeys.parseGeocode(body ?: "").forEach { g -> a.put(JSONObject().put("name", g.name).put("country", g.country).put("cc", g.countryCode).put("admin1", g.admin1 ?: JSONObject.NULL).put("lat", g.lat).put("lon", g.lon).put("tz", g.tz)) } }
                    out.put("ok", true).put("results", res)
                }
                "place" -> {
                    val nm = r.optString("name"); val lat = r.getDouble("lat"); val lon = r.getDouble("lon"); val tz = r.optString("tz", "UTC")
                    val wName = "j_wx_${slug(nm)}.json"
                    val wx = (cached(wName, 30 * 60_000L) ?: fetch(Journeys.forecastUrl(lat, lon, tz))?.also { if (Journeys.parseForecast(it) != null) store.save(wName, it) })?.let { Journeys.parseForecast(it) }
                    val nName = "j_native_${slug(nm)}.json"
                    val native = (cached(nName, 30 * DAY) ?: fetch(Journeys.wikidataUrl(nm.substringBefore(',')))?.also { store.save(nName, it) })?.let { Journeys.parseNativeName(it) }
                    val sName = "j_line_${slug(nm)}.json"
                    val summary = cached(sName, 30 * DAY) ?: fetch(Journeys.summaryUrl(nm.substringBefore(',')))?.also { store.save(sName, it) }
                    val line = summary?.let { Journeys.parseSummary(it) }; val image = summary?.let { Journeys.parseSummaryImage(it) }
                    out.put("ok", true).put("place", JSONObject().put("name", nm).put("lat", lat).put("lon", lon).put("tz", tz).put("local", native ?: JSONObject.NULL).put("line", line ?: JSONObject.NULL).put("image", image ?: JSONObject.NULL)
                        .put("weather", wx?.let { JSONObject().put("tempC", it.tempC).put("desc", it.desc).put("sunrise", it.sunrise).put("sunset", it.sunset) } ?: JSONObject.NULL))
                }
                "videos" -> {
                    if (key.isBlank()) return out.put("ok", false).put("error", "nokey").toString()
                    val nm = r.optString("name"); val mode = r.optString("mode", "walk")
                    val live = mode == "live"; val min = mapOf("walk" to 8, "drive" to 8, "nature" to 8, "food" to 4, "festivals" to 4, "stays" to 4)[mode] ?: 0
                    val q = r.optString("query").ifBlank { mapOf("walk" to "$nm walking tour 4K", "drive" to "$nm driving tour 4K", "food" to "$nm street food", "festivals" to "$nm festival", "nature" to "$nm nature 4K drone", "stays" to "$nm luxury hotel tour", "live" to "$nm live cam")[mode] ?: nm }
                    val name = "j_vid_${slug("$nm|$mode")}.json"
                    val hit = cached(name, if (live) 3_600_000L else 7 * DAY)
                    val body = hit ?: run {
                        if (!quota.take()) return out.put("ok", false).put("error", "quota").toString()
                        val ids = fetch(Journeys.searchUrl(key, q, live))?.let { Journeys.parseSearch(it) } ?: return out.put("ok", false).put("error", "net").toString()
                        // a failed details call is a network error, not "no footage": nothing is cached, so the next try searches again
                        val v = if (ids.isEmpty()) emptyList() else fetch(Journeys.videosUrl(key, ids))?.let { Journeys.parseVideos(it, min, live) } ?: return out.put("ok", false).put("error", "net").toString()
                        Journeys.toJson(v).toString().also { store.save(name, it) }
                    }
                    out.put("ok", true).put("videos", JSONArray(body))
                }
                else -> out.put("ok", false).put("error", "op")
            }
            out.toString()
        }.getOrElse { out.put("ok", false).put("error", "fail").toString() }
    }
}
