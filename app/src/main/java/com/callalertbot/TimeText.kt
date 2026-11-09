package com.callalertbot

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Calendar
import java.util.Date

/** Times written the way the phone is set to show them (12 or 24 hour). */
object TimeText {

    /** "11:00 PM" for 1380. */
    fun minutes(context: Context, minuteOfDay: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        calendar.set(Calendar.MINUTE, minuteOfDay % 60)
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    fun clock(context: Context, millis: Long): String =
        DateFormat.getTimeFormat(context).format(Date(millis))

    /** "9 Nov, 10:07 AM". */
    fun dateTime(context: Context, millis: Long): String =
        DateUtils.formatDateTime(
            context, millis,
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
        )
}
