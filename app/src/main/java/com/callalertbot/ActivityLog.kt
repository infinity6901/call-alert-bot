package com.callalertbot

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The last few alerts, kept on the device so the app can show whether they were delivered. */
object ActivityLog {
    data class Entry(val time: Long, val kind: String, val text: String, val ok: Boolean, val detail: String)

    /** Entries whose detail starts with this were deliberately not sent (muted, quiet hours). */
    const val SKIPPED = "skipped: "

    private const val FILE = "activity"
    private const val KEY = "entries"
    private const val MAX = 30

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun add(context: Context, kind: String, text: String, ok: Boolean, detail: String = "") {
        val updated = JSONArray()
        updated.put(
            JSONObject().put("t", System.currentTimeMillis()).put("k", kind)
                .put("x", text).put("ok", ok).put("d", detail)
        )
        val old = read(context)
        for (i in 0 until minOf(old.length(), MAX - 1)) updated.put(old.getJSONObject(i))
        prefs(context).edit().putString(KEY, updated.toString()).apply()
    }

    @Synchronized
    fun entries(context: Context): List<Entry> {
        val array = read(context)
        return (0 until array.length()).map {
            val o = array.getJSONObject(it)
            Entry(o.optLong("t"), o.optString("k"), o.optString("x"), o.optBoolean("ok"), o.optString("d"))
        }
    }

    @Synchronized
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun read(context: Context): JSONArray =
        try {
            JSONArray(prefs(context).getString(KEY, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }
}
