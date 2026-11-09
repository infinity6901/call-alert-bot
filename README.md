# Call Alert Bot

A tiny Android app that sends a Telegram message when someone calls you and when you miss a call. No third-party automation apps and no server: the phone talks to the Telegram Bot API directly.

Your bot token and chat ID are typed into the app and stored only on your phone. They are not in this repo and not in the APK.

## Get the APK

**From a release (easiest):** open the repo's **Releases** page and download the `.apk` from the latest release.

**From a CI run:** open the **Actions** tab, pick a finished **CI** run, and download the **call-alert-bot-debug-apk** artifact (a zip containing `call-alert-bot-debug.apk`).

Then copy the APK to your phone and install it. Android will ask you to allow installs from your browser or file manager.

## Set up Telegram

1. Message **@BotFather**, send `/newbot`, and copy the bot token.
2. Send any message to your new bot.
3. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` and find `"chat":{"id":...}`. That number is your chat ID.

## Set up the app

Open the app and go through the four buttons in order:

1. Enter the bot token and chat ID, then tap **Save**.
2. **Grant permissions** (Phone, Call log, Contacts). Without Call log, Android hides the caller's number.
3. **Send test message** and check Telegram.
4. Open **Battery settings** and set the app to **Unrestricted**, otherwise some phones stop it in the background.

Open the app at least once after installing. Android does not deliver call events to apps that have never been launched.

## What you get

- `📞 Incoming call from Name (+91...) · 10:07 AM` when the phone starts ringing
- `❌ Missed call from Name (+91...) · 10:07 AM` when it stops ringing without being answered (this also covers calls you reject)
- `✅ Call with Name (+91...) ended · 2m 14s · 10:09 AM` when a call ends (off by default; also covers outgoing calls)

Everything can be switched on or off in the app: each kind of alert, contact names, "unknown callers only" (skips numbers saved in your contacts), silent delivery, and a master switch to pause alerts. The app also keeps a short list of recent alerts so you can see whether each one reached Telegram.

### More things it can do (all off by default)

- **Quiet hours**: between the times you pick, alerts arrive silently, or not at all with "Skip alerts in quiet hours". The window can cross midnight.
- **VIP numbers**: numbers (one per line) that always alert with sound, even in quiet hours or while muted. Matching uses the last 10 digits, so `+91 98765 43210` and `098765 43210` are the same number.
- **Telegram commands**: send your bot `/help`, `/status`, `/lastcalls 5`, `/mute 2h`, `/unmute`, `/pause`, `/resume` or `/summary`. This runs a small foreground service (you'll see a notification) that long-polls Telegram, and it only obeys the chat ID you saved. `/mute` silences alerts for a while (up to 7 days) except VIPs; `/pause` is the same as the master switch.
- **Daily summary**: one message a day at the time you choose with the number of calls, talk time, missed calls and the longest caller. It is sent even while alerts are paused.
- **Forward text messages**: sends incoming SMS, including one-time passwords, to your Telegram chat. Anyone who can read that chat can read them, so turn it on only for a private chat. Needs the SMS permission.

Reboots are handled: the command service and the daily summary are restored when the phone starts.

Setup helpers: **Find my chat ID** looks it up for you after you message the bot, **Save & test** checks the token and sends a test message, and you can paste the token with spaces or even the whole `api.telegram.org/bot...` link and the app extracts it.

## CI

The **CI** workflow (`.github/workflows/build.yml`) runs on every push to `main`, on pull requests, and on version tags. You can also start it by hand from the Actions tab. It uses the Gradle wrapper and the runner's preinstalled Android SDK.

Every check runs through `scripts/ci-step.sh`, which records PASS or FAIL and keeps going, so one failing check never hides the results of the ones after it. A final step in each job then fails the job if anything failed.

| Job | What it does |
| --- | --- |
| **Gate 1: build, lint and APK checks** | Kotlin compilation, unit tests, manifest merge plus a check that the merged manifest declares the call receiver and permissions, debug APK build, APK smoke test (`scripts/smoke-test.sh`: package, icon, permissions, entry points, and no bot token compiled in), and Android lint. |
| **Gate 2: emulator checks** | On an API 34 emulator: the instrumented tests, install of the APK Gate 1 built, cold launch, background/foreground, rotation, screen off/on and force-stop/relaunch cycles, crash and ANR detection from logcat, then a simulated incoming call and missed call (`adb emu gsm`). The test bot config is fake, so Telegram rejects the message; what's checked is that the receiver ran end to end. |
| **CI report** | Builds a check-by-check report and a failure report from everything above. |
| **Build signed release APK** | Only on `v*` tags after both gates pass, and only does work when the signing secrets exist. |
| **Publish GitHub release** | Only on `v*` tags, and only if both gates passed. Attaches the APK and the report to a new release. |

### Reading a run

Open a CI run and its **Summary** page shows the CI report (gates, every check as PASS / FAIL / NOT RUN, lint issues, test results) followed by the failure report (the key Gradle errors and the last lines of the log for each failed check, plus details of any failing test).

A check is only PASS if CI actually ran it and it passed. Anything that did not run shows as NOT RUN.

The run also keeps these artifacts: `call-alert-bot-debug-apk`, `call-alert-bot-release-apk` (tags with a key), `build-logs` (a log per check), `lint-reports`, `instrumented-test-reports`, `device-logs` (logcat, crash log, final screenshot), `ci-report` and `failure-report`.

### Publishing a release

```
git tag v1.0.0
git push origin v1.0.0
```

The tag name becomes the app's version name (`1.0.0`) and the CI run number becomes the version code. A tag with a dash, such as `v1.1.0-rc1`, is published as a pre-release.

### Signed releases

Without any setup, the release APK is the debug-signed build, which is fine for sideloading. For a proper release-signed APK:

1. On your own computer (needs a JDK), run `bash scripts/generate-keystore.sh`.
2. Add the four secrets it prints (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) under Settings > Secrets and variables > Actions.
3. Back up `release.keystore` and the password. Every future update must use the same key.

On the next tag, the **Build signed release APK** job builds with that key, runs the APK smoke test and `apksigner verify`, and the release uses it. Android won't install a release-signed APK over a debug-signed one, so uninstall the old build first (once).

## Build locally

Needs JDK 17 and the Android SDK:

```
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
bash scripts/smoke-test.sh app/build/outputs/apk/debug/app-debug.apk
./gradlew connectedDebugAndroidTest   # needs an emulator or a connected phone
```

## Icon

The launcher icon is an adaptive vector icon (a ringing phone handset on a blue gradient). It includes a single-colour layer, so Android 13+ can tint it to match your wallpaper when themed icons are on. The files are in `app/src/main/res/drawable/ic_launcher_*.xml`. The handset is the Material Design "call" glyph (Apache 2.0).

## Notes

- Android only. iOS does not let apps see calls.
- The CI build is a debug APK signed with the standard debug key, which is fine for installing on your own phone.
- Treat the bot token like a password. If it leaks, send `/revoke` to @BotFather.
