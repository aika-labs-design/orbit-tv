package app.orbittv.feeds

import org.json.JSONArray
import org.json.JSONObject

data class Channel(val id: String, val name: String, val tag: String, val color: String, val handle: String?, val pkg: String?)
data class ChannelState(val id: String, val live: Boolean, val videoId: String?, val title: String?, val thumb: String?, val resolvedMs: Long)

/**
 * Live channels from home: YouTube channels resolved to their current live video, plus installed apps. Curated per country in
 * assets/channels/<CC>.json; a country without a file gets channels from one YouTube live search a day ([fromLiveSearch]).
 */
object Channels {
    const val YOUTUBE_PKG = "com.amazon.firetv.youtube"
    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    private val LIVE_NOW = Regex("\"isLiveNow\":(true|false)")
    private val IS_LIVE = Regex("\"isLive\":(true|false)")
    private val DETAILS_TITLE = Regex("\"videoDetails\":\\{\"videoId\":\"[^\"]+\",\"title\":\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val OG_TITLE = Regex("<meta property=\"og:title\" content=\"([^\"]*)\"")
    private val HTML_TITLE = Regex("<title>([^<]*)</title>")

    fun parse(json: String): List<Channel> = JSONArray(json).let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Channel(it.getString("id"), it.getString("name"), it.optString("tag"), it.optString("color", "#223"), it.optString("handle").ifBlank { null }, it.optString("pkg").ifBlank { null }) } } }

    /** (live, videoId, title) from a YouTube channel /live page; junk gives (false, null, null). */
    fun resolve(html: String): Triple<Boolean, String?, String?> {
        val id = VIDEO_ID.find(html)?.groupValues?.get(1) ?: return Triple(false, null, null)
        val live = (LIVE_NOW.find(html) ?: IS_LIVE.find(html))?.groupValues?.get(1) == "true"
        val title = DETAILS_TITLE.find(html)?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\u0026", "&")
            ?: OG_TITLE.find(html)?.groupValues?.get(1)?.let { Rss.decodeEntities(it) }
            ?: HTML_TITLE.find(html)?.groupValues?.get(1)?.takeIf { it.isNotBlank() && it != "YouTube" }?.removeSuffix(" - YouTube")
        return Triple(live, id, title)
    }

    /**
     * A channel's /live page, read line by line only until everything [resolve] prefers is in (video id, isLiveNow, the
     * videoDetails title), so the rest of a page of up to 2 MB is never downloaded. Null when the request failed.
     */
    fun readLive(url: String, stream: (String, (String) -> Boolean) -> Boolean): String? {
        val out = StringBuilder(); var id = false; var liveNow = false; var title = false
        val ok = stream(url) { line ->
            if (out.isNotEmpty()) out.append('\n')
            out.append(line)
            id = id || VIDEO_ID.containsMatchIn(line); liveNow = liveNow || LIVE_NOW.containsMatchIn(line); title = title || DETAILS_TITLE.containsMatchIn(line)
            !(id && liveNow && title) && out.length < MAX_PAGE
        }
        return if (ok) out.toString() else null
    }
    private const val MAX_PAGE = 2 * 1024 * 1024

    /** Live streams from a YouTube search answer, one per channel, as channels already resolved to their live video. Junk gives nothing. */
    fun fromLiveSearch(json: String, tag: String, nowMs: Long = 0L): List<Pair<Channel, ChannelState>> = runCatching {
        val items = JSONObject(json).getJSONArray("items")
        (0 until items.length()).map { items.getJSONObject(it) }.mapNotNull { o ->
            val sn = o.optJSONObject("snippet") ?: return@mapNotNull null
            val vid = o.optJSONObject("id")?.optString("videoId")?.ifBlank { null } ?: return@mapNotNull null
            val ch = sn.optString("channelId").ifBlank { return@mapNotNull null }
            if (sn.optString("liveBroadcastContent") != "live") return@mapNotNull null
            val c = Channel("yt:$ch", Rss.decodeEntities(sn.optString("channelTitle")).ifBlank { return@mapNotNull null }, tag, "#223", null, null)
            c to ChannelState(c.id, true, vid, Rss.decodeEntities(sn.optString("title")).ifBlank { null }, thumb(vid), nowMs)
        }.distinctBy { it.first.id }
    }.getOrDefault(emptyList())

    fun thumb(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    fun liveUrl(handle: String) = "https://www.youtube.com/@$handle/live"

    /** [countries]: the home countries each channel is listed for, so a page shows only the current person's. */
    fun toJson(channels: List<Channel>, states: Map<String, ChannelState>, countries: Map<String, List<String>> = emptyMap()): JSONArray = JSONArray().also { arr ->
        channels.forEach { c ->
            val s = states[c.id]
            val yt = c.pkg == null && (c.handle != null || s?.videoId != null)
            val url = if (yt) (if (s?.live == true && s.videoId != null) "https://www.youtube.com/watch?v=${s.videoId}" else c.handle?.let(::liveUrl)) else null
            arr.put(JSONObject().put("id", c.id).put("name", c.name).put("tag", c.tag).put("color", c.color).put("pkg", if (yt) YOUTUBE_PKG else c.pkg)
                .put("handle", c.handle ?: JSONObject.NULL).put("url", url ?: JSONObject.NULL).put("live", s?.live ?: false)
                .put("title", s?.title ?: JSONObject.NULL).put("thumb", (if (s?.live == true) s.thumb else null) ?: JSONObject.NULL).put("resolvedMs", s?.resolvedMs ?: 0L).put("countries", JSONArray(countries[c.id] ?: emptyList<String>())))
        }
    }
}
