package app.orbittv.web

/**
 * The pages are served from a private https origin answered from the APK's assets, not from file://.
 * YouTube embeds refuse to play without a real origin (Error 153).
 */
object WebOrigin {
    const val HOST = "orbit.local"

    fun pageUrl(name: String): String = "https://$HOST/web/$name.html"

    /** The bundled world data the pages read (setup); they live at the asset root for the native side and are served at /web/data/. */
    private val DATA = setOf("countries.json", "languages.json", "interests.json")

    /** The asset path for a request to our host under /web/, or null for anything else (other hosts, other folders, "..") */
    fun assetPath(host: String?, path: String?): String? {
        if (host != HOST || path == null || !path.startsWith("/web/") || path.contains("..")) return null
        path.removePrefix("/web/data/").takeIf { it in DATA }?.let { return it }
        return path.removePrefix("/")
    }

    /** The top frame never leaves our origin, so the bridge is never under a foreign document. */
    fun allowsTopNavigation(url: String): Boolean = url.startsWith("https://$HOST/")

    /**
     * TMDB's CDN sometimes omits CORS headers, so the page cannot measure an image's brightness. The app fetches it instead,
     * same-origin, at /img?u=<url>; only https://image.tmdb.org/ is allowed, no redirects, raster images only.
     */
    private val IMAGE_HOSTS = listOf("https://image.tmdb.org/")
    fun imageProxyTarget(host: String?, path: String?, u: String?): String? =
        if (host == HOST && path == "/img" && u != null && IMAGE_HOSTS.any { u.startsWith(it) } && !u.contains("..")) u else null
    /** Raster images only (no SVG or HTML can ever be served from our origin). */
    fun imageTypeAllowed(contentType: String?): Boolean = contentType?.substringBefore(';')?.trim()?.lowercase() in setOf("image/jpeg", "image/png", "image/webp")

    /** App artwork is served by the activity at /app-art/<package>.png; [version] changes when the picture does. */
    fun appArtUrl(pkg: String, version: Int): String = "https://$HOST/app-art/$pkg.png?v=$version"
    private val ART_PATH = Regex("^/app-art/([A-Za-z0-9_.]+)\\.png$")
    fun appArtPackage(host: String?, path: String?): String? =
        if (host != HOST || path == null || path.contains("..")) null else ART_PATH.find(path)?.groupValues?.get(1)

    fun mime(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "html" -> "text/html"; "js" -> "application/javascript"; "css" -> "text/css"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
        "ttf" -> "font/ttf"; "m4a" -> "audio/mp4"; "json" -> "application/json"; "svg" -> "image/svg+xml"; "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
