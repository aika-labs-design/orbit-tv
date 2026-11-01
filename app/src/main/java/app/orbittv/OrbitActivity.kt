package app.orbittv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import app.orbittv.catalog.AppCatalog
import app.orbittv.catalog.TileArtwork
import app.orbittv.catalog.TvApp
import app.orbittv.usage.UsageRanker
import app.orbittv.usage.UsageRepository
import app.orbittv.wake.WakePolicy
import app.orbittv.web.WebOrigin
import app.orbittv.wake.WakeService
import app.orbittv.audio.AudioEnhancer
import app.orbittv.audio.RadioPlayer
import app.orbittv.feeds.FeedStore
import app.orbittv.feeds.Profiles
import android.os.Build
import app.orbittv.web.UsageSummary
import app.orbittv.web.WebBridge
import org.json.JSONObject
import java.util.TimeZone

/**
 * Native shell for Orbit: hosts the web scene, feeds it the app catalogue and usage facts,
 * launches apps, and relays wake and return events. All visuals live in assets/web.
 */
class OrbitActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var catalog: AppCatalog
    private val handler = Handler(Looper.getMainLooper())
    private var apps: List<TvApp> = emptyList()
    private var pageReady = false
    private val answers = app.orbittv.web.ReadyQueue()   // journey answers that come back before the page is ready
    private val pushed = app.orbittv.web.PushMemo()   // the catalogue goes to a page once, then only when it changes
    private var appsJson: String? = null
    private var lastLaunched: String? = null
    private var appsBuiltMs = 0L   // when the usage facts and ring order were last built; rebuilt on return once stale
    private var catalogDirty = false
    private var introPending = false
    private var introConsumed = false
    private val artCache = HashMap<String, ByteArray>()
    private val feedsUpdated = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { pushFeeds() } }
    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Fire OS fires PACKAGE_CHANGED for its own components all the time; only our apps matter for that action.
            val pkg = intent.data?.schemeSpecificPart ?: return
            val relevant = intent.action != Intent.ACTION_PACKAGE_CHANGED || apps.any { it.packageName == pkg }
            if (relevant) { catalogDirty = true; synchronized(artCache) { artCache.remove(pkg) }; Log.i(WebBridge.TAG, "catalogue dirty: ${intent.action} $pkg") }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        introConsumed = savedInstanceState?.getBoolean(STATE_INTRO_CONSUMED) ?: false
        lastLaunched = savedInstanceState?.getString(STATE_LAST_LAUNCHED)
        introPending = intent.getBooleanExtra(EXTRA_INTRO, false) && !introConsumed
        WakeService.start(this)
        // Debug builds only: the activity is exported, so a release build ignores these ADB switches from any caller.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("audiofx", false)) AudioEnhancer.enable() // feasibility switch, driven over ADB
        catalog = AppCatalog(packageManager, packageName)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            setBackgroundColor(0xFF03101A.toInt())
            addJavascriptInterface(bridge, "Android")
            // Pages come from a private https origin answered from assets (YouTube embeds need a real origin).
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, req: android.webkit.WebResourceRequest) = server.intercept(req)
                // The top frame never leaves our origin, so the bridge never sits under a foreign document.
                override fun shouldOverrideUrlLoading(view: WebView, req: android.webkit.WebResourceRequest): Boolean = req.isForMainFrame && !WebOrigin.allowsTopNavigation(req.url.toString())
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { bridge.pageStarted() }
                // Fresh token per page, injected into the main frame only; a ready the page sent too early runs now.
                override fun onPageFinished(view: WebView, url: String) {
                    if (!WebOrigin.allowsTopNavigation(url)) return
                    view.evaluateJavascript("window.__orbitToken=${JSONObject.quote(gate.renew())};", null)
                    bridge.tokenInjected()
                }
            }
            webChromeClient = object : WebChromeClient() { override fun onConsoleMessage(m: ConsoleMessage): Boolean { Log.i(WebBridge.TAG, "console ${m.messageLevel()} ${m.sourceId()}:${m.lineNumber()} ${m.message()}"); return true } }
            if (BuildConfig.DEBUG) intent.getStringExtra("page")?.let { prefs.edit().putString(PREF_PAGE, it).apply() }  // ADB, debug builds: --es page wall|hero
            val perf = if (BuildConfig.DEBUG) intent.getStringExtra("perf") else null // ADB, debug builds: comma-separated render toggles for measuring
            if (prefs.getString(PREF_PAGE, null) == "stage") prefs.edit().putString(PREF_PAGE, "cinema").apply()   // Cinema's earlier name, rewritten once
            currentPage = WakePolicy.startPage(prefs.getString(PREF_PAGE, "cinema"), Profiles.parse(profilesJson()).isNotEmpty())
            loadUrl(pageUrl(currentPage) + if (perf.isNullOrEmpty()) "" else "#$perf")
        }
        setContentView(web)
        registerReceiver(packageChanges, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
        val feedsFilter = IntentFilter(FeedStore.ACTION_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(feedsUpdated, feedsFilter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(feedsUpdated, feedsFilter)
        buildApps()
    }

    private val radio by lazy { RadioPlayer { state -> if (pageReady) web.evaluateJavascript("orbit.radioState && orbit.radioState(${JSONObject.quote(state)});", null) } }
    private var currentPage = "cinema"
    private val gate = app.orbittv.web.BridgeGate()
    private val bridge by lazy {
        WebBridge(
            onLaunch = { pkg -> runOnUiThread { launch(pkg) } }, onReady = { runOnUiThread { onPageReady() } },
            onOpen = { url, pkg -> runOnUiThread { open(url, pkg) } }, onPage = { name -> runOnUiThread { showPage(name) } },
            onSystem = { action -> runOnUiThread { system(action) } },
            onRadio = { url -> runOnUiThread { playRadio(url) } },
            onJourney = { req -> val gen = answers.generation; journeys.execute { answerJourney(journeyService.handle(req), gen) } },   // answered for the page that asked
            onSaveProfiles = { json -> runOnUiThread { saveProfiles(json) } },
            gate = gate, onToast = { m -> runOnUiThread { android.widget.Toast.makeText(this@OrbitActivity, m, android.widget.Toast.LENGTH_LONG).show() } },
        )
    }
    private val server by lazy { app.orbittv.web.AssetServer(assets) { pkg -> synchronized(artCache) { artCache[pkg] } } }
    private val journeys = java.util.concurrent.Executors.newFixedThreadPool(2)
    private val journeyService by lazy { app.orbittv.feeds.JourneyService({ u -> app.orbittv.feeds.Http.get(u) }, app.orbittv.feeds.FeedStore(java.io.File(filesDir, "journeys")), BuildConfig.YOUTUBE_API_KEY) }
    private val prefs by lazy { getSharedPreferences("orbit", Context.MODE_PRIVATE) }
    private fun pageUrl(name: String) = WebOrigin.pageUrl(if (name in PAGES) name else "cinema")
    /** Switch the hosted page (the Menu key toggles Cinema and the Wall; pages ask for Home Mode, Atlas, Journey, Setup). */
    private fun showPage(name: String) {
        // Home Mode is never the page Orbit starts or wakes into, so its sound never starts by itself.
        radio.stop(); if (name != "home") prefs.edit().putString(PREF_PAGE, name).apply(); currentPage = name; pageReady = false; answers.newPage()
        web.loadUrl(pageUrl(name)); Log.i(WebBridge.TAG, "page -> $name")
    }
    /** A Journeys answer (worker thread) goes back to the page through orbit.journeyResult. */
    private fun answerJourney(res: String, gen: Long) {
        Log.i(WebBridge.TAG, "journey " + res.take(140))
        val js = "window.orbit && orbit.journeyResult && orbit.journeyResult(JSON.parse(${JSONObject.quote(res)}));"
        runOnUiThread { answers.offer(js, gen)?.let { web.evaluateJavascript(it, null) } }   // a quick answer (quota) can beat the page's ready
    }
    /** An empty URL stops the radio; only http and https streams are played. */
    private fun playRadio(url: String) {
        if (url.isBlank()) radio.stop()
        else if (RadioPlayer.playable(url)) radio.play(url)
        else Log.w(WebBridge.TAG, "radio: refused a non-web stream")
    }
    private fun open(url: String, pkg: String?) {
        // A YouTube channel's /live page only opens the app's home; resolve it to the current live video first.
        if (url.contains("youtube.com/") && url.endsWith("/live")) {
            Thread {
                val html = app.orbittv.feeds.Channels.readLive(url) { u, onLine -> app.orbittv.feeds.Http.stream(u, 8_000, onLine) }
                val (live, id, _) = html?.let { app.orbittv.feeds.Channels.resolve(it) } ?: Triple(false, null, null)
                runOnUiThread {
                    if (live && id != null) openNow("https://www.youtube.com/watch?v=$id", pkg)
                    else { web.evaluateJavascript("Shell && Shell.toast('Not live right now, opening the channel');", null); openNow(url.removeSuffix("/live"), pkg) }
                }
            }.start()
            return
        }
        openNow(url, pkg)
    }
    private fun openNow(url: String, pkg: String?) {
        val base = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        try { startActivity(Intent(base).setPackage(pkg)); lastLaunched = pkg } catch (e: Exception) {
            try { startActivity(base); lastLaunched = pkg } catch (e2: Exception) { web.evaluateJavascript("orbit.launchFailed(${JSONObject.quote(pkg ?: url)});", null) }
        }
    }

    /** System row: audio enhancement, screensaver preview, Amazon home. */
    private fun system(request: String) {
        val action = request.substringBefore(':')
        when (action) {
            "audio" -> { val on = !prefs.getBoolean(PREF_AUDIO, false); prefs.edit().putBoolean(PREF_AUDIO, on).apply(); if (on) AudioEnhancer.enable() else AudioEnhancer.disable(); pushSystem() }
            "amazon" -> packageManager.getLeanbackLaunchIntentForPackage(AMAZON_HOME)?.let { startActivity(it) }
            "dream" -> web.evaluateJavascript("Shell && Shell.toast('Starts by itself after five idle minutes');", null) // Fire OS has no public way to start a dream on demand
        }
    }
    private fun systemJson(): String {
        val json = JSONObject().put("audio", prefs.getBoolean(PREF_AUDIO, false))
        return json.toString()
    }
    private fun pushSystem() { if (pageReady) web.evaluateJavascript("orbit.setSystem && orbit.setSystem(${systemJson()});", null) }

    /** The compiled feeds document (Live and Today cards) written by the wake service; pushed whenever it changes. */
    private fun pushFeeds() {
        if (!pageReady) return
        val json = FeedStore.pageJson(this) ?: return
        if (pushed.freshFeeds(json)) {
            web.evaluateJavascript("orbit.setFeeds && orbit.setFeeds(JSON.parse(${JSONObject.quote(json)}));", null)
            Log.i(WebBridge.TAG, "feeds pushed: ${json.length} bytes")
        }
        FeedStore.of(this).load(FeedStore.CATALOG)?.takeIf { pushed.fresh("catalog", it) }?.let { c -> web.evaluateJavascript("orbit.setCatalog && orbit.setCatalog(JSON.parse(${JSONObject.quote(c)}));", null) }
    }

    /** Profiles live in files/profiles.json. The first run preloads it from an optional bundled seed, local-profiles.json (same JSON shape). */
    private fun profilesJson(): String {
        val store = FeedStore(filesDir)
        store.load(Profiles.FILE)?.let { return it }
        val seed = try { assets.open("local-profiles.json").bufferedReader().use { it.readText() } } catch (e: java.io.IOException) { "" }
        val json = Profiles.toJson(Profiles.parse(seed))
        if (seed.isNotBlank()) { store.save(Profiles.FILE, json); WakeService.profilesChanged(this) }   // the seeded people's feeds are fetched now
        return json
    }
    private fun saveProfiles(json: String) {
        val accepted = Profiles.acceptSave(json)
        if (accepted == null) { Log.w(WebBridge.TAG, "saveProfiles refused: payload of ${json.length} chars is invalid or would drop profiles"); return }
        FeedStore(filesDir).save(Profiles.FILE, Profiles.toJson(accepted))
        pushProfiles()
        WakeService.profilesChanged(this)   // a new person's loves, films and homes are fetched now, not at the next schedule
    }
    private fun pushProfiles() {
        if (!pageReady) return
        web.evaluateJavascript("window.orbit && orbit.setProfiles && orbit.setProfiles(JSON.parse(${JSONObject.quote(profilesJson())}));", null)
    }

    /** Catalogue, artwork and usage facts, built off the main thread and pushed to the page when both are ready. */
    private fun buildApps(centreOn: String? = null) {
        catalogDirty = false; appsBuiltMs = System.currentTimeMillis()
        Thread {
            val found = catalog.load()
            val events = UsageRepository(this).recentForegroundEvents()
            val now = System.currentTimeMillis()
            val ranked = UsageRanker.order(found.map { it.packageName }, events, now, TimeZone.getDefault())
            val loaded = ranked.mapNotNull { pkg -> found.firstOrNull { it.packageName == pkg } }
            val brands = loaded.map { TileArtwork.brandOf(it) }
            // Rendered outside the lock (AssetServer reads the cache on the WebView's threads); kept only if no one put one meanwhile.
            val art = loaded.mapIndexed { i, app ->
                synchronized(artCache) { artCache[app.packageName] }
                    ?: TileArtwork.facePng(app, brands[i]).let { png -> synchronized(artCache) { artCache.getOrPut(app.packageName) { png } } }
            }
            val usage = UsageSummary.build(loaded.map { it.packageName }, events, now, TimeZone.getDefault())
            val json = WebBridge.appsJson(loaded, brands, art, usage)
            val light = loaded.map { it.copy(banner = null, icon = null) }
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                apps = light; appsJson = json
                pushApps(centreOn)
            }
        }.start()
    }

    private fun pushApps(centreOn: String?) {
        val json = appsJson ?: return
        if (!pageReady) return
        val centre = centreOn?.let { JSONObject.quote(it) } ?: "null"
        val intro = introPending
        if (intro) { introPending = false; introConsumed = true }
        web.evaluateJavascript("orbit.setApps($json, { centreOn: $centre, intro: $intro });", null)
        Log.i(WebBridge.TAG, "apps pushed: ${apps.size}, intro=$intro, centreOn=$centreOn")
    }

    private fun onPageReady() {
        pageReady = true; pushed.clear()
        pushApps(lastLaunched.also { lastLaunched = null })
        pushFeeds(); pushSystem(); pushProfiles()
        answers.ready().forEach { web.evaluateJavascript(it, null) }
    }

    private fun launch(pkg: String) {
        val app = apps.firstOrNull { it.packageName == pkg }
        // Hand-offs from Live cards may name an app that is not on the ring (for example discovery+); fall back to the package's own launcher.
        val intent = if (app != null) catalog.launchIntent(app) else (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
        if (intent == null) { web.evaluateJavascript("orbit.launchFailed(${JSONObject.quote(pkg)});", null); return }
        try {
            startActivity(intent); lastLaunched = pkg
        } catch (e: ActivityNotFoundException) {
            web.evaluateJavascript("orbit.launchFailed(${JSONObject.quote(pkg)});", null)
            handler.postDelayed({ buildApps() }, 1_500)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_INTRO, false)) { introPending = true; introConsumed = false }
        if (intent.getBooleanExtra(EXTRA_WAKE, false)) WakePolicy.pageAfterWake(currentPage).let { if (it != currentPage) showPage(it) }
    }

    override fun onResume() {
        super.onResume(); web.onResume(); resumed = true; web.evaluateJavascript("window.orbit && orbit.resumed && orbit.resumed();", null)
        val returning = lastLaunched; lastLaunched = null
        val rebuilding = catalogDirty
        when {
            catalogDirty || !pageReady || appsJson == null -> if (catalogDirty) buildApps(centreOn = returning)
            introPending -> { introPending = false; introConsumed = true; web.evaluateJavascript("orbit.wake(${returning?.let { JSONObject.quote(it) } ?: "null"});", null) }
            returning != null -> web.evaluateJavascript("orbit.returned(${JSONObject.quote(returning)});", null)
        }
        // The activity is singleTask and stays alive, so usage minutes and the ring order would otherwise stay as at start (artwork is cached).
        if (!rebuilding && pageReady && appsJson != null && UsageSummary.refreshDue(appsBuiltMs, System.currentTimeMillis())) buildApps()
    }

    override fun onPause() { super.onPause(); web.evaluateJavascript("window.orbit && orbit.paused && orbit.paused();", null); radio.stop(); web.onPause(); resumed = false; lastPausedAt = System.currentTimeMillis() } // no pauseTimers(): it is process-global and would freeze the dream's clock

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_INTRO_CONSUMED, introConsumed)
        lastLaunched?.let { outState.putString(STATE_LAST_LAUNCHED, it) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Back stays inside Orbit; the Amazon home escape hatch lives in the Playground's System tile.
        if (keyCode == KeyEvent.KEYCODE_BACK) { web.evaluateJavascript("orbit.back();", null); return true }
        if (keyCode == KeyEvent.KEYCODE_MENU) { showPage(if (currentPage == "wall") "cinema" else "wall"); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(packageChanges)
        unregisterReceiver(feedsUpdated)
        journeys.shutdownNow()
        web.destroy()
    }

    companion object {
        const val EXTRA_INTRO = "intro"
        const val EXTRA_WAKE = "wake"
        private const val PREF_PAGE = "page"
        const val PREF_AUDIO = "audio"
        private const val AMAZON_HOME = "com.amazon.tv.launcher"
        private val PAGES = listOf("cinema", "hero", "wall", "home", "atlas", "journey", "setup")
        private const val STATE_INTRO_CONSUMED = "introConsumed"
        private const val STATE_LAST_LAUNCHED = "lastLaunched"
        @Volatile private var resumed = false
        @Volatile private var lastPausedAt = 0L
        fun inFrontAt(atMs: Long): Boolean = resumed || (atMs - lastPausedAt) in 0..3_000
    }
}
