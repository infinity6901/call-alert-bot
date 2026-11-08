#!/usr/bin/env bash
# Static smoke test for a built APK: package, permissions, entry points,
# and a check that no Telegram bot token got compiled into the app.
set -euo pipefail

APK="${1:?usage: scripts/smoke-test.sh path/to/app.apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "ANDROID_HOME is not set" >&2; exit 1; }
[ -f "$APK" ] || { echo "APK not found: $APK" >&2; exit 1; }

AAPT2="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)aapt2"
[ -x "$AAPT2" ] || { echo "aapt2 not found under $SDK/build-tools" >&2; exit 1; }

badging="$(mktemp)"
manifest="$(mktemp)"
trap 'rm -f "$badging" "$manifest"' EXIT
"$AAPT2" dump badging "$APK" > "$badging"
"$AAPT2" dump xmltree --file AndroidManifest.xml "$APK" > "$manifest"

fail=0
check() { # description, file, fixed string that must be present
  if grep -qF -- "$3" "$2"; then
    echo "PASS  $1"
  else
    echo "FAIL  $1 (missing: $3)"
    fail=1
  fi
}

check "package name" "$badging" "package: name='com.callalertbot'"
check "launcher activity" "$badging" "launchable-activity: name='com.callalertbot.MainActivity'"
for perm in INTERNET READ_PHONE_STATE READ_CALL_LOG READ_CONTACTS; do
  check "permission $perm" "$badging" "uses-permission: name='android.permission.$perm'"
done
check "call receiver declared" "$manifest" "com.callalertbot.CallReceiver"
check "PHONE_STATE intent filter" "$manifest" "android.intent.action.PHONE_STATE"

# Bot tokens look like 123456789:AAxxxxxxxx... and must live on the phone, never in the APK.
hits="$(unzip -p "$APK" 'classes*.dex' | grep -acE '[0-9]{8,10}:AA[A-Za-z0-9_-]{30,}' || true)"
if [ "$hits" -eq 0 ]; then
  echo "PASS  no bot token embedded in the APK"
else
  echo "FAIL  something that looks like a bot token is embedded in the APK"
  fail=1
fi

if [ "$fail" -eq 0 ]; then echo "Smoke test passed"; else echo "Smoke test FAILED"; fi
exit "$fail"
