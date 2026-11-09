package com.callalertbot

/** Phone number helpers, used for the VIP list. */
object Numbers {

    fun digits(text: String): String = text.filter { it.isDigit() }

    /**
     * True when both texts are the same phone number, even if one has a country code or
     * punctuation and the other doesn't: "+91 98765 43210" matches "09876543210".
     * Only the last 10 digits are compared, and very short numbers never match.
     */
    fun sameNumber(a: String, b: String): Boolean {
        val x = digits(a)
        val y = digits(b)
        if (x.length < MIN_DIGITS || y.length < MIN_DIGITS) return false
        val compared = minOf(10, x.length, y.length)
        return x.takeLast(compared) == y.takeLast(compared)
    }

    /** Splits what the user typed (one per line, or separated by commas or semicolons) into numbers. */
    fun parseList(text: String): List<String> =
        text.split('\n', ',', ';')
            .map { it.trim() }
            .filter { digits(it).length >= MIN_DIGITS }

    fun matchesAny(number: String, list: List<String>): Boolean = list.any { sameNumber(number, it) }

    private const val MIN_DIGITS = 6
}
