#!/usr/bin/env bash
# Device measurement helpers for the reference TV (see README). Usage: tools/measure.sh <install|fps|jank|mem|shot FILE|launch> [...]
# install builds release (real-world numbers). The --es perf / --es page switches are honoured by debug builds only (assembleDebug).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"
ADB="$ANDROID_HOME/platform-tools/adb"
PKG=app.orbittv
ACT=$PKG/.OrbitActivity
OUT=build/measure; mkdir -p "$OUT"
wake() { "$ADB" shell input keyevent KEYCODE_WAKEUP; sleep 2; }
# A key the app ignores, sent after launches so the Fire OS idle timer (screensaver after 5 min) is reset.
nudge() { "$ADB" shell input keyevent KEYCODE_DPAD_UP; }

for cmd in "$@"; do case "$cmd" in
  install)
    ./gradlew assembleRelease --no-daemon -q 2>&1 | grep -E "^e:|FAILED" || true
    "$ANDROID_HOME/build-tools/34.0.0/apksigner" sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
      --ks-key-alias androiddebugkey --out "$OUT/app.apk" app/build/outputs/apk/release/app-release-unsigned.apk
    "$ADB" install -r "$OUT/app.apk" | tail -1
    "$ADB" shell appops set $PKG GET_USAGE_STATS allow
    wake; "$ADB" shell am force-stop $PKG; "$ADB" logcat -c
    "$ADB" shell am start -n $ACT >/dev/null; sleep 4; nudge; sleep 2 ;;
  launch) wake; "$ADB" shell am force-stop $PKG; "$ADB" logcat -c; "$ADB" shell am start -n $ACT >/dev/null; sleep 4; nudge; sleep 2 ;;
  fps) "$ADB" logcat -d -s OrbitWeb | grep fps | tail -3 ;;
  jank)
    "$ADB" shell dumpsys gfxinfo $PKG reset >/dev/null
    # One shell invocation per direction: each adb "input" call spawns a process on the TV and skews the numbers.
    "$ADB" shell input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
    sleep 1
    "$ADB" shell input keyevent KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT
    sleep 2; "$ADB" shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky|90th|99th" | tr '\n' ' '; echo ;;
  mem) "$ADB" shell dumpsys meminfo $PKG | grep -E "TOTAL:" | head -1 ;;
  shot) shift_next=1 ;;
  *) if [ "${shift_next:-0}" = 1 ]; then "$ADB" exec-out screencap -p > "$cmd"; echo "saved $cmd"; shift_next=0; else echo "unknown: $cmd"; fi ;;
esac; done
