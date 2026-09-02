package com.routinealarm.app

import java.util.Locale

/** Pure countdown calculations used by the timer screen and JVM tests. */
object CountdownTimerPolicy {
    const val DEFAULT_DURATION_MILLIS = 5 * 60 * 1_000L
    const val MIN_DURATION_SECONDS = 1L
    const val MAX_DURATION_SECONDS = 24 * 60 * 60L

    fun parseDurationSeconds(value: String): Long? = value.trim().toLongOrNull()
        ?.takeIf { it in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS }

    fun remainingMillis(
        baseRemainingMillis: Long,
        startedAtElapsedMillis: Long,
        nowElapsedMillis: Long,
        running: Boolean,
    ): Long {
        if (!running || startedAtElapsedMillis <= 0L) return baseRemainingMillis.coerceAtLeast(0L)
        return (baseRemainingMillis - (nowElapsedMillis - startedAtElapsedMillis))
            .coerceAtLeast(0L)
    }

    fun format(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
