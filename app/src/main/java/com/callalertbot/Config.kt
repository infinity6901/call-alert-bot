package com.callalertbot

import android.content.Context
import android.content.SharedPreferences

/** Stores the Telegram bot token, chat ID and all settings on the device (never in the repo or the APK). */
object Config {
    private const val FILE = "config"
    private const val KEY_TOKEN = "token"
    private const val KEY_CHAT_ID = "chat_id"
    private const val KEY_QUIET_START = "quiet_start"
    private const val KEY_QUIET_END = "quiet_end"
    private const val KEY_SUMMARY_AT = "summary_at"
    private const val KEY_VIP = "vip_numbers"
    private const val KEY_MUTED_UNTIL = "muted_until"

    /** Every on/off setting, with the value it has before the user touches it. */
    enum class Option(val key: String, val default: Boolean) {
        ENABLED("enabled", true),
        INCOMING("alert_incoming", true),
        MISSED("alert_missed", true),
        ENDED("alert_ended", false),
        NAMES("show_names", true),
        UNKNOWN_ONLY("unknown_only", false),
        SILENT("silent", false),
        QUIET("quiet_hours", false),
        QUIET_SKIP("quiet_skip", false),
        COMMANDS("commands", false),
        SUMMARY("daily_summary", false),
        SMS("forward_sms", false)
    }

    const val DEFAULT_QUIET_START = 23 * 60
    const val DEFAULT_QUIET_END = 7 * 60
    const val DEFAULT_SUMMARY_AT = 21 * 60

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

    // Quiet hours and the summary time are stored as minutes since midnight.
    fun quietStart(context: Context): Int = prefs(context).getInt(KEY_QUIET_START, DEFAULT_QUIET_START)
    fun quietEnd(context: Context): Int = prefs(context).getInt(KEY_QUIET_END, DEFAULT_QUIET_END)
    fun summaryAt(context: Context): Int = prefs(context).getInt(KEY_SUMMARY_AT, DEFAULT_SUMMARY_AT)

    fun setQuietStart(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_QUIET_START, minutes).apply()
    fun setQuietEnd(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_QUIET_END, minutes).apply()
    fun setSummaryAt(context: Context, minutes: Int) = prefs(context).edit().putInt(KEY_SUMMARY_AT, minutes).apply()

    /** The VIP list exactly as typed, and parsed into numbers. */
    fun vipText(context: Context): String = prefs(context).getString(KEY_VIP, "") ?: ""
    fun setVipText(context: Context, text: String) = prefs(context).edit().putString(KEY_VIP, text).apply()
    fun vipNumbers(context: Context): List<String> = Numbers.parseList(vipText(context))

    /** Alerts are muted (by the /mute command) until this time; 0 means not muted. */
    fun mutedUntil(context: Context): Long = prefs(context).getLong(KEY_MUTED_UNTIL, 0L)
    fun setMutedUntil(context: Context, millis: Long) = prefs(context).edit().putLong(KEY_MUTED_UNTIL, millis).apply()
    fun isMuted(context: Context, now: Long = System.currentTimeMillis()): Boolean = mutedUntil(context) > now

    fun isQuietNow(context: Context, now: Long = System.currentTimeMillis()): Boolean =
        isOn(context, Option.QUIET) &&
            Schedule.isQuiet(Schedule.minuteOfDay(now), quietStart(context), quietEnd(context))
}
