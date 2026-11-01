# How Orbit is built

A short map of the code for anyone who wants to change it. Package: `app.orbittv`. Kotlin sources are in `app/src/main/java/app/orbittv/`, the pages in `app/src/main/assets/web/`, bundled data in `app/src/main/assets/`.

## Native shell

A plain Android app (Kotlin, `androidx.core` only, no Google Play Services, which Fire TVs do not have). minSdk 25, target 34, 32-bit ARM.

- **`OrbitActivity`** hosts one WebView and switches pages (`cinema`, `setup`, `atlas`, `journey`, `home`, `hero`, `wall`). It launches apps, plays home radio and answers the pages' requests.
- **`wake/`**: Fire OS does not allow a true default launcher, so `WakeService` (a foreground service) brings Orbit back each time the TV wakes, and refreshes the feeds. `BootReceiver` starts it after a reboot.
- **`catalog/`**: the installed TV apps, with generated artwork when an app has no good banner.
- **`usage/`**: usage statistics (granted once over ADB) rank apps and feed the digital-wellbeing summary.
- **`dream/`**: `OrbitDreamService`, the screensaver (the Earth in low orbit, `web/index.html#dream`).
- **`audio/`**: an optional system-wide audio enhancement and the Home Mode radio player with a watchdog.

## Pages and the bridge

The pages are HTML, CSS and JavaScript, served from a private https origin (`https://orbit.local/web/...`, `web/WebOrigin.kt`, `web/AssetServer.kt`) answered from the APK's assets, not from `file://`; YouTube embeds need a real origin. File access is off. An image proxy (`https://orbit.local/img?u=`) fetches only TMDB, YouTube thumbnail and Wikimedia images so a page can read their brightness.

Pages talk to the shell through one injected `Android` object (`web/WebBridge.kt`). Calls that act or push data (ready, launch, open, system, page, radio, journey, saveProfiles) need a per-page token (`web/BridgeGate.kt`) that the shell injects into the main frame only. The YouTube player runs in a plain embed iframe driven over postMessage (`web/ytframe.js`), so no third-party script runs in the frame that holds the token. Answers come back as calls on `window.orbit`. Shared page logic is in `common.js`; the pure modules (`setup.js`, `profiles.js`, `taste.js`, `cinema-rows.js`, `journeys.js`, `home.js`, `keyboard.js`, `world-data.js`, `wellbeing.js`) are tested with Node.

## Profiles

A profile is a name, a home (country and up to three places), languages in priority order, where the person lives (city, time zone, country) and loves (catalogue ids with follow-up choices, or a custom search). Up to six people per TV. The shell stores them in the app's `files/profiles.json` (`feeds/Profiles.kt`); an invalid save is refused, so good profiles are never overwritten by a broken list. Per-person page data (history, taste, journey history) is keyed by profile id in WebView storage. Setup (`setup.html`, `setup.js`) creates and edits profiles. An optional git-ignored `local-profiles.json` at the repository root is bundled as a first-launch seed (see SETUP).

Nothing about a country, language, region or sport is written into the code: it comes from the profiles and from data keyed by country or language code (`countries.json`, `languages.json`, `channels/<CC>.json`, `festivals/<CC>.json`, `handoffs.json`, `providers.json`, `web/homes/local.json`, `interests.json`).

## Feeds and caches

`feeds/FeedRefresher.kt`, run by `WakeService`, fetches everything the pages show and writes one compiled document that the activity pushes to the page whenever it changes. `feeds/FeedSchedule.kt` decides when: sport every 2 minutes while something followed is live, otherwise every 15; news every 30 minutes; the catalogue once a day; nothing between 23:00 and 07:00 unless the TV wakes. Raw bodies and the compiled document live in the app's files (`feeds/FeedStore.kt`), so cards survive restarts and failed fetches. Caches are keyed by love, language and country, so similar people share work.

Sources: TMDB (`Tmdb.kt`, `Catalog.kt`), YouTube Data API (`LoveFeeds.kt`, `Journeys.kt`, `Channels.kt`), Google News RSS (`Rss.kt`, `NewsCards.kt`), ESPN and Cricinfo (`EspnScoreboard.kt`, `CricinfoLive.kt`, `CricketSeries.kt`), Jolpica (`Jolpica.kt`), Open-Meteo (`HomeWeather.kt`), Nager.Date (`Nager.kt`), Radio Browser (`RadioBrowser.kt`), Wikipedia and Wikidata (`Places.kt`, `Journeys.kt`), the Netflix Top 10 file and TVmaze (`Tonight.kt`), ipwho.is for the TV's country. Keys come only from `BuildConfig` (built from `local.properties`); they never reach the pages, the logs or the cache files.

## The YouTube allowance

A free YouTube Data API key allows about 100 searches a day. Orbit stops at 80 per Pacific-time day (when Google's count resets), shared by Journeys and loves; loves may use at most 60, and live searches for loves run at most once a day. Each place and mode is cached for 7 days, live cameras for 1 hour. When the day is used up the pages say so and keep working with what is cached.

## The relevance gate

Every row must be about what the person chose. `feeds/Relevance.kt` and the love recipes in `interests.json` (built by `tools/interests/build_catalogue.py`, which checks every TMDB keyword, ESPN league and team and image against the live source) decide what may show: match words, a whole-word clickbait filter that exempts a love's own words (so "fight" stays for boxing), minimum lengths for footage, and embeddable videos only. Home news must name the home place, its region or its country in one of its languages (`feeds/PlaceNames.kt`). Rows and cards say why they are there.

## Performance budget

Orbit was built on a TV with a fill-bound Mali-G31 GPU. The target is a 90th-percentile frame time of 22 ms or less and under 220 MB of memory (total PSS) while moving with the remote. What keeps it there:

- Pages are plain DOM. Only the screensaver uses WebGL (Three.js); a WebGL canvas under rich DOM took a slow compositing path on this WebView.
- The Journeys globe is 72 pre-rendered frames (`tools/globe/`), decoded off-screen and swapped once focus rests.
- Only `transform` and `opacity` animate, with one shared focus ring. Large art is painted once into a software canvas.
- Image sizes are the small ones each service offers (Wikimedia 330/500/960 px widths, YouTube 320 px thumbnails, TMDB small sizes).

Measure on your own TV with `tools/measure.sh` (install, launch, `fps`, `jank` for frame-time percentiles from `dumpsys gfxinfo`, `mem` for `dumpsys meminfo`). Debug builds also accept `--es perf <flags>` switches that turn parts of a page off, to find what costs the time. Send one remote key every 0.7 seconds while measuring; each `adb shell input` call spawns a process on the TV and skews the numbers.

## Tests

```
node --test app/src/test/js/*.test.js     # page logic
./gradlew :app:testDebugUnitTest          # native code, against recorded responses in app/src/test/resources/feeds
```
