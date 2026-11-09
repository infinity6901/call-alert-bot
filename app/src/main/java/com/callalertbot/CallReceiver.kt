package com.callalertbot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log
import java.text.DateFormat
import java.util.Date
import kotlin.concurrent.thread

/**
 * Listens for phone state changes and forwards them to Telegram.
 *
 * Call that is never picked up:  RINGING -> IDLE
 * Call that is answered:         RINGING -> OFFHOOK -> IDLE
 * Call you place yourself:       IDLE -> OFFHOOK -> IDLE
 */
class CallReceiver : BroadcastReceiver() {

    private class Alert(val kind: String, val option: Config.Option, val text: String, val known: Boolean)

    private class Caller(val label: String, val known: Boolean)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!Config.isConfigured(context)) return
        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        if (!Config.isOn(context, Config.Option.ENABLED)) {
            // Paused: forget any half-finished call so resuming mid-call cannot produce a bogus alert.
            prefs.edit().clear().apply()
            return
        }

        val last = prefs.getString("last_state", TelephonyManager.EXTRA_STATE_IDLE)
            ?: TelephonyManager.EXTRA_STATE_IDLE
        val now = System.currentTimeMillis()
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now))
        var alert: Alert? = null

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android usually sends RINGING twice: once without the number, then again with it.
                // Treat anything within two minutes of the first one as the same ring.
                val sameRing = last == TelephonyManager.EXTRA_STATE_RINGING &&
                    now - prefs.getLong("ring_start", 0L) < RING_WINDOW_MS
                if (!sameRing) {
                    prefs.edit()
                        .putLong("ring_start", now)
                        .putBoolean("notified", false)
                        .putString("number", null)
                        .putString("direction", "in")
                        .apply()
                }
                if (!number.isNullOrBlank() && !prefs.getBoolean("notified", false)) {
                    prefs.edit().putBoolean("notified", true).putString("number", number).apply()
                    val caller = caller(context, number)
                    alert = Alert(
                        "incoming", Config.Option.INCOMING,
                        "📞 Incoming call from ${caller.label} · $time", caller.known
                    )
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (last == TelephonyManager.EXTRA_STATE_RINGING) {
                    prefs.edit().putLong("talk_start", now).putString("direction", "in").apply()
                } else if (last == TelephonyManager.EXTRA_STATE_IDLE) {
                    prefs.edit().putLong("talk_start", now).putString("direction", "out")
                        .putString("number", null).apply()
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val saved = prefs.getString("number", null)
                if (last == TelephonyManager.EXTRA_STATE_RINGING) {
                    alert = if (saved != null) {
                        val caller = caller(context, saved)
                        Alert("missed", Config.Option.MISSED, "❌ Missed call from ${caller.label} · $time", caller.known)
                    } else {
                        Alert("missed", Config.Option.MISSED, "❌ Missed call (number hidden) · $time", false)
                    }
                } else if (last == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    val length = Format.duration(now - prefs.getLong("talk_start", now))
                    alert = if (prefs.getString("direction", "in") == "out") {
                        Alert("ended", Config.Option.ENDED, "📤 Outgoing call ended · $length · $time", false)
                    } else {
                        val caller = caller(context, saved)
                        Alert("ended", Config.Option.ENDED, "✅ Call with ${caller.label} ended · $length · $time", caller.known)
                    }
                }
            }
        }

        prefs.edit().putString("last_state", state).apply()

        val pendingAlert = alert ?: return
        if (!Config.isOn(context, pendingAlert.option)) return
        if (pendingAlert.known && Config.isOn(context, Config.Option.UNKNOWN_ONLY)) return

        val token = Config.token(context)
        val chatId = Config.chatId(context)
        val silent = Config.isOn(context, Config.Option.SILENT)
        val appContext = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                // The number and the token are never logged; CI's emulator test looks for this line.
                Log.i(TAG, "Dispatching alert")
                val result = Telegram.sendMessage(token, chatId, pendingAlert.text, silent)
                if (result.ok) {
                    Log.i(TAG, "Alert sent")
                } else {
                    Log.w(TAG, "Alert failed: ${Telegram.friendly(result).take(120)}")
                }
                ActivityLog.add(
                    appContext, pendingAlert.kind, pendingAlert.text, result.ok,
                    if (result.ok) "" else Telegram.friendly(result)
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun caller(context: Context, number: String?): Caller {
        if (number.isNullOrBlank()) return Caller("unknown number", false)
        val name = contactName(context, number)
        val showName = Config.isOn(context, Config.Option.NAMES)
        return Caller(if (name != null && showName) "$name ($number)" else number, name != null)
    }

    private fun contactName(context: Context, number: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val RING_WINDOW_MS = 2 * 60 * 1000L
        const val TAG = "CallAlertBot"
    }
}
