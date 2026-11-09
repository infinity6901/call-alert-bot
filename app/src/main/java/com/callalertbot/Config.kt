package com.callalertbot

import android.content.Context
import android.content.SharedPreferences

/** Stores the Telegram bot token, chat ID and alert options on the device (never in the repo or the APK). */
object Config {
    private const val FILE = "config"
    private const val KEY_TOKEN = "token"
    private const val KEY_CHAT_ID = "chat_id"

    /** Every on/off setting, with the value it has before the user touches it. */
    enum class Option(val key: String, val default: Boolean) {
        ENABLED("enabled", true),
        INCOMING("alert_incoming", true),
        MISSED("alert_missed", true),
        ENDED("alert_ended", false),
        NAMES("show_names", true),
        UNKNOWN_ONLY("unknown_only", false),
        SILENT("silent", false)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun token(context: Context): String = prefs(context).getString(KEY_TOKEN, "") ?: ""

    fun chatId(context: Context): String = prefs(context).getString(KEY_CHAT_ID, "") ?: ""

    fun isConfigured(context: Context): Boolean =
        token(context).isNotBlank() && chatId(context).isNotBlank()

    fun save(context: Context, token: String, chatId: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_CHAT_ID, chatId)
            .apply()
    }

    fun isOn(context: Context, option: Option): Boolean =
        prefs(context).getBoolean(option.key, option.default)

    fun set(context: Context, option: Option, value: Boolean) {
        prefs(context).edit().putBoolean(option.key, value).apply()
    }
}
