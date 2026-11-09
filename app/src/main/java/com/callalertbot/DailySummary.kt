package com.callalertbot

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DailySummary {

    /** The summary of today's calls, or an explanation when the call log can't be read. */
    fun text(context: Context, now: Long = System.currentTimeMillis()): String {
        val calls = CallLogReader.since(context, Schedule.startOfDay(now))
            ?: return "I can't read the call log yet. Open the app and grant the call log permission."
        val title = "Call summary · " + SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(now))
        return Summary.build(calls, title) { TimeText.clock(context, it) }
    }

    /** Builds today's summary and sends it. Must be called off the main thread. */
    fun sendNow(context: Context): Telegram.Result {
        val app = context.applicationContext
        val result = Telegram.sendMessage(Config.token(app), Config.chatId(app), text(app))
        ActivityLog.add(
            app, "summary", "Daily summary", result.ok,
            if (result.ok) "" else Telegram.friendly(result)
        )
        return result
    }
}
