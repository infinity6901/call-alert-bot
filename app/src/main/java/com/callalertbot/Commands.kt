package com.callalertbot

/** Parsing for the commands you can send to your bot (/status, /mute 2h, ...). */
object Commands {

    data class Parsed(val name: String, val args: List<String>)

    private val whitespace = Regex("\\s+")
    private val duration = Regex("(\\d{1,4})\\s*([mhd]?)")
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR
    private const val MAX_MUTE = 7 * DAY

    /** "/Mute@MyBot 2h" becomes Parsed("mute", ["2h"]). Returns null when the text isn't a command. */
    fun parse(text: String): Parsed? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("/")) return null
        val parts = trimmed.split(whitespace)
        val name = parts[0].removePrefix("/").substringBefore("@").lowercase()
        if (name.isEmpty()) return null
        return Parsed(name, parts.drop(1))
    }

    /**
     * "30m", "2h" and "1d" become milliseconds; a bare number counts as minutes.
     * Returns null for anything else, for zero, and for more than 7 days.
     */
    fun parseDuration(text: String): Long? {
        val match = duration.matchEntire(text.trim().lowercase()) ?: return null
        val amount = match.groupValues[1].toLong()
        if (amount <= 0) return null
        val unit = when (match.groupValues[2]) {
            "h" -> HOUR
            "d" -> DAY
            else -> MINUTE
        }
        return (amount * unit).takeIf { it <= MAX_MUTE }
    }
}
