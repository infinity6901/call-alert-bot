package com.callalertbot

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    private fun reset() {
        Config.save(context, "", "")
        context.getSharedPreferences("call_state", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun mainScreenShowsTitleAndUnconfiguredStatus() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Telegram call alerts")).check(matches(isDisplayed()))
            onView(withText(containsString("✗ Bot token and chat ID saved")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun savingStoresTokenAndChatId() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withHint("Bot token (from @BotFather)")).perform(replaceText("123456:TEST-TOKEN"))
            onView(withHint("Chat ID")).perform(replaceText("987654"))
            onView(withText("1. Save")).perform(click())

            onView(withText(containsString("✓ Bot token and chat ID saved")))
                .perform(scrollTo())
                .check(matches(isDisplayed()))
        }
        assertEquals("123456:TEST-TOKEN", Config.token(context))
        assertEquals("987654", Config.chatId(context))
    }

    @Test
    fun receiverDoesNothingWhenNotConfigured() {
        val intent = Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            .putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            .putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, "+911234567890")

        CallReceiver().onReceive(context, intent)

        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        assertFalse("receiver must not track state without a saved token", prefs.contains("last_state"))
    }
}
