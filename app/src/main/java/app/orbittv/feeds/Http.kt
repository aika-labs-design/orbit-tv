package app.orbittv.feeds

import java.net.HttpURLConnection
import java.net.URL

/** Plain GET with a browser-like agent. Null on any failure or non-2xx; bodies capped at 2 MB. */
object Http {
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** Streams a large text body line by line; the callback returns false to stop early. True when the request succeeded. */
    fun stream(url: String, timeoutMs: Int = 20_000, onLine: (String) -> Boolean): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url, timeoutMs)
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> for (l in lines) if (!onLine(l)) break }
            true
        } catch (e: Exception) { false } finally { conn?.disconnect() }
    }
    private fun open(url: String, timeoutMs: Int) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs; readTimeout = timeoutMs; instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Orbit/0.1 (" + app.orbittv.BuildConfig.CONTACT + ")")
        setRequestProperty("Accept-Language", "en-GB,en;q=0.8")
        if (url.contains("youtube.com")) setRequestProperty("Cookie", "SOCS=CAI") // skip the EU consent interstitial
        setRequestProperty("Accept", "application/json, application/rss+xml, application/xml, text/xml, */*")
    }
    fun get(url: String, timeoutMs: Int = 10_000): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url, timeoutMs)
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var n: Int
                while (ins.read(buf).also { n = it } > 0) { out.write(buf, 0, n); if (out.size() > MAX_BYTES) break }
                String(out.toByteArray(), Charsets.UTF_8)
            }
        } catch (e: Exception) { null } finally { conn?.disconnect() }
    }
}
