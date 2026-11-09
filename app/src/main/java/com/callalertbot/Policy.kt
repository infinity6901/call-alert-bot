package com.callalertbot

enum class Delivery { NORMAL, SILENT, SKIP }

/** Decides how, or whether, an alert is delivered. */
object Policy {

    /**
     * - VIP numbers always come through with sound, even in quiet hours or when muted.
     * - Muted drops everything else.
     * - Quiet hours send silently, or drop the alert when [quietSkip] is on.
     * - Otherwise the "silent messages" setting decides.
     */
    fun decide(
        vip: Boolean,
        muted: Boolean,
        quietNow: Boolean,
        quietSkip: Boolean,
        silentSetting: Boolean
    ): Delivery = when {
        vip -> Delivery.NORMAL
        muted -> Delivery.SKIP
        quietNow -> if (quietSkip) Delivery.SKIP else Delivery.SILENT
        silentSetting -> Delivery.SILENT
        else -> Delivery.NORMAL
    }
}
