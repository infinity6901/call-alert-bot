package com.callalertbot

import android.content.Context

/** Stores the Telegram bot token and chat ID on the device (never in the repo or the APK). */
object Config {
    private const val FILE = "config"
    private const val KEY_TOKEN = "token"
    private const val KEY_CHAT_ID = "chat_id"

    fun token(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun chatId(context: Context): String =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_CHAT_ID, "") ?: ""

    fun save(context: Context, token: String, chatId: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CHAT_ID, chatId)
            .apply()
    }
}
