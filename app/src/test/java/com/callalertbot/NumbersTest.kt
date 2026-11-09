package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumbersTest {

    @Test
    fun sameNumberIgnoresCountryCodeAndPunctuation() {
        assertTrue(Numbers.sameNumber("+91 98765 43210", "09876543210"))
        assertTrue(Numbers.sameNumber("98765-43210", "+919876543210"))
        assertTrue(Numbers.sameNumber("(555) 123-4567", "5551234567"))
    }

    @Test
    fun differentNumbersDoNotMatch() {
        assertFalse(Numbers.sameNumber("+91 98765 43210", "+91 98765 43211"))
        assertFalse(Numbers.sameNumber("5551234567", "5551234568"))
    }

    @Test
    fun veryShortNumbersNeverMatch() {
        assertFalse(Numbers.sameNumber("123", "123"))
        assertFalse(Numbers.sameNumber("", ""))
    }

    @Test
    fun parseListAcceptsLinesCommasAndSemicolons() {
        val list = Numbers.parseList("+91 98765 43210\n5551234567, 0123456789;  junk ,12")
        assertEquals(listOf("+91 98765 43210", "5551234567", "0123456789"), list)
    }

    @Test
    fun matchesAnyChecksTheWholeList() {
        val vips = Numbers.parseList("+91 98765 43210\n5551234567")
        assertTrue(Numbers.matchesAny("05551234567", vips))
        assertFalse(Numbers.matchesAny("5559999999", vips))
        assertFalse(Numbers.matchesAny("5551234567", emptyList()))
    }
}
