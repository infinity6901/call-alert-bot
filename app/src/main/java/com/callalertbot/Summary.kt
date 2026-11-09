package com.callalertbot

/** Turns call log entries into the text of the daily summary and the /lastcalls list. */
object Summary {

    enum class Kind {
        INCOMING, OUTGOING, MISSED, REJECTED, OTHER;

        companion object {
            /** Maps android.provider.CallLog.Calls.TYPE values. */
            fun fromCallLogType(type: Int): Kind = when (type) {
                1 -> INCOMING
                2 -> OUTGOING
                3 -> MISSED
                5 -> REJECTED
                else -> OTHER
            }
        }
    }

    data class Call(val kind: Kind, val number: String, val name: String?, val seconds: Long, val time: Long)

    private const val MAX_MISSED_LISTED = 5

    fun who(call: Call): String {
        val number = call.number.ifBlank { "unknown number" }
        return if (!call.name.isNullOrBlank()) "${call.name} ($number)" else number
    }

    /** One line for /lastcalls, e.g. "↙ Asha (+91...) · 2m 14s · 9 Nov, 10:07 AM". */
    fun describe(call: Call, timeText: (Long) -> String): String {
        val icon = when (call.kind) {
            Kind.INCOMING -> "↙"
            Kind.OUTGOING -> "↗"
            Kind.MISSED -> "❌"
            Kind.REJECTED -> "🚫"
            Kind.OTHER -> "•"
        }
        val length = if (call.seconds > 0) " · ${Format.duration(call.seconds * 1000)}" else ""
        return "$icon ${who(call)}$length · ${timeText(call.time)}"
    }

    /** The message body of the daily summary. [calls] are the day's calls, in any order. */
    fun build(calls: List<Call>, title: String, timeText: (Long) -> String): String {
        val lines = mutableListOf("📊 $title")
        if (calls.isEmpty()) {
            lines += "No calls today."
            return lines.joinToString("\n")
        }

        val incoming = calls.count { it.kind == Kind.INCOMING }
        val outgoing = calls.count { it.kind == Kind.OUTGOING }
        val missed = calls.filter { it.kind == Kind.MISSED }
        val rejected = calls.count { it.kind == Kind.REJECTED }
        val talkSeconds = calls.filter { it.kind == Kind.INCOMING || it.kind == Kind.OUTGOING }.sumOf { it.seconds }

        lines += "${calls.size} ${if (calls.size == 1) "call" else "calls"}, ${Format.duration(talkSeconds * 1000)} on the phone"
        lines += ""
        lines += "↙ Incoming: $incoming"
        lines += "↗ Outgoing: $outgoing"
        lines += "❌ Missed: ${missed.size}"
        if (rejected > 0) lines += "🚫 Rejected: $rejected"

        if (missed.isNotEmpty()) {
            lines += ""
            lines += "Missed calls:"
            for (call in missed.sortedBy { it.time }.take(MAX_MISSED_LISTED)) {
                lines += "• ${who(call)} · ${timeText(call.time)}"
            }
            if (missed.size > MAX_MISSED_LISTED) lines += "• and ${missed.size - MAX_MISSED_LISTED} more"
        }

        val longest = calls
            .filter { it.seconds > 0 }
            .groupBy { Numbers.digits(it.number).takeLast(10).ifEmpty { it.number } }
            .map { (_, group) -> group.first() to group.sumOf { it.seconds } }
            .maxByOrNull { it.second }
        if (longest != null) {
            lines += ""
            lines += "Most time on the phone with: ${who(longest.first)} · ${Format.duration(longest.second * 1000)}"
        }
        return lines.joinToString("\n")
    }
}
