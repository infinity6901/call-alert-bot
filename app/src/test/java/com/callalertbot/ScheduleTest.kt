package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(utc)
        calendar.clear()
        calendar.set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    @Test
    fun quietWindowThatCrossesMidnight() {
        val start = 23 * 60
        val end = 7 * 60
        assertTrue(Schedule.isQuiet(23 * 60, start, end))
        assertTrue(Schedule.isQuiet(0, start, end))
        assertTrue(Schedule.isQuiet(6 * 60 + 59, start, end))
        assertFalse(Schedule.isQuiet(7 * 60, start, end))
        assertFalse(Schedule.isQuiet(12 * 60, start, end))
        assertFalse(Schedule.isQuiet(22 * 60 + 59, start, end))
    }

    @Test
    fun quietWindowWithinOneDay() {
        assertTrue(Schedule.isQuiet(13 * 60, 13 * 60, 15 * 60))
        assertFalse(Schedule.isQuiet(15 * 60, 13 * 60, 15 * 60))
        assertFalse(Schedule.isQuiet(10 * 60, 13 * 60, 15 * 60))
    }

    @Test
    fun emptyWindowIsNeverQuiet() {
        assertFalse(Schedule.isQuiet(0, 8 * 60, 8 * 60))
        assertFalse(Schedule.isQuiet(8 * 60, 8 * 60, 8 * 60))
    }

    @Test
    fun minuteOfDayReadsTheClock() {
        assertEquals(10 * 60 + 5, Schedule.minuteOfDay(at(9, 10, 5), utc))
    }

    @Test
    fun nextDailyIsLaterToday() {
        assertEquals(at(9, 21, 0), Schedule.nextDailyMillis(at(9, 10, 0), 21 * 60, utc))
    }

    @Test
    fun nextDailyRollsOverToTomorrow() {
        assertEquals(at(10, 9, 0), Schedule.nextDailyMillis(at(9, 10, 0), 9 * 60, utc))
    }

    @Test
    fun nextDailyIsStrictlyAfterNow() {
        assertEquals(at(10, 21, 0), Schedule.nextDailyMillis(at(9, 21, 0), 21 * 60, utc))
    }

    @Test
    fun startOfDayIsMidnight() {
        assertEquals(at(9, 0, 0), Schedule.startOfDay(at(9, 17, 45), utc))
    }
}
