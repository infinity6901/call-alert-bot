package com.callalertbot

import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Built at runtime so this fake never looks like a real bot token to secret scanners.
    private val testToken = "123456789" + ":AA" + "x".repeat(33)

    @Before
    fun setUp() = reset()

    @After
    fun tearDown() = reset()

    private fun reset() {
        context.getSharedPreferences("config", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("call_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("activity", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun ringing(number: String) =
        Intent(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            .putExtra(TelephonyManager.EXTRA_STATE, TelephonyManager.EXTRA_STATE_RINGING)
            .putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number)

    @Test
    fun mainScreenAsksForSetupWhenNothingIsConfigured() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.status_title)).check(matches(withText(R.string.status_setup_title)))
            onView(withId(R.id.status_subtitle)).check(matches(withText(R.string.status_need_telegram)))
            onView(withText(R.string.section_telegram)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun savingStoresTokenAndChatId() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.token_input)).perform(scrollTo(), replaceText(testToken))
            onView(withId(R.id.chat_input)).perform(scrollTo(), replaceText("987654"))
            closeSoftKeyboard()
            onView(withId(R.id.button_save_test)).perform(scrollTo(), click())
        }
        assertEquals(testToken, Config.token(context))
        assertEquals("987654", Config.chatId(context))
        assertTrue(Config.isConfigured(context))
    }

    @Test
    fun pastedLinkIsCleanedUpToJustTheToken() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.token_input))
                .perform(scrollTo(), replaceText("https://api.telegram.org/bot$testToken/getUpdates"))
            onView(withId(R.id.chat_input)).perform(scrollTo(), replaceText("42"))
            closeSoftKeyboard()
            onView(withId(R.id.button_save_test)).perform(scrollTo(), click())
        }
        assertEquals(testToken, Config.token(context))
    }

    @Test
    fun masterSwitchPausesAndResumesAlerts() {
        assertTrue(Config.isOn(context, Config.Option.ENABLED))
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.switch_enabled)).perform(click())
            assertFalse(Config.isOn(context, Config.Option.ENABLED))
            onView(withId(R.id.status_title)).check(matches(withText(R.string.status_paused_title)))

            onView(withId(R.id.switch_enabled)).perform(click())
            assertTrue(Config.isOn(context, Config.Option.ENABLED))
        }
    }

    @Test
    fun alertOptionsStartWithSensibleDefaults() {
        assertTrue(Config.isOn(context, Config.Option.INCOMING))
        assertTrue(Config.isOn(context, Config.Option.MISSED))
        assertTrue(Config.isOn(context, Config.Option.NAMES))
        assertFalse(Config.isOn(context, Config.Option.ENDED))
        assertFalse(Config.isOn(context, Config.Option.UNKNOWN_ONLY))
        assertFalse(Config.isOn(context, Config.Option.SILENT))
        // Every new feature is opt-in.
        assertFalse(Config.isOn(context, Config.Option.QUIET))
        assertFalse(Config.isOn(context, Config.Option.QUIET_SKIP))
        assertFalse(Config.isOn(context, Config.Option.COMMANDS))
        assertFalse(Config.isOn(context, Config.Option.SUMMARY))
        assertFalse(Config.isOn(context, Config.Option.SMS))
    }

    @Test
    fun muteCommandSetsAndClearsTheMute() {
        val handler = CommandHandler(context)
        assertFalse(Config.isMuted(context))

        val reply = handler.handle("/mute 2h")
        assertTrue(reply!!.contains("Muted"))
        assertTrue(Config.isMuted(context))

        handler.handle("/unmute")
        assertFalse(Config.isMuted(context))
    }

    @Test
    fun badMuteDurationIsRejected() {
        val reply = CommandHandler(context).handle("/mute banana")
        assertTrue(reply!!.contains("/mute 30m"))
        assertFalse(Config.isMuted(context))
    }

    @Test
    fun pauseAndResumeCommandsFlipTheMasterSwitch() {
        val handler = CommandHandler(context)
        handler.handle("/pause")
        assertFalse(Config.isOn(context, Config.Option.ENABLED))
        handler.handle("/resume")
        assertTrue(Config.isOn(context, Config.Option.ENABLED))
    }

    @Test
    fun plainTextAndUnknownCommandsGetTheRightAnswer() {
        val handler = CommandHandler(context)
        assertEquals(null, handler.handle("hello there"))
        assertTrue(handler.handle("/nope")!!.contains("/help"))
        assertTrue(handler.handle("/help")!!.contains("/mute"))
    }

    @Test
    fun smsReceiverDoesNothingWhenForwardingIsOff() {
        Config.save(context, testToken, "123456789")
        SmsReceiver().onReceive(context, Intent("android.provider.Telephony.SMS_RECEIVED"))
        assertTrue(ActivityLog.entries(context).isEmpty())
    }

    @Test
    fun activityLogKeepsNewestFirstAndCanBeCleared() {
        assertTrue(ActivityLog.entries(context).isEmpty())
        ActivityLog.add(context, "incoming", "first", true)
        ActivityLog.add(context, "missed", "second", false, "no connection")

        val entries = ActivityLog.entries(context)
        assertEquals(listOf("second", "first"), entries.map { it.text })
        assertFalse(entries[0].ok)
        assertEquals("no connection", entries[0].detail)

        ActivityLog.clear(context)
        assertTrue(ActivityLog.entries(context).isEmpty())
    }

    @Test
    fun receiverDoesNothingWhenNotConfigured() {
        CallReceiver().onReceive(context, ringing("+911234567890"))

        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        assertFalse("receiver must not track state without a saved token", prefs.contains("last_state"))
    }

    @Test
    fun receiverDoesNothingWhilePaused() {
        Config.save(context, "123456789:AA-ci-test-token-not-real", "123456789")
        Config.set(context, Config.Option.ENABLED, false)

        CallReceiver().onReceive(context, ringing("+911234567890"))

        val prefs = context.getSharedPreferences("call_state", Context.MODE_PRIVATE)
        assertFalse("receiver must ignore calls while alerts are paused", prefs.contains("last_state"))
        assertTrue(ActivityLog.entries(context).isEmpty())
    }
}
