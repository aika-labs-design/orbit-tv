package app.orbittv.web

import android.util.Log
import android.webkit.JavascriptInterface
import app.orbittv.catalog.TvApp
import org.json.JSONArray
import org.json.JSONObject

/** The page's window into the native shell. Everything heavy is pre-built by the activity and pushed with evaluateJavascript. */
class WebBridge(
    private val onLaunch: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onOpen: (String, String?) -> Unit = { _, _ -> },
    private val onPage: (String) -> Unit = {},
    private val onSystem: (String) -> Unit = {},
    private val onRadio: (String) -> Unit = {},
    private val gate: BridgeGate? = null,
    private val onToast: (String) -> Unit = {},
    private val onJourney: (String) -> Unit = {},
    private val onSaveProfiles: (String) -> Unit = {},
) {
    /** Side effects (and ready, which pushes the house's data) need the page token: embedded frames (YouTube) see the bridge but not the token. */
    private fun ok(token: String?, what: String): Boolean = (gate == null || gate.allows(token)).also { if (!it) Log.w(TAG, "bridge: refused $what without the page token") }
    @JavascriptInterface fun log(message: String) { Log.i(TAG, message) }
    @JavascriptInterface fun launch(packageName: String, token: String?) { if (ok(token, "launch")) onLaunch(packageName) }
    @JavascriptInterface fun ready(token: String?) { if (ok(token, "ready")) { readyRefused = false; onReady() } else readyRefused = true }
    /** A ready that came before the token (or without it) is remembered and replayed once the shell has injected the token. */
    @Volatile private var readyRefused = false
    fun tokenInjected() { if (readyRefused) { readyRefused = false; onReady() } }
    /** A new page: a refusal from the page before it is not replayed. */
    fun pageStarted() { readyRefused = false }
    /** Open a URL in a specific app (a YouTube live stream in the YouTube app, for example). */
    @JavascriptInterface fun open(url: String, packageName: String?, token: String?) { if (ok(token, "open")) onOpen(url, packageName) }
    /** System row actions: audio, dream, amazon. */
    @JavascriptInterface fun system(action: String, token: String?) { if (ok(token, "system")) onSystem(action) }
    /** Home Mode radio: play a stream URL, or stop with an empty string. State comes back through orbit.radioState. */
    @JavascriptInterface fun radio(url: String, token: String?) { if (ok(token, "radio")) onRadio(url) }
    /** Journeys: places, weather and footage, answered later through orbit.journeyResult (the YouTube key stays native). */
    @JavascriptInterface fun journey(request: String, token: String?) { if (ok(token, "journey")) onJourney(request) }
    /** Setup: store the profiles (JSON array) on the TV; the page gets them back through orbit.setProfiles. */
    @JavascriptInterface fun saveProfiles(json: String, token: String?) { if (ok(token, "saveProfiles")) onSaveProfiles(json) }
    /** A system toast, visible over the app that is being launched. */
    @JavascriptInterface fun toast(message: String) { onToast(message.take(120)) }
    /** Switch the page the shell hosts (hero, wall). */
    @JavascriptInterface fun page(name: String, token: String?) { if (ok(token, "page")) onPage(name) }

    companion object {
        const val TAG = "OrbitWeb"

        /** JSON for the page: one entry per app with its artwork URL (served by package name, see [WebOrigin.appArtUrl]) and usage facts. */
        fun appsJson(apps: List<TvApp>, brands: List<Int>, art: List<ByteArray>, usage: Map<String, AppUsage>): String {
            val arr = JSONArray()
            apps.forEachIndexed { i, app ->
                val u = usage[app.packageName]
                arr.put(JSONObject()
                    .put("pkg", app.packageName).put("label", app.label)
                    .put("brand", String.format("#%06X", brands[i] and 0xFFFFFF))
                    .put("art", WebOrigin.appArtUrl(app.packageName, art[i].contentHashCode()))
                    .put("minutesWeek", u?.minutesWeek ?: 0).put("minutesToday", u?.minutesToday ?: 0).put("lastUsedMs", u?.lastUsedMs ?: 0L)
                    .put("hours", JSONArray((u?.hours ?: IntArray(24)).toList()))
                    .put("days", JSONArray((u?.days ?: IntArray(7)).toList()))
                    .put("streak", u?.streak ?: 0))
            }
            return arr.toString()
        }
    }
}
