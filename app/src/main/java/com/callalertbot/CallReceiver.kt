package com.callalertbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Listens for phone state changes and forwards them to Telegram.
 *
 * Call that is never picked up:  RINGING -> IDLE
 * Call that is answered:         RINGING -> OFFHOOK -> IDLE
 * Call you place yourself:       IDLE -> OFFHOOK -> IDLE
 */
class CallReceiver : BroadcastReceiver() {

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
        val time = TimeText.clock(context, now)
        var alert: Dispatcher.Alert? = null

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
                    alert = callAlert(
                        "incoming", Config.Option.INCOMING,
                        "📞 Incoming call from ${caller.label} · $time", number, caller.known
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
                        callAlert("missed", Config.Option.MISSED, "❌ Missed call from ${caller.label} · $time", saved, caller.known)
                    } else {
                        callAlert("missed", Config.Option.MISSED, "❌ Missed call (number hidden) · $time", null, false)
                    }
                } else if (last == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    val length = Format.duration(now - prefs.getLong("talk_start", now))
                    alert = if (prefs.getString("direction", "in") == "out") {
                        callAlert("ended", Config.Option.ENDED, "📤 Outgoing call ended · $length · $time", null, false)
                    } else {
                        val caller = caller(context, saved)
                        callAlert("ended", Config.Option.ENDED, "✅ Call with ${caller.label} ended · $length · $time", saved, caller.known)
                    }
                }
            }
        }

        prefs.edit().putString("last_state", state).apply()

        val pendingAlert = alert ?: return
        Dispatcher.send(context, pendingAlert, goAsync())
    }

    private fun callAlert(kind: String, option: Config.Option, text: String, number: String?, known: Boolean) =
        Dispatcher.Alert(kind, option, text, text, number, known)

    private fun caller(context: Context, number: String?): Caller {
        if (number.isNullOrBlank()) return Caller("unknown number", false)
        val name = Contacts.name(context, number)
        val showName = Config.isOn(context, Config.Option.NAMES)
        return Caller(if (name != null && showName) "$name ($number)" else number, name != null)
    }

    private companion object {
        const val RING_WINDOW_MS = 2 * 60 * 1000L
    }
}
