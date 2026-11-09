package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramTest {

    // Built at runtime so this fake never looks like a real bot token to secret scanners.
    private val token = "123456789" + ":AA" + "x".repeat(33)

    @Test
    fun normalizeTokenKeepsAGoodToken() {
        assertEquals(token, Telegram.normalizeToken(token))
    }

    @Test
    fun normalizeTokenTrimsSpacesAndNewlines() {
        assertEquals(token, Telegram.normalizeToken("  $token \n"))
    }

    @Test
    fun normalizeTokenExtractsItFromAPastedLink() {
        assertEquals(token, Telegram.normalizeToken("https://api.telegram.org/bot$token/getUpdates"))
        assertEquals(token, Telegram.normalizeToken("bot$token"))
    }

    @Test
    fun normalizeTokenLeavesAnUnrecognisedValueAlone() {
        assertEquals("not-a-token", Telegram.normalizeToken(" not-a-token "))
        assertEquals("", Telegram.normalizeToken("   "))
    }

    @Test
    fun sanitizeRemovesTheTokenFromErrorText() {
        val cleaned = Telegram.sanitize("failed to connect to /bot$token/sendMessage", token)
        assertFalse(cleaned.contains(token))
        assertTrue(cleaned.contains("***"))
        assertEquals("unchanged", Telegram.sanitize("unchanged", ""))
    }

    @Test
    fun parseUpdatesReadsMessagesAndKeepsOtherUpdatesForTheOffset() {
        val body = """{"ok":true,"result":[
            {"update_id":10,"message":{"message_id":1,"chat":{"id":555,"first_name":"A"},"text":"/status"}},
            {"update_id":11,"edited_message":{"message_id":2,"chat":{"id":555},"text":"x"}},
            {"update_id":12,"message":{"message_id":3,"chat":{"id":-100777},"text":"hello"}}
        ]}"""
        val updates = Telegram.parseUpdates(body)
        assertEquals(listOf(10L, 11L, 12L), updates.map { it.id })
        assertEquals(Telegram.Update(10, "555", "/status"), updates[0])
        assertEquals(Telegram.Update(11, "", ""), updates[1])
        assertEquals("-100777", updates[2].chatId)
    }

    @Test
    fun parseUpdatesToleratesGarbage() {
        assertTrue(Telegram.parseUpdates("not json").isEmpty())
        assertTrue(Telegram.parseUpdates("""{"ok":true}""").isEmpty())
    }

    @Test
    fun friendlyExplainsTheCommonFailures() {
        assertTrue(Telegram.friendly(Telegram.Result(false, "Unauthorized", 401)).contains("bot token"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "Bad Request: chat not found", 400)).contains("Find my chat ID"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "Forbidden: bot was blocked", 403)).contains("Start"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "timeout", 0)).contains("internet"))
        assertEquals("Too Many Requests", Telegram.friendly(Telegram.Result(false, "Too Many Requests", 429)))
    }
}
