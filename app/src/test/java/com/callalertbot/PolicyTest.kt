package com.callalertbot

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyTest {

    private fun decide(
        vip: Boolean = false,
        muted: Boolean = false,
        quietNow: Boolean = false,
        quietSkip: Boolean = false,
        silent: Boolean = false
    ) = Policy.decide(vip, muted, quietNow, quietSkip, silent)

    @Test
    fun ordinaryAlertsAreDeliveredNormally() {
        assertEquals(Delivery.NORMAL, decide())
    }

    @Test
    fun silentSettingMakesAlertsSilent() {
        assertEquals(Delivery.SILENT, decide(silent = true))
    }

    @Test
    fun quietHoursSendSilentlyOrSkip() {
        assertEquals(Delivery.SILENT, decide(quietNow = true))
        assertEquals(Delivery.SKIP, decide(quietNow = true, quietSkip = true))
    }

    @Test
    fun mutedDropsEverythingExceptVips() {
        assertEquals(Delivery.SKIP, decide(muted = true))
        assertEquals(Delivery.SKIP, decide(muted = true, silent = true))
    }

    @Test
    fun vipsAlwaysComeThroughWithSound() {
        assertEquals(Delivery.NORMAL, decide(vip = true, muted = true))
        assertEquals(Delivery.NORMAL, decide(vip = true, quietNow = true, quietSkip = true))
        assertEquals(Delivery.NORMAL, decide(vip = true, silent = true))
    }
}
