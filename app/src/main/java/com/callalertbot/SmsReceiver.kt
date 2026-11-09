package com.callalertbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Forwards incoming text messages (including one-time passwords) to Telegram when that option is on. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!Config.isConfigured(context)) return
        if (!Config.isOn(context, Config.Option.ENABLED) || !Config.isOn(context, Config.Option.SMS)) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val sender = parts[0].originatingAddress.orEmpty()
        // A long message arrives in several parts; join them back into one.
        val body = parts.joinToString("") { it.messageBody.orEmpty() }

        val name = if (sender.isNotBlank()) Contacts.name(context, sender) else null
        val label = when {
            sender.isBlank() -> "unknown sender"
            name != null && Config.isOn(context, Config.Option.NAMES) -> "$name ($sender)"
            else -> sender
        }
        val header = "💬 SMS from $label · ${TimeText.clock(context, System.currentTimeMillis())}"
        // Telegram allows 4096 characters per message.
        val text = "$header\n\n${body.take(MAX_BODY)}"

        Dispatcher.send(
            context,
            Dispatcher.Alert("sms", Config.Option.SMS, text, header, sender.ifBlank { null }, name != null),
            goAsync()
        )
    }

    private companion object {
        const val MAX_BODY = 3500
    }
}
