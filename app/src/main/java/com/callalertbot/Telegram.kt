package com.callalertbot

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Telegram {

    /**
     * [message] is Telegram's error description (or the network error) when [ok] is false.
     * [code] is the HTTP status, or 0 when the request never got an answer.
     * [body] is the raw JSON reply when [ok] is true; [value] carries a parsed result.
     */
    data class Result(
        val ok: Boolean,
        val message: String = "",
        val code: Int = 0,
        val value: String? = null,
        val body: String = ""
    )

    private val tokenPattern = Regex("""\d{5,}:[A-Za-z0-9_-]{20,}""")
    private val chatKeys = listOf("message", "edited_message", "channel_post", "my_chat_member")

    /**
     * Cleans up what people paste: stray spaces or newlines, a "bot" prefix, or even the whole
     * https://api.telegram.org/bot<token>/getUpdates link.
     */
    fun normalizeToken(raw: String): String {
        val text = raw.trim()
        return tokenPattern.find(text)?.value ?: text.filterNot { it.isWhitespace() }
    }

    /** Sends a message, trying a second time when the failure looks temporary. */
    fun sendMessage(token: String, chatId: String, text: String, silent: Boolean = false): Result {
        var result = Result(false, "not attempted")
        for (attempt in 1..2) {
            result = call(
                token, "sendMessage",
                mapOf("chat_id" to chatId, "text" to text, "disable_notification" to silent.toString())
            )
            if (result.ok || !retryable(result)) break
            if (attempt == 1) Thread.sleep(1500)
        }
        return result
    }

    /** Checks the token with getMe. On success [Result.value] is the bot's username. */
    fun verifyBot(token: String): Result {
        val reply = call(token, "getMe", emptyMap())
        if (!reply.ok) return reply
        val username = try {
            JSONObject(reply.body).getJSONObject("result").optString("username")
        } catch (e: Exception) {
            ""
        }
        return reply.copy(value = username)
    }

    /**
     * Finds the chat the bot last heard from. On success [Result.value] is the chat ID and
     * [Result.message] is the chat's name.
     */
    fun detectChat(token: String): Result {
        val reply = call(token, "getUpdates", mapOf("limit" to "100", "timeout" to "0"))
        if (!reply.ok) return reply
        return try {
            val updates = JSONObject(reply.body).getJSONArray("result")
            for (i in updates.length() - 1 downTo 0) {
                val update = updates.getJSONObject(i)
                for (key in chatKeys) {
                    val chat = update.optJSONObject(key)?.optJSONObject("chat") ?: continue
                    val name = listOf("title", "first_name", "username")
                        .map { chat.optString(it) }
                        .firstOrNull { it.isNotBlank() } ?: ""
                    return reply.copy(value = chat.getLong("id").toString(), message = name)
                }
            }
            Result(false, "No messages found yet. Send any message to your bot in Telegram, then try again.", 200)
        } catch (e: Exception) {
            Result(false, "Telegram sent a reply this app could not read.", 200)
        }
    }

    /** A failure explained in words a person can act on. */
    fun friendly(result: Result): String = when {
        result.code == 401 || result.code == 404 ->
            "Telegram doesn't recognise this bot token. Copy it again from @BotFather."
        result.code == 400 && result.message.contains("chat not found", ignoreCase = true) ->
            "Chat not found. Send your bot a message first, then tap Find my chat ID."
        result.code == 403 ->
            "The bot isn't allowed to message this chat. Open the bot in Telegram and press Start."
        result.code == 0 ->
            "Can't reach Telegram (${result.message}). Check your internet connection."
        else -> result.message
    }

    /** Removes the bot token from text before it is shown or stored. */
    internal fun sanitize(text: String, token: String): String =
        if (token.isBlank()) text else text.replace(token, "***")

    private fun retryable(result: Result) = result.code == 0 || result.code == 429 || result.code >= 500

    private fun call(token: String, method: String, params: Map<String, String>): Result {
        return try {
            val form = params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
            val conn = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 4_000
                conn.readTimeout = 5_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = try { JSONObject(text) } catch (e: Exception) { null }

                if (code in 200..299 && json?.optBoolean("ok") == true) {
                    Result(true, code = code, body = text)
                } else {
                    val description = json?.optString("description").orEmpty()
                    Result(false, description.ifBlank { "HTTP $code" }, code)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Result(false, sanitize(e.message ?: e.javaClass.simpleName, token), 0)
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
