package com.callalertbot

import android.content.Context

/** Answers the commands you send to your bot. Returns the reply text, or null when the text isn't a command. */
class CommandHandler(private val context: Context) {

    fun handle(text: String): String? {
        val command = Commands.parse(text) ?: return null
        return when (command.name) {
            "start", "help" -> help()
            "status" -> status()
            "lastcalls", "last" -> lastCalls(command.args)
            "mute" -> mute(command.args)
            "unmute" -> unmute()
            "pause" -> {
                Config.set(context, Config.Option.ENABLED, false)
                "⏸ Alerts paused. Send /resume to turn them back on."
            }
            "resume" -> {
                Config.set(context, Config.Option.ENABLED, true)
                "▶ Alerts are on again."
            }
            "summary" -> DailySummary.text(context)
            else -> "I don't know /${command.name}. Send /help to see what I can do."
        }
    }

    private fun help(): String = """
        Commands:
        /status - what's on and what isn't
        /lastcalls [n] - your most recent calls (default 5, up to 20)
        /mute [30m|2h|1d] - silence alerts for a while (default 1h)
        /unmute - turn alerts back on
        /pause and /resume - switch alerts off and on
        /summary - today's call summary
    """.trimIndent()

    private fun status(): String {
        val lines = mutableListOf<String>()
        lines += if (Config.isOn(context, Config.Option.ENABLED)) "✅ Alerts are on" else "⏸ Alerts are paused"
        if (Config.isMuted(context)) {
            lines += "🔕 Muted until ${TimeText.clock(context, Config.mutedUntil(context))}"
        }
        lines += if (Config.isOn(context, Config.Option.QUIET)) {
            val how = if (Config.isOn(context, Config.Option.QUIET_SKIP)) "no alerts" else "silent alerts"
            "🌙 Quiet hours ${TimeText.minutes(context, Config.quietStart(context))} to " +
                "${TimeText.minutes(context, Config.quietEnd(context))} ($how)"
        } else {
            "🌙 Quiet hours off"
        }
        val vips = Config.vipNumbers(context).size
        lines += "⭐ VIP numbers: $vips"
        lines += if (Config.isOn(context, Config.Option.SUMMARY)) {
            "📊 Daily summary at ${TimeText.minutes(context, Config.summaryAt(context))}"
        } else {
            "📊 Daily summary off"
        }
        lines += "💬 Text message forwarding " + if (Config.isOn(context, Config.Option.SMS)) "on" else "off"
        return lines.joinToString("\n")
    }

    private fun lastCalls(args: List<String>): String {
        val count = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 20) ?: 5
        val calls = CallLogReader.recent(context, count)
            ?: return "I can't read the call log yet. Open the app and grant the call log permission."
        if (calls.isEmpty()) return "The call log is empty."
        return calls.joinToString("\n") { Summary.describe(it) { time -> TimeText.dateTime(context, time) } }
    }

    private fun mute(args: List<String>): String {
        val text = args.firstOrNull()
        if (text.equals("off", ignoreCase = true)) return unmute()
        val length = if (text == null) HOUR else Commands.parseDuration(text)
            ?: return "Use /mute 30m, /mute 2h or /mute 1d (up to 7 days)."
        val until = System.currentTimeMillis() + length
        Config.setMutedUntil(context, until)
        return "🔕 Muted until ${TimeText.dateTime(context, until)}. " +
            "VIP numbers still come through. Send /unmute to turn alerts back on."
    }

    private fun unmute(): String {
        Config.setMutedUntil(context, 0L)
        return "🔔 Alerts are on again."
    }

    private companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
