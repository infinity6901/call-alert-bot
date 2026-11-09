package com.callalertbot

object Format {

    /** 134_000 -> "2m 14s", 3_725_000 -> "1h 02m 05s", 45_000 -> "45s". Negative values count as zero. */
    fun duration(millis: Long): String {
        val total = millis.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "${hours}h ${two(minutes)}m ${two(seconds)}s"
            minutes > 0 -> "${minutes}m ${two(seconds)}s"
            else -> "${seconds}s"
        }
    }

    private fun two(value: Long): String = value.toString().padStart(2, '0')
}
