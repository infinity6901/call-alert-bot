package com.callalertbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

/**
 * Listens for commands sent to your bot while the phone is on, by asking Telegram for new messages
 * (long polling). Only messages from your own chat are obeyed; everything else is ignored.
 * Android requires the notice in the notification shade while this runs.
 */
class CommandService : Service() {

    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNotice()
        if (!Config.isOn(this, Config.Option.COMMANDS) || !Config.isConfigured(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker?.isAlive != true) {
            running = true
            worker = thread(name = "command-listener") { listen() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    private fun listen() {
        val handler = CommandHandler(applicationContext)
        val state = getSharedPreferences("commands", Context.MODE_PRIVATE)
        var offset = state.getLong("offset", 0L)
        var failures = 0

        while (running) {
            // Read every round, so a changed token or chat ID takes effect without a restart.
            val token = Config.token(this)
            val chatId = Config.chatId(this)
            if (token.isBlank() || chatId.isBlank() || !Config.isOn(this, Config.Option.COMMANDS)) break

            val reply = Telegram.getUpdates(token, offset, POLL_SECONDS)
            if (!reply.ok) {
                failures++
                Log.w(Dispatcher.TAG, "Command listener: ${Telegram.friendly(reply).take(100)}")
                // 5s, 10s, 20s ... up to a minute.
                sleep(minOf(60_000L, 5_000L shl minOf(failures - 1, 4)))
                continue
            }
            failures = 0

            for (update in Telegram.parseUpdates(reply.body)) {
                offset = update.id + 1
                state.edit().putLong("offset", offset).apply()
                // Only your own chat may give commands.
                if (update.chatId != chatId) continue
                val answer = handler.handle(update.text) ?: continue
                Telegram.sendMessage(token, chatId, answer)
                val name = Commands.parse(update.text)?.name.orEmpty()
                ActivityLog.add(applicationContext, "command", "Command /$name", true)
            }
        }
        stopSelf()
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            // The service is being stopped.
        }
    }

    private fun showNotice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notice_channel), NotificationManager.IMPORTANCE_MIN)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notice: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(getString(R.string.notice_title))
            .setContentText(getString(R.string.notice_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTICE_ID, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTICE_ID, notice)
        }
    }

    private companion object {
        const val CHANNEL_ID = "commands"
        const val NOTICE_ID = 1
        const val POLL_SECONDS = 25
    }
}
