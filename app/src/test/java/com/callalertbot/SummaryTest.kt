package com.callalertbot

import com.callalertbot.Summary.Call
import com.callalertbot.Summary.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTest {

    private val time: (Long) -> String = { "t$it" }

    @Test
    fun mapsCallLogTypes() {
        assertEquals(Kind.INCOMING, Kind.fromCallLogType(1))
        assertEquals(Kind.OUTGOING, Kind.fromCallLogType(2))
        assertEquals(Kind.MISSED, Kind.fromCallLogType(3))
        assertEquals(Kind.REJECTED, Kind.fromCallLogType(5))
        assertEquals(Kind.OTHER, Kind.fromCallLogType(4))
    }

    @Test
    fun describesACallWithNameDurationAndTime() {
        val call = Call(Kind.INCOMING, "+919876543210", "Asha", 134, 7)
        assertEquals("↙ Asha (+919876543210) · 2m 14s · t7", Summary.describe(call, time))
    }

    @Test
    fun missedCallsHaveNoDuration() {
        val call = Call(Kind.MISSED, "5551234567", null, 0, 9)
        assertEquals("❌ 5551234567 · t9", Summary.describe(call, time))
    }

    @Test
    fun anEmptyDayIsSaidPlainly() {
        assertEquals("📊 Today\nNo calls today.", Summary.build(emptyList(), "Today", time))
    }

    @Test
    fun summaryCountsEachKindAndAddsUpTalkTime() {
        val calls = listOf(
            Call(Kind.INCOMING, "+919876543210", "Asha", 134, 1),
            Call(Kind.OUTGOING, "5551234567", null, 60, 2),
            Call(Kind.MISSED, "5559990000", "Ravi", 0, 4),
            Call(Kind.MISSED, "5558880000", null, 0, 3),
            Call(Kind.REJECTED, "5557770000", null, 0, 5)
        )
        val text = Summary.build(calls, "Call summary", time)

        assertTrue(text.contains("5 calls, 3m 14s on the phone"))
        assertTrue(text.contains("↙ Incoming: 1"))
        assertTrue(text.contains("↗ Outgoing: 1"))
        assertTrue(text.contains("❌ Missed: 2"))
        assertTrue(text.contains("🚫 Rejected: 1"))
        assertTrue(text.contains("Most time on the phone with: Asha (+919876543210) · 2m 14s"))
        // Missed calls are listed oldest first.
        assertTrue(text.indexOf("5558880000 · t3") < text.indexOf("Ravi (5559990000) · t4"))
    }

    @Test
    fun rejectedLineIsOmittedWhenThereAreNone() {
        val text = Summary.build(listOf(Call(Kind.INCOMING, "5551234567", null, 10, 1)), "S", time)
        assertFalse(text.contains("Rejected"))
        assertTrue(text.contains("1 call, 10s on the phone"))
    }

    @Test
    fun longMissedListsAreCut() {
        val calls = (1..7).map { Call(Kind.MISSED, "55500000$it", null, 0, it.toLong()) }
        val text = Summary.build(calls, "S", time)
        assertTrue(text.contains("• and 2 more"))
        assertFalse(text.contains("t6"))
    }

    @Test
    fun sameNumberInDifferentFormatsCountsAsOnePerson() {
        val calls = listOf(
            Call(Kind.INCOMING, "+91 98765 43210", null, 100, 1),
            Call(Kind.OUTGOING, "09876543210", null, 100, 2),
            Call(Kind.INCOMING, "5551234567", null, 150, 3)
        )
        val text = Summary.build(calls, "S", time)
        assertTrue(text.contains("Most time on the phone with: +91 98765 43210 · 3m 20s"))
    }
}
