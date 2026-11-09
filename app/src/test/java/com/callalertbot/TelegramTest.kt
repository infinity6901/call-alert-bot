package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramTest {

    private val token = "123456789:AAHdqTcvCH1vGWJxfSeofSAs0K5PALDsaw"

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
    fun friendlyExplainsTheCommonFailures() {
        assertTrue(Telegram.friendly(Telegram.Result(false, "Unauthorized", 401)).contains("bot token"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "Bad Request: chat not found", 400)).contains("Find my chat ID"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "Forbidden: bot was blocked", 403)).contains("Start"))
        assertTrue(Telegram.friendly(Telegram.Result(false, "timeout", 0)).contains("internet"))
        assertEquals("Too Many Requests", Telegram.friendly(Telegram.Result(false, "Too Many Requests", 429)))
    }
}
