package com.callalertbot

import java.util.Calendar
import java.util.TimeZone

/** Time-of-day logic for quiet hours and the daily summary. Minutes are "minutes since midnight". */
object Schedule {

    /**
     * True when [nowMinute] is inside the quiet window. The window may cross midnight
     * (23:00 to 07:00), and a window whose start equals its end is never active.
     */
    fun isQuiet(nowMinute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> nowMinute in start until end
        else -> nowMinute >= start || nowMinute < end
    }

    fun minuteOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    /** The first moment strictly after [nowMillis] at which the clock reads [minuteOfDay]. */
    fun nextDailyMillis(nowMillis: Long, minuteOfDay: Int, zone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        calendar.set(Calendar.MINUTE, minuteOfDay % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= nowMillis) calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }

    /** Midnight at the start of the day that contains [nowMillis]. */
    fun startOfDay(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
