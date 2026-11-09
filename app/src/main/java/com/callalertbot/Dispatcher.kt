package com.callalertbot

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import kotlin.concurrent.thread

/** Applies the user's rules to an alert and sends it to Telegram. Used for calls and text messages. */
object Dispatcher {

    const val TAG = "CallAlertBot"

    /**
     * [text] is what is sent. [logText] is what the activity list shows (so a text message's body is
     * never kept on the phone twice). [number] is used for the VIP list; [known] means it is a saved contact.
     */
    class Alert(
        val kind: String,
        val option: Config.Option,
        val text: String,
        val logText: String,
        val number: String?,
        val known: Boolean
    )

    fun send(context: Context, alert: Alert, pending: BroadcastReceiver.PendingResult?) {
        val app = context.applicationContext
        if (!Config.isOn(app, alert.option)) {
            pending?.finish()
            return
        }
        if (alert.known && Config.isOn(app, Config.Option.UNKNOWN_ONLY)) {
            pending?.finish()
            return
        }

        val vip = alert.number != null && Numbers.matchesAny(alert.number, Config.vipNumbers(app))
        val delivery = Policy.decide(
            vip = vip,
            muted = Config.isMuted(app),
            quietNow = Config.isQuietNow(app),
            quietSkip = Config.isOn(app, Config.Option.QUIET_SKIP),
            silentSetting = Config.isOn(app, Config.Option.SILENT)
        )
        if (delivery == Delivery.SKIP) {
            val reason = if (Config.isMuted(app)) "muted" else "quiet hours"
            ActivityLog.add(app, alert.kind, alert.logText, true, "${ActivityLog.SKIPPED}$reason")
            pending?.finish()
            return
        }

        val token = Config.token(app)
        val chatId = Config.chatId(app)
        thread {
            try {
                // The number and the token are never logged; CI's emulator test looks for this line.
                Log.i(TAG, "Dispatching alert")
                val result = Telegram.sendMessage(token, chatId, alert.text, delivery == Delivery.SILENT)
                if (result.ok) {
                    Log.i(TAG, "Alert sent")
                } else {
                    Log.w(TAG, "Alert failed: ${Telegram.friendly(result).take(120)}")
                }
                ActivityLog.add(
                    app, alert.kind, alert.logText, result.ok,
                    if (result.ok) "" else Telegram.friendly(result)
                )
            } finally {
                pending?.finish()
            }
        }
    }
}
