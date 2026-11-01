# Setting up Orbit on a Fire TV

This takes about an hour the first time. You need a computer (macOS, Linux or Windows), a Fire TV on the same Wi-Fi, and two free API keys. Orbit is a personal project: it is installed from your computer over the network, not from the Amazon Appstore.

## 1. Tools on your computer

- **JDK 17.** On macOS: `brew install openjdk@17`. Check with `java -version`.
- **Android SDK command-line tools** with `platform-tools` (for `adb`), `build-tools;34.0.0` (for `apksigner`) and `platforms;android-35`. On macOS: `brew install --cask android-commandlinetools`, then
  `sdkmanager "platform-tools" "build-tools;34.0.0" "platforms;android-35"`, then accept the licences with `yes | sdkmanager --licenses`. With the Homebrew cask the SDK lives in `$(brew --prefix)/share/android-commandlinetools` (that is your `sdk.dir`).
- **Git**, to clone this repository.

Put `<sdk>/platform-tools` (for `adb`) and `<sdk>/build-tools/34.0.0` (for `apksigner`) on your `PATH`.

## 2. Prepare the Fire TV

1. Settings > My Fire TV > About. Select the device name seven times until it says you are a developer.
2. Settings > My Fire TV > Developer options: turn on **ADB debugging** and **Apps from Unknown Sources**.
3. Settings > My Fire TV > About > Network: note the **IP address**.
4. On your computer: `adb connect <tv-ip>:5555`. Accept the prompt on the TV (tick "Always allow").
5. `adb devices` should list the TV as `device`. If it says `offline`, wake the TV and connect again.

## 3. Get your own two free keys

Each person who builds Orbit uses their own keys. They are free, personal and never part of the repository.

**TMDB** (films, series, trailers, posters, where to watch):
1. Create an account at themoviedb.org.
2. Settings > API > request a key for personal, non-commercial use.
3. Copy the "API Key" (the short one, not the long read-access token).

**YouTube Data API v3** (Journeys footage and videos for loves):
1. Go to console.cloud.google.com and create a project.
2. APIs & Services > Library > enable **YouTube Data API v3**.
3. APIs & Services > Credentials > Create credentials > API key.
4. Edit the key: under API restrictions choose **Restrict key** and select only YouTube Data API v3. Leave application restrictions as None (the TV calls it directly).

The free YouTube allowance is about 100 searches a day. Orbit stops itself at 80 per Pacific-time day (when Google's count resets), shared by Journeys and loves (loves take at most 60), and caches what it finds.

## 4. Configure

```
git clone https://github.com/aika-labs-design/orbit-tv.git
cd orbit-tv
cp local.properties.example local.properties
```

Edit `local.properties`: set `sdk.dir` to your SDK path and paste both keys (`tmdb.apiKey=...`, `youtube.apiKey=...`). This file is ignored by git.

Optionally set `contact.email=you@example.com`. Orbit names a contact in the User-Agent of its feed requests, as Wikimedia and others ask; without it the contact is the project page and no email is sent. The data tools in `tools/` read the same contact from the `ORBIT_CONTACT` environment variable.

- **Never commit your keys** and never paste them into code, data files, docs or issues. They live only in `local.properties` and reach the app through the build.
- Run `tools/check-secrets.sh` before every commit and push (see "Before you push" below).
- The keys are built into the app. **Do not share the APK you build**: anyone who has it can read your keys out of it.

## 5. Build, sign and install

```
export JAVA_HOME=$(brew --prefix openjdk@17)   # macOS with Homebrew; elsewhere, your JDK 17 path
./gradlew assembleRelease
```

Sign with your Android debug key. If `~/.android/debug.keystore` does not exist yet, create it once:

```
keytool -genkeypair -keystore ~/.android/debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Then:

```
apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out orbit.apk app/build/outputs/apk/release/app-release-unsigned.apk
adb install -r orbit.apk
```

## 6. Permissions and the screensaver

```
adb shell appops set app.orbittv GET_USAGE_STATS allow
adb shell settings put secure screensaver_enabled 1
adb shell settings put secure screensaver_components app.orbittv/.dream.OrbitDreamService
adb shell settings put secure screensaver_activate_on_sleep 1
```

Open Orbit once from Apps on the TV. From then on it takes over each time the TV wakes. Fire OS does not let any app replace the Home button, which still goes to Amazon's home.

To go back to the Fire TV screensaver:
`adb shell settings put secure screensaver_components com.amazon.ftv.screensaver/.app.services.ScreensaverService`

## 7. First setup on the TV

A new install has no people yet, so Orbit opens in setup. Everything is done with the remote: arrows to move, Select to choose, Back to go one step back (your choices are kept).

1. **Name.** Type on the on-screen keyboard and choose Done. Hold Select on a letter, or use the "à é" key, for accents and tone marks.
2. **Where is home?** Up and Down move through the countries while the globe turns to each one; type to jump. Select opens a country's cities: pick up to three, or "Another town in ..." to search for any town.
3. **Languages and where you live.** Select adds or removes a language; the first one you pick leads (films, greetings, radio, news). The card on the right shows the city Orbit guessed from the TV's clock: choose "That's right" or "Somewhere else" to search for yours.
4. **What you love.** Select loves a tile; Right on a loved tile asks a follow-up (teams, a cuisine). "Search anything" takes any word. You can skip this step: Orbit learns as you watch.
5. **Summary.** Select a language and move it with Left and Right to change the order. Start saves and opens your home.

The first few minutes after Start, Cinema fills in as films, live sport and news for your choices arrive. Your films tab and film shelves can take about four minutes.

## 8. More people, and changing your setup

The circle with your initial, at the right of the top bar in Cinema, opens a menu:

- **Who's watching** lists everyone on this TV; select a person to switch. Each person has their own home, picks and history.
- **Add a person** runs setup again for someone new (up to six people).
- **Change my setup** opens setup with the current person's choices filled in.

## 9. Make it yours: the data files

These are data files you can edit, then rebuild and install. Codes are ISO country codes (`NG`, `DE`) and ISO language codes (`yo`, `de`; a 3-letter code such as `pcm` where a language has no 2-letter one).

- **Loves:** `app/src/main/assets/interests.json` is generated; do not edit it by hand. Add or change a love in `tools/interests/catalogue_data.py` (name, theme, Wikipedia photo, match words, TMDB keywords or genres, news and video queries, live source, follow-up options), then run `python3 tools/interests/build_catalogue.py`. It reads your TMDB key from `local.properties` at run time and checks every keyword, league, team and image against the live sources before writing the file. `--check` verifies without writing.
- **Live channels:** `app/src/main/assets/channels/<CC>.json`, curated YouTube live channels for people whose home is that country (`IN.json` is an example). A home country without a file gets one YouTube live search a day for "<country> live news".
- **Festival days:** `app/src/main/assets/festivals/<CC>.json`, public holidays for a home country that Nager.Date does not cover: `national` days for everyone with that home, plus `languages` days for speakers of a language. **These dates are fixed per year: add the next year's dates once the official lists are out** (the bundled `IN.json` runs to the end of 2027). Countries Nager.Date covers need no file.
- **Sport hand-offs:** `app/src/main/assets/handoffs.json`, which Fire TV app opens which sport, by country and league (an ESPN league path such as `soccer/eng.1`, or `cricket`, `f1`, `motogp`, `tennis`). A country without an entry shows the score with no app button.
- **Streaming apps:** `app/src/main/assets/providers.json`, which Fire TV app plays which TMDB watch provider: apps offered everywhere, plus extras per country.
- **Home Mode touches:** `app/src/main/assets/web/homes/local.json`, keyed by country code: an extra soundscape (`sound`, one of the files in `homes/sounds/`) and a dawn or evening line for that home. Credit any new sound in `homes/ATTRIBUTION.md`.
- **Place names:** `app/src/main/assets/web/aliases.json`, groups of names for one place (Mumbai, Bombay), so Journeys recognises footage that uses an older or local name.
- **Journeys places:** `app/src/main/assets/web/places.json`, the curated world places.
- **Sport details:** `app/src/main/assets/feeds.json`, cricket leagues scanned for fixtures, tennis rounds always shown, venues (with their country), the MotoGP calendar.
- **Countries and languages:** `countries.json` and `languages.json` are generated from GeoNames and CLDR by `tools/data/build_countries.py`; see `tools/data/src/README.txt`.

Homes, languages, the streaming country (where to watch, the Netflix Top 10), followed sport and news topics come from the people set up on the TV, never from these files.

## 10. Preloading people (private seed)

To skip setup on a new install, put a `local-profiles.json` at the repository root before you build. It is git-ignored and is bundled only into builds made on a computer that has it, so it never reaches the repository.

The file is a JSON array of people in the same shape the TV saves (`id`, `name`, `created`, `home` with `country` and `places` of `name`, `lat`, `lon`, `tz`, `languages`, `living` with `name`, `lat`, `lon`, `tz`, `country`, and `loves` of `id` and `refine`). On the first launch with no saved people, Orbit stores them as its profiles. A fresh install without the seed starts in setup. Check the file is not tracked with `git check-ignore -v local-profiles.json`; `tools/check-secrets.sh` also fails if it is ever committed.

## 11. Updating

`git pull`, then repeat step 5. Your `local.properties` and the people and learning on the TV are kept.

## Before you push

Run the secret check before every commit and push:

```
tools/check-secrets.sh
```

It reads your `tmdb.apiKey` and `youtube.apiKey` from `local.properties` and fails if either value, a Google API key pattern, or a tracked `local.properties` or `local-profiles.json` appears in any tracked file or any commit. It prints only file names and commit ids, never a key. You want to see `secrets: clean`.

## Troubleshooting

- **`adb` says unauthorized:** accept the prompt on the TV, or run `adb kill-server` and connect again.
- **Films are missing:** check `tmdb.apiKey` and rebuild. Live, apps and home rows work without it.
- **Journeys says "Travel search needs the YouTube key":** the key is missing. "Resting until tomorrow" means the day's 80 searches are used up.
- **Love rows show news but no videos:** the day's YouTube searches for loves are used up (60 of the 80); they come back after midnight Pacific time.
- **Journeys says "Could not reach YouTube":** check the key's API restriction allows YouTube Data API v3.
- **Commands hang:** the TV is asleep. Press a button on the remote, then `adb connect` again.

## Tests

```
node --test app/src/test/js/*.test.js     # page logic (Node 18 or later)
./gradlew :app:testDebugUnitTest          # native code
```
