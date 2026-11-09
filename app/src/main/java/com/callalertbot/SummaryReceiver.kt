package com.callalertbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

/** Fired by the alarm that Features.scheduleSummary sets; sends the summary and sets the next alarm. */
class SummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try {
                if (Config.isOn(app, Config.Option.SUMMARY) && Config.isConfigured(app)) {
                    DailySummary.sendNow(app)
                }
            } finally {
                Features.scheduleSummary(app)
                pending.finish()
            }
        }
    }
}
