package app.orbittv.dream

import android.annotation.SuppressLint
import android.service.dreams.DreamService
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.orbittv.feeds.FeedStore
import app.orbittv.web.AssetServer
import app.orbittv.web.WebBridge
import app.orbittv.web.WebOrigin
import org.json.JSONObject

/**
 * The screensaver: the Earth-orbit world in dream mode (clock, sky line, wellbeing line, live sport line, slow drift).
 * Fire OS has no UI to pick a third-party screensaver; it is selected once over ADB:
 *   settings put secure screensaver_enabled 1
 *   settings put secure screensaver_components app.orbittv/.dream.OrbitDreamService
 */
class OrbitDreamService : DreamService() {
    private var web: WebView? = null
    private val server by lazy { AssetServer(assets) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true; isScreenBright = false
        val w = WebView(this).apply {
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowFileAccessFromFileURLs = false; settings.allowUniversalAccessFromFileURLs = false
            setBackgroundColor(0xFF03101A.toInt())
            addJavascriptInterface(WebBridge(onLaunch = {}, onReady = { post { pushFeeds() } }), "Android")
            // Served from the same private https origin as the pages, answered from assets; the top frame never leaves it.
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest) = server.intercept(req)
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean = req.isForMainFrame && !WebOrigin.allowsTopNavigation(req.url.toString())
            }
            loadUrl(WebOrigin.pageUrl("index") + "#dream")
        }
        web = w; setContentView(w)
        Log.i(TAG, "dreaming")
    }

    private fun pushFeeds() {
        val json = FeedStore.pageJson(this) ?: return
        web?.evaluateJavascript("orbit.setFeeds && orbit.setFeeds(JSON.parse(${JSONObject.quote(json)}));", null)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        web?.let { it.loadUrl("about:blank"); (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }; web = null
        Log.i(TAG, "dream stopped, web view released")
    }

    override fun onDetachedFromWindow() { web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }; web = null; super.onDetachedFromWindow() }

    companion object { const val TAG = "OrbitDream" }
}
