package com.callalertbot

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import kotlin.concurrent.thread

/**
 * Listens for phone state changes and forwards them to Telegram.
 *
 * Sequence for a call that is never picked up: RINGING -> IDLE.
 * Sequence for an answered call:               RINGING -> OFFHOOK -> IDLE.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val token = Config.token(context)
        val chatId = Config.chatId(context)
        if (token.isBlank() || chatId.isBlank()) return

        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        val lastState = prefs.getString("last_state", TelephonyManager.EXTRA_STATE_IDLE)
        val now = System.currentTimeMillis()
        var message: String? = null

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Android usually sends RINGING twice: once without the number, then again with it.
                // Treat anything within two minutes of the first one as the same ring.
                val sameRing = lastState == TelephonyManager.EXTRA_STATE_RINGING &&
                    now - prefs.getLong("ring_start", 0L) < RING_WINDOW_MS
                if (!sameRing) {
                    prefs.edit()
                        .putLong("ring_start", now)
                        .putBoolean("notified", false)
                        .putString("number", null)
                        .apply()
                }
                if (!number.isNullOrBlank() && !prefs.getBoolean("notified", false)) {
                    prefs.edit().putBoolean("notified", true).putString("number", number).apply()
                    message = "📞 Incoming call from ${describe(context, number)}"
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
                    val saved = prefs.getString("number", null)
                    message = if (saved != null) {
                        "❌ Missed call from ${describe(context, saved)}"
                    } else {
                        "❌ Missed call (number unavailable - check the Call Log permission)"
                    }
                }
            }
        }

        prefs.edit().putString("last_state", state).apply()

        val text = message ?: return
        val pending = goAsync()
        thread {
            try {
                Telegram.send(token, chatId, text)
            } finally {
                pending.finish()
            }
        }
    }

    private fun describe(context: Context, number: String): String {
        val name = contactName(context, number)
        return if (name != null) "$name ($number)" else number
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
    }
}
