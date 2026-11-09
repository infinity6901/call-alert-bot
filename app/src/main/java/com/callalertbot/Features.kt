package com.callalertbot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/** Starts and stops the parts of the app that run in the background, to match the settings. */
object Features {

    /** Call after any setting that affects the command listener or the daily summary changes. */
    fun apply(context: Context) {
        val app = context.applicationContext
        updateCommandService(app)
        scheduleSummary(app)
    }

    fun updateCommandService(context: Context) {
        val intent = Intent(context, CommandService::class.java)
        val wanted = Config.isOn(context, Config.Option.COMMANDS) && Config.isConfigured(context)
        if (wanted) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android refuses to start it when the app isn't allowed to right now; the next app launch retries.
                Log.w(Dispatcher.TAG, "Could not start the command listener: ${e.javaClass.simpleName}")
            }
        } else {
            context.stopService(intent)
        }
    }

    /** Sets (or cancels) the alarm for the next daily summary. */
    fun scheduleSummary(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_SUMMARY, Intent(context, SummaryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val wanted = Config.isOn(context, Config.Option.SUMMARY) && Config.isConfigured(context)
        if (!wanted) {
            alarms.cancel(pending)
            return
        }
        val at = Schedule.nextDailyMillis(System.currentTimeMillis(), Config.summaryAt(context))
        // Not an exact alarm (that needs a special permission): Android may deliver it a few minutes late.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private const val REQUEST_SUMMARY = 1
}
