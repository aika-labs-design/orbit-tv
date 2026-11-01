package app.orbittv.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import app.orbittv.OrbitActivity
import app.orbittv.feeds.Channels
import app.orbittv.feeds.FeedConfig
import app.orbittv.feeds.FeedRefresher
import app.orbittv.feeds.PlaceNames
import app.orbittv.feeds.FeedSchedule
import app.orbittv.feeds.FeedStore
import app.orbittv.feeds.FestivalBook
import app.orbittv.feeds.Handoffs
import app.orbittv.feeds.Http
import app.orbittv.feeds.Interests
import app.orbittv.feeds.LoveFeeds
import app.orbittv.feeds.LoveKeys
import app.orbittv.feeds.Profiles
import app.orbittv.feeds.ProviderApps
import app.orbittv.feeds.World
import app.orbittv.feeds.YouTubeQuota
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * Keeps a process alive so the TV waking up (SCREEN_ON) brings Orbit to the front.
 * Records what was in front when the screen went off, so TV inputs are left alone.
 */
class WakeService : Service() {
    private var foregroundAtSleep: String? = null
    private var sleepAtMs = 0L

    // Feeds: refreshed here because this service outlives the activity, so cards are current when Orbit comes back.
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var refresher: FeedRefresher? = null
    @Volatile private var lastLiveMs = 0L
    @Volatile private var lastNewsMs = 0L
    @Volatile private var lastChannelsMs = 0L
    @Volatile private var networkChanged = false   // the country the TV is seen from is looked up on wake and on a network change only
    @Volatile private var countryFailedMs: Long? = null   // the last failed lookup; null after a success (FeedSchedule.countryDue)
    @Volatile private var lastTonightMs = 0L
    @Volatile private var lastStationsMs = 0L
    @Volatile private var lastCatalogMs = 0L
    @Volatile private var audioRetry = false
    @Volatile private var forced = true
    @Volatile private var refreshing = false
    private val check = object : Runnable {
        override fun run() {
            val r = refresher ?: return
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); val now = System.currentTimeMillis()
            val live = FeedSchedule.liveDue(lastLiveMs, now, r.anyLive, hour, forced)
            val news = FeedSchedule.newsDue(lastNewsMs, now, hour, forced)
            val channels = FeedSchedule.channelsDue(lastChannelsMs, now, hour, forced)
            val tonight = FeedSchedule.tonightDue(lastTonightMs, now, hour, cached = r.tonightSavedAt != null)
            val stations = FeedSchedule.stationsDue(lastStationsMs, now, hour)
            val catalog = FeedSchedule.catalogDue(lastCatalogMs, now, hour)
            val country = FeedSchedule.countryDue(forced, networkChanged, countryFailedMs, now)
            val feedsDue = live || news || channels || tonight || stations || catalog
            if ((feedsDue || country) && !refreshing) {
                refreshing = true; forced = false
                worker.execute {
                    try { r.pass {
                        val countryWas = r.country
                        if (country) { networkChanged = false; countryFailedMs = if (r.refreshCountry()) null else System.currentTimeMillis() }
                        // A pass for the country alone rewrites and announces the page only when the country changed (hand-offs follow it).
                        if (!feedsDue) { if (r.country != countryWas) { r.writePage(); sendBroadcast(Intent(FeedStore.ACTION_UPDATED).setPackage(packageName)) }; return@pass }
                        if (live) { r.refreshLive(); r.refreshPlaces(); lastLiveMs = System.currentTimeMillis() }
                        if (news) { r.refreshHomes(); lastNewsMs = System.currentTimeMillis() }
                        if (channels) { r.refreshChannels(); lastChannelsMs = System.currentTimeMillis() }
                        if (stations) { r.refreshStations(); lastStationsMs = System.currentTimeMillis() }
                        if (!tonight && !FeedSchedule.quiet(hour)) r.refreshPosters()
                        if (tonight) { if (r.refreshTonight { url, onLine -> Http.stream(url, onLine = onLine) }) lastTonightMs = System.currentTimeMillis() else lastTonightMs = System.currentTimeMillis() - 23 * 3600_000L }
                        val page = r.writePage()
                        Log.i(TAG, "feeds: live=${live} news=${news} channels=${channels} tonight=${tonight} catalog=${catalog} anyLive=${r.anyLive} bytes=${page.length}")
                        sendBroadcast(Intent(FeedStore.ACTION_UPDATED).setPackage(packageName))
                        // Loves cost several requests each (their own caches gate them), so they follow the page out and send a second update.
                        if (live || news) { if (r.refreshLoves()) { r.writePage(); sendBroadcast(Intent(FeedStore.ACTION_UPDATED).setPackage(packageName)) } }
                        // The catalogue can take minutes (TMDB details), so it runs after the live page is out; a failure retries in about an hour.
                        if (catalog) { val ok = r.refreshCatalog(); lastCatalogMs = FeedSchedule.catalogMark(ok, System.currentTimeMillis()); if (ok) sendBroadcast(Intent(FeedStore.ACTION_UPDATED).setPackage(packageName)) }
                    } } catch (e: Exception) { Log.w(TAG, "feeds failed", e) } finally { refreshing = false }
                }
            }
            handler.postDelayed(this, FeedSchedule.nextCheckDelayMs(r.anyLive, hour))
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() { override fun onAvailable(network: Network) { networkChanged = true } }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    sleepAtMs = System.currentTimeMillis()
                    handler.removeCallbacks(check)   // no feed refreshes while the screen is off; SCREEN_ON forces one
                    foregroundAtSleep = currentForeground()
                    Log.i(TAG, "screen off, foreground=$foregroundAtSleep")
                }
                Intent.ACTION_SCREEN_ON -> {
                    forced = true; handler.removeCallbacks(check); handler.post(check)
                    if (audioRetry) audioRetry = app.orbittv.audio.AudioEnhancer.enable().contains("failed")
                    val orbitInFront = foregroundAtSleep == packageName || OrbitActivity.inFrontAt(sleepAtMs)
                    val takeOver = WakePolicy.shouldTakeOver(foregroundAtSleep)
                    val intro = WakePolicy.shouldPlayIntro(orbitInFront)
                    Log.i(TAG, "screen on, foregroundAtSleep=$foregroundAtSleep takeOver=$takeOver intro=$intro")
                    if (takeOver) startActivity(
                        Intent(context, OrbitActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            .putExtra(OrbitActivity.EXTRA_INTRO, intro).putExtra(OrbitActivity.EXTRA_WAKE, true),
                    )
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Wake watcher", NotificationManager.IMPORTANCE_MIN))
        startForeground(1, Notification.Builder(this, CHANNEL)
            .setContentTitle("Orbit").setContentText("Ready to wake with the TV")
            .setSmallIcon(android.R.drawable.ic_media_play).build())
        registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) })
        runCatching { getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback) }
        if (getSharedPreferences("orbit", Context.MODE_PRIVATE).getBoolean(OrbitActivity.PREF_AUDIO, false)) audioRetry = app.orbittv.audio.AudioEnhancer.enable().contains("failed")
        worker.execute {
            try { fun asset(name: String) = assets.open(name).bufferedReader().use { it.readText() }
                // curated live channels per home country: channels/<CC>.json
                val channelFiles = (assets.list("channels") ?: emptyArray()).filter { it.endsWith(".json") }
                    .mapNotNull { f -> runCatching { f.removeSuffix(".json") to Channels.parse(asset("channels/$f")) }.getOrNull() }.toMap()
                // bundled holidays for home countries Nager.Date does not cover: festivals/<CC>.json
                val festivalFiles = (assets.list("festivals") ?: emptyArray()).filter { it.endsWith(".json") }
                    .mapNotNull { f -> runCatching { f.removeSuffix(".json") to FestivalBook.parse(asset("festivals/$f")) }.getOrNull() }.toMap()
                val interests = runCatching { Interests.parse(assets.open(Interests.ASSET).bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
                val countries = runCatching { World.countries(assets.open("countries.json").bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
                val languages = runCatching { World.languages(asset("languages.json")) }.getOrDefault(emptyList())
                val langNames = languages.associate { it.code to it.name }
                val feedStore = FeedStore.of(this)
                // the YouTube allowance file lives with Journeys, so both count against the same 80 a day
                val quota = YouTubeQuota(FeedStore(java.io.File(filesDir, "journeys")))
                val keys = app.orbittv.BuildConfig.TMDB_API_KEY to app.orbittv.BuildConfig.YOUTUBE_API_KEY
                val loveFeeds = LoveFeeds(Http::get, feedStore, LoveKeys(keys.first, keys.second), quota, { code -> langNames[code] ?: code })
                val installed = { runCatching { packageManager.getInstalledPackages(0).map { it.packageName }.toSet() }.getOrDefault(emptySet()) }
                refresher = FeedRefresher(
                    FeedConfig.load(assets), feedStore, Http::get,
                    onSourceFailed = { Log.w(TAG, "feeds: $it") },
                    channels = channelFiles, festivals = festivalFiles,
                    loveFeeds = loveFeeds,
                    profiles = { Profiles.parse(FeedStore(filesDir).load(Profiles.FILE) ?: "[]") },
                    interests = interests, countries = countries, languages = languages,
                    handoffs = runCatching { Handoffs.parse(asset("handoffs.json")) }.getOrDefault(Handoffs.NONE),
                    providerApps = runCatching { ProviderApps.parse(asset("providers.json")) }.getOrDefault(ProviderApps.NONE),
                    youtubeKey = keys.second, quota = quota, tmdbKey = keys.first, installed = installed,
                    stream = { u, onLine -> Http.stream(u, onLine = onLine) },
                    placeNames = runCatching { PlaceNames.parse(asset("place-names.json"), asset("web/aliases.json")) }.getOrDefault(PlaceNames.NONE),
                ).also { lastTonightMs = it.tonightSavedAt ?: 0L }
                handler.post(check)
            } catch (e: Exception) { Log.w(TAG, "feeds config failed", e) }
        }
        Log.i(TAG, "service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROFILES) {
            // Saved profiles: everything that depends on people (loves, catalogue languages, homes, live, radio) is due now.
            forced = true; lastCatalogMs = 0L; lastStationsMs = 0L
            handler.removeCallbacks(check); handler.post(check)
            Log.i(TAG, "profiles changed: refresh now")
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { unregisterReceiver(screenReceiver); runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }; handler.removeCallbacks(check); worker.shutdownNow(); super.onDestroy() }

    /**
     * The app the person was really using before sleep: the latest foreground event in the past hours,
     * ignoring the Amazon launcher, which Fire OS brings forward as the screen goes off. Null without usage access.
     */
    private fun currentForeground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - LOOKBACK_MS, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND && e.packageName != AMAZON_LAUNCHER) last = e.packageName
        }
        return last
    }

    companion object {
        const val TAG = "OrbitWake"
        private const val CHANNEL = "wake"
        private const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
        private const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
        private const val ACTION_PROFILES = "app.orbittv.PROFILES_CHANGED"
        fun start(context: Context) { context.startForegroundService(Intent(context, WakeService::class.java)) }
        fun profilesChanged(context: Context) { context.startForegroundService(Intent(context, WakeService::class.java).setAction(ACTION_PROFILES)) }
    }
}
