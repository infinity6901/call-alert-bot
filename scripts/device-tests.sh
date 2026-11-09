#!/usr/bin/env bash
# Runs on a booted emulator (via reactivecircus/android-emulator-runner). Automates:
#   - Gradle instrumented tests (connectedDebugAndroidTest)
#   - install of the debug APK that CI built, with a throw-away bot config seeded on the device
#   - cold launch, background/foreground, rotation, lock/unlock and force-stop/relaunch cycles
#   - crash / ANR detection from logcat
#   - a simulated incoming call and missed call (adb emu gsm): the receiver must dispatch an alert
#     for each. The bot token is fake, so Telegram rejects the message; what is checked is that the
#     receiver ran end to end, not that a message arrived.
# Usage: device-tests.sh <debug-apk> <outdir> [cycles]
set -u
APK="$1"; OUT="$2"; CYCLES="${3:-3}"; PKG="com.callalertbot"
mkdir -p "$OUT"
export CI_LOG_DIR="$OUT/ci-logs" CI_STATUS_DIR="$OUT/ci-status"
# shellcheck source=/dev/null
source "$(dirname "$0")/ci-step.sh"

adb wait-for-device
adb shell getprop ro.product.model         > "$OUT/device-model.txt"
adb shell getprop ro.build.version.release > "$OUT/android-version.txt"
adb shell getprop ro.build.display.id      > "$OUT/android-build.txt"
adb shell getprop ro.product.cpu.abi       > "$OUT/device-abi.txt"

# --- 1. Gradle instrumented tests -----------------------------------------------------------
ci_step instrumented-tests "Gradle instrumented tests (connectedDebugAndroidTest)" \
  ./gradlew connectedDebugAndroidTest --stacktrace --no-build-cache

# --- 2. Install the artifact that CI built --------------------------------------------------
adb logcat -c
adb logcat -v time > "$OUT/call-alert-logcat.txt" 2>&1 &
LOGCAT_PID=$!
trap 'kill $LOGCAT_PID 2>/dev/null' EXIT

# The instrumented tests leave their own copy and data behind; start clean.
adb uninstall "$PKG" >/dev/null 2>&1 || true
ci_step apk-install "Install the CI-built debug APK" adb install -r -g "$APK"

# Written before the first launch so the app's process reads it on start. The debug build is
# debuggable, so run-as can write into its private storage.
seed_config() {
  local xml b64
  xml="<?xml version='1.0' encoding='utf-8' standalone='yes' ?><map><string name=\"token\">123456789:AA-ci-test-token-not-real</string><string name=\"chat_id\">123456789</string></map>"
  b64="$(printf '%s' "$xml" | base64 -w0)"
  adb shell "run-as $PKG sh -c 'mkdir -p shared_prefs && echo $b64 | base64 -d > shared_prefs/config.xml'" || return 1
  adb shell "run-as $PKG cat shared_prefs/config.xml" | grep -q 'name="chat_id"'
}
ci_step config-seed "Seed a throw-away bot config on the device" seed_config

alive()   { [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; }
crashed() { adb logcat -d -b crash | grep -A3 "FATAL EXCEPTION" | grep -q "Process: $PKG"; }
anr()     { adb logcat -d | grep -q "ANR in $PKG"; }

# check_phase <id> <description> : asserts the app survived the phase that just ran
check_phase() {
  local id="$1" desc="$2" rc=0
  sleep 3
  alive   || { echo "::error::app process is gone after: $desc"; rc=1; }
  crashed && { echo "::error::FATAL EXCEPTION after: $desc"; rc=1; }
  anr     && { echo "::error::ANR after: $desc"; rc=1; }
  if [ $rc -eq 0 ]; then ci_record "$id" PASS "$desc"; else ci_record "$id" FAIL "$desc"
    adb logcat -d -t 400 > "$CI_LOG_DIR/$id.log" 2>&1; fi
}

# --- 3. Cold launch -------------------------------------------------------------------------
adb shell am start -W -n "$PKG/.MainActivity" >/dev/null 2>&1 \
  || adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
check_phase cold-launch "Cold launch: app stays alive, no crash/ANR for 8 s"

# --- 4. Lifecycle cycles --------------------------------------------------------------------
for i in $(seq 1 "$CYCLES"); do
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 2
done
check_phase background-foreground "Background/foreground x$CYCLES"

adb shell settings put system accelerometer_rotation 0
for i in $(seq 1 "$CYCLES"); do
  adb shell settings put system user_rotation 1; sleep 2
  adb shell settings put system user_rotation 0; sleep 2
done
check_phase rotation "Rotate portrait/landscape x$CYCLES"

for i in $(seq 1 "$CYCLES"); do
  adb shell input keyevent KEYCODE_SLEEP; sleep 2
  adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard; sleep 2
done
check_phase lock-unlock "Screen off/on x$CYCLES"

# Force-stop kills the process (and leaves the app in the "stopped" state, where it receives no
# broadcasts); the relaunch clears that state again, which the call simulation below relies on.
adb shell am force-stop "$PKG"; sleep 2
adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 8
check_phase process-restart "Force-stop then relaunch: app restarts cleanly"

# --- 5. Simulated calls ---------------------------------------------------------------------
# CallReceiver logs "Dispatching alert" (no numbers, no token) every time it sends a message.
alerts() { adb logcat -d -s CallAlertBot:I | grep -c "Dispatching alert" || true; }

# The broadcast can take a while on a cold emulator, so poll instead of sleeping a fixed time.
wait_for_alerts() {  # wait_for_alerts <count> <seconds>
  local end=$((SECONDS + $2))
  while [ $SECONDS -lt "$end" ]; do
    [ "$(alerts)" -ge "$1" ] && return 0
    sleep 2
  done
  [ "$(alerts)" -ge "$1" ]
}

adb logcat -c
adb emu gsm call 5551234567 >/dev/null 2>&1
if wait_for_alerts 1 60; then
  ci_record incoming-call-alert PASS "Simulated incoming call: the receiver dispatched an alert"
else
  ci_record incoming-call-alert FAIL "Simulated incoming call: no alert was dispatched (see call-alert-logcat.txt)"
  adb logcat -d -t 300 > "$CI_LOG_DIR/incoming-call-alert.log" 2>&1
fi

adb emu gsm cancel 5551234567 >/dev/null 2>&1
if wait_for_alerts 2 60; then
  ci_record missed-call-alert PASS "Simulated missed call: the receiver dispatched a second alert"
else
  ci_record missed-call-alert FAIL "Simulated missed call: no second alert was dispatched (see call-alert-logcat.txt)"
  adb logcat -d -t 300 > "$CI_LOG_DIR/missed-call-alert.log" 2>&1
fi
check_phase call-simulation "App survives the simulated calls"

# --- 6. Snapshots for later inspection ------------------------------------------------------
adb logcat -d -b crash                     > "$OUT/logcat-crash.txt" 2>&1
adb exec-out screencap -p                  > "$OUT/final-screen.png" 2>/dev/null || true
kill $LOGCAT_PID 2>/dev/null
exit 0
