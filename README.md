# Call Alert Bot

A tiny Android app that sends a Telegram message when someone calls you and when you miss a call. No third-party automation apps and no server: the phone talks to the Telegram Bot API directly.

Your bot token and chat ID are typed into the app and stored only on your phone. They are not in this repo and not in the APK.

## Get the APK

**From a release (easiest):** open the repo's **Releases** page and download the `.apk` from the latest release.

**From a CI run:** open the **Actions** tab, pick a finished **CI** run, and download the **call-alert-bot-debug-apk** artifact (a zip containing `app-debug.apk`).

Then copy the APK to your phone and install it. Android will ask you to allow installs from your browser or file manager.

## CI

The **CI** workflow (`.github/workflows/build.yml`) runs on every push to `main`, on pull requests, and on version tags. You can also start it by hand from the Actions tab.

| Job | What it does |
| --- | --- |
| **Lint** | Runs `gradle lintDebug`; fails on lint errors and uploads the HTML report. |
| **Build and smoke test** | Builds the debug APK, then `scripts/smoke-test.sh` checks the package name, launcher activity, permissions, call receiver, and that no bot token is embedded. |
| **Emulator tests** | Boots an API 34 emulator and runs the instrumented tests in `app/src/androidTest` (main screen, saving the config, receiver guard). |
| **Publish GitHub release** | Only on `v*` tags, and only if the three jobs above passed. Attaches the APK to a new GitHub release with generated notes. |

### Publishing a release

```
git tag v1.0.0
git push origin v1.0.0
```

The tag name becomes the app's version name (`1.0.0`) and the CI run number becomes the version code. A tag with a dash, such as `v1.1.0-rc1`, is published as a pre-release. The release APK is signed with the debug key, which is fine for sideloading onto your own phone.

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

- `📞 Incoming call from Name (+91...)` when the phone starts ringing
- `❌ Missed call from Name (+91...)` when it stops ringing without being answered (this also covers calls you reject)

## Build locally

Needs JDK 17, the Android SDK and Gradle 8.9:

```
gradle lintDebug
gradle assembleDebug
bash scripts/smoke-test.sh app/build/outputs/apk/debug/app-debug.apk
gradle connectedDebugAndroidTest   # needs an emulator or a connected phone
```

To add the Gradle wrapper, run `gradle wrapper --gradle-version 8.9` once and commit the generated files.

## Notes

- Android only. iOS does not let apps see calls.
- The CI build is a debug APK signed with the standard debug key, which is fine for installing on your own phone.
- Treat the bot token like a password. If it leaks, send `/revoke` to @BotFather.
