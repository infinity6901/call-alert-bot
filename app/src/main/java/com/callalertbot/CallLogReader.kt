package com.callalertbot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog

/** Reads the phone's call log. Every function returns null when the call log permission is missing. */
object CallLogReader {

    /** The newest [count] calls, newest first. */
    fun recent(context: Context, count: Int): List<Summary.Call>? =
        query(context, null, null, count)

    /** All calls since [startMillis], newest first. */
    fun since(context: Context, startMillis: Long): List<Summary.Call>? =
        query(context, "${CallLog.Calls.DATE} >= ?", arrayOf(startMillis.toString()), MAX_ROWS)

    private fun query(context: Context, selection: String?, args: Array<String>?, limit: Int): List<Summary.Call>? {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val columns = arrayOf(
                CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                CallLog.Calls.DATE, CallLog.Calls.DURATION
            )
            // Limiting is done in the loop: a LIMIT inside the sort order is rejected on newer Android versions.
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, columns, selection, args, "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val calls = ArrayList<Summary.Call>()
                while (cursor.moveToNext() && calls.size < limit) {
                    calls.add(
                        Summary.Call(
                            Summary.Kind.fromCallLogType(cursor.getInt(2)),
                            cursor.getString(0).orEmpty(),
                            cursor.getString(1),
                            cursor.getLong(4),
                            cursor.getLong(3)
                        )
                    )
                }
                calls
            } ?: emptyList()
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            emptyList()
        }
    }

    private const val MAX_ROWS = 500
}
