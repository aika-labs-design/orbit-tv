package app.orbittv.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebOriginTest {
    @Test fun `pages load from the private https origin`() {
        assertEquals("https://orbit.local/web/stage.html", WebOrigin.pageUrl("stage"))
    }
    @Test fun `only web assets on our host are served`() {
        assertEquals("web/stage.html", WebOrigin.assetPath("orbit.local", "/web/stage.html"))
        assertEquals("web/homes/ka/dawn-01.jpg", WebOrigin.assetPath("orbit.local", "/web/homes/ka/dawn-01.jpg"))
        assertNull(WebOrigin.assetPath("www.youtube.com", "/web/stage.html"))
        assertNull(WebOrigin.assetPath("orbit.local", "/feeds.json"))
        assertNull(WebOrigin.assetPath("orbit.local", "/web/../homes.json"))
        assertNull(WebOrigin.assetPath("orbit.local", null))
    }
    @Test fun `the bundled world data is readable by the pages and nothing else beside it`() {
        assertEquals("countries.json", WebOrigin.assetPath("orbit.local", "/web/data/countries.json"))
        assertEquals("languages.json", WebOrigin.assetPath("orbit.local", "/web/data/languages.json"))
        assertEquals("interests.json", WebOrigin.assetPath("orbit.local", "/web/data/interests.json"))
        assertEquals("web/data/feeds.json", WebOrigin.assetPath("orbit.local", "/web/data/feeds.json"))
        assertNull(WebOrigin.assetPath("www.youtube.com", "/web/data/countries.json"))
    }
    @Test fun `mime types cover what the pages use`() {
        mapOf("a.html" to "text/html", "a.js" to "application/javascript", "a.css" to "text/css", "a.png" to "image/png", "a.jpg" to "image/jpeg",
            "a.ttf" to "font/ttf", "a.m4a" to "audio/mp4", "a.json" to "application/json", "a.bin" to "application/octet-stream")
            .forEach { (f, m) -> assertEquals(f, m, WebOrigin.mime(f)) }
    }
    @Test fun `the top frame may only navigate within our origin`() {
        assertTrue(WebOrigin.allowsTopNavigation("https://orbit.local/web/home.html"))
        assertFalse(WebOrigin.allowsTopNavigation("https://www.youtube.com/watch?v=x"))
        assertFalse(WebOrigin.allowsTopNavigation("https://orbit.local.evil.com/web/x.html"))
        assertFalse(WebOrigin.allowsTopNavigation("file:///android_asset/web/stage.html"))
    }
    @Test fun `encoded traversal is caught after decoding`() { assertNull(WebOrigin.assetPath("orbit.local", java.net.URLDecoder.decode("/web/%2e%2e/homes.json", "UTF-8"))) }
    @Test fun `the image proxy only fetches TMDB images`() {
        assertEquals("https://image.tmdb.org/t/p/w500/x.png", WebOrigin.imageProxyTarget("orbit.local", "/img", "https://image.tmdb.org/t/p/w500/x.png"))
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", "https://evil.example/x.png"))
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", "http://image.tmdb.org/t/p/w500/x.png"))
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", "https://image.tmdb.org.evil.example/x.png"))
        assertNull(WebOrigin.imageProxyTarget("www.youtube.com", "/img", "https://image.tmdb.org/t/p/w500/x.png"))
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", null))
    }
    @Test fun `the proxy serves TMDB images only, and only real image types`() {
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", "https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertNull(WebOrigin.imageProxyTarget("orbit.local", "/img", "https://upload.wikimedia.org/a.jpg"))
        assertTrue(WebOrigin.imageTypeAllowed("image/jpeg")); assertTrue(WebOrigin.imageTypeAllowed("image/png")); assertTrue(WebOrigin.imageTypeAllowed("image/webp; charset=binary"))
        assertFalse(WebOrigin.imageTypeAllowed("image/svg+xml")); assertFalse(WebOrigin.imageTypeAllowed("text/html")); assertFalse(WebOrigin.imageTypeAllowed(null))
    }

    @Test fun `app artwork is served by package name from our origin only`() {
        assertEquals("https://orbit.local/app-art/com.netflix.ninja.png?v=7", WebOrigin.appArtUrl("com.netflix.ninja", 7))
        assertEquals("com.netflix.ninja", WebOrigin.appArtPackage("orbit.local", "/app-art/com.netflix.ninja.png"))
        assertEquals("in.startv.hotstar_1", WebOrigin.appArtPackage("orbit.local", "/app-art/in.startv.hotstar_1.png"))
        assertNull(WebOrigin.appArtPackage("evil.example", "/app-art/com.netflix.ninja.png"))
        assertNull(WebOrigin.appArtPackage("orbit.local", "/app-art/../web/x.png"))
        assertNull(WebOrigin.appArtPackage("orbit.local", "/app-art/com.netflix.ninja.jpg"))
        assertNull(WebOrigin.appArtPackage("orbit.local", "/web/app-art/com.netflix.ninja.png"))
    }
}
