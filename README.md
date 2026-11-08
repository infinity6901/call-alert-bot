# Call Alert Bot

A tiny Android app that sends a Telegram message when someone calls you and when you miss a call. No third-party automation apps and no server: the phone talks to the Telegram Bot API directly.

Your bot token and chat ID are typed into the app and stored only on your phone. They are not in this repo and not in the APK.

## Get the APK

1. Push to `main` (or open the **Actions** tab and run **Build APK** manually).
2. Open the finished run and download the **call-alert-bot-debug-apk** artifact (a zip containing `app-debug.apk`).
3. Copy the APK to your phone and install it. Android will ask you to allow installs from your browser or file manager.

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
gradle assembleDebug
```

To add the Gradle wrapper, run `gradle wrapper --gradle-version 8.9` once and commit the generated files.

## Notes

- Android only. iOS does not let apps see calls.
- The CI build is a debug APK signed with the standard debug key, which is fine for installing on your own phone.
- Treat the bot token like a password. If it leaks, send `/revoke` to @BotFather.
