package com.callalertbot

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Telegram {

    /** Sends a message through the Bot API. Returns null on success, or a short error description. */
    fun send(token: String, chatId: String, text: String): String? {
        return try {
            val body = "chat_id=${enc(chatId)}&text=${enc(text)}"
            val conn = URL("https://api.telegram.org/bot$token/sendMessage").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code == 200) {
                    null
                } else {
                    val reply = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    "HTTP $code $reply"
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
