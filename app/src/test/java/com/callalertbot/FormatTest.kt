package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun secondsOnly() {
        assertEquals("0s", Format.duration(0))
        assertEquals("45s", Format.duration(45_000))
        assertEquals("59s", Format.duration(59_999))
    }

    @Test
    fun minutesAndSeconds() {
        assertEquals("1m 00s", Format.duration(60_000))
        assertEquals("2m 14s", Format.duration(134_000))
    }

    @Test
    fun hoursMinutesSeconds() {
        assertEquals("1h 02m 05s", Format.duration(3_725_000))
        assertEquals("10h 00m 00s", Format.duration(36_000_000))
    }

    @Test
    fun negativeCountsAsZero() {
        assertEquals("0s", Format.duration(-5_000))
    }
}
