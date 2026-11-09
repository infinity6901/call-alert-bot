package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CommandsTest {

    @Test
    fun parsesNameAndArguments() {
        val parsed = Commands.parse("/mute 2h")
        assertNotNull(parsed)
        assertEquals("mute", parsed!!.name)
        assertEquals(listOf("2h"), parsed.args)
    }

    @Test
    fun ignoresCaseAndTheBotSuffix() {
        assertEquals("status", Commands.parse("/Status@MyCallBot")!!.name)
        assertEquals(listOf("10"), Commands.parse("  /lastcalls    10 ")!!.args)
    }

    @Test
    fun plainTextIsNotACommand() {
        assertNull(Commands.parse("hello"))
        assertNull(Commands.parse("/"))
        assertNull(Commands.parse(""))
    }

    @Test
    fun parsesDurations() {
        assertEquals(30 * 60_000L, Commands.parseDuration("30m"))
        assertEquals(2 * 3_600_000L, Commands.parseDuration("2h"))
        assertEquals(86_400_000L, Commands.parseDuration("1d"))
        assertEquals(90 * 60_000L, Commands.parseDuration("90"))
        assertEquals(3_600_000L, Commands.parseDuration(" 1H "))
    }

    @Test
    fun rejectsBadDurations() {
        assertNull(Commands.parseDuration(""))
        assertNull(Commands.parseDuration("soon"))
        assertNull(Commands.parseDuration("0m"))
        assertNull(Commands.parseDuration("-5m"))
        assertNull(Commands.parseDuration("8d"))
        assertNull(Commands.parseDuration("2h30m"))
    }
}
