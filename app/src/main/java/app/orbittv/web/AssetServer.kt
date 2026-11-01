package app.orbittv.web

import android.content.res.AssetManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Answers requests to the private https origin ([WebOrigin.HOST]) from the APK's assets, plus the same-origin TMDB image proxy.
 * Shared by the activity and the screensaver, so no WebView needs file:// access. The activity also serves app artwork ([appArt]).
 */
class AssetServer(private val assets: AssetManager, private val appArt: (String) -> ByteArray? = { null }) {
    fun intercept(req: WebResourceRequest): WebResourceResponse? {
        val url = req.url
        WebOrigin.imageProxyTarget(url.host, url.path, url.getQueryParameter("u"))?.let { return proxyImage(it) }
        if (url.host != WebOrigin.HOST) return null
        WebOrigin.appArtPackage(url.host, url.path)?.let { pkg -> return appArt(pkg)?.let { WebResourceResponse("image/png", null, ByteArrayInputStream(it)) } ?: notFound() }
        val path = WebOrigin.assetPath(url.host, url.path) ?: return notFound()
        return try { WebResourceResponse(WebOrigin.mime(path), "utf-8", assets.open(path)) } catch (e: Exception) { notFound() }
    }

    private fun proxyImage(target: String): WebResourceResponse = try {
        val c = (URL(target).openConnection() as HttpURLConnection).apply { connectTimeout = 3000; readTimeout = 3000; instanceFollowRedirects = false }
        if (c.responseCode != 200 || !WebOrigin.imageTypeAllowed(c.contentType)) { c.disconnect(); throw IOException("refused") }
        val headers = mapOf("Access-Control-Allow-Origin" to "*", "X-Content-Type-Options" to "nosniff")
        WebResourceResponse(c.contentType.substringBefore(';').trim(), null, 200, "OK", headers, c.inputStream)
    } catch (e: Exception) { notFound() }

    private fun notFound() = WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
