package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class DismissTimerPolicyTest {
    @Test
    fun unknownMediaUsesExistingFallbackMaximum() {
        assertEquals(60, DismissTimerPolicy.maxDelaySeconds(null))
    }

    @Test
    fun knownMediaDurationBecomesMaximumIncludingZero() {
        assertEquals(0, DismissTimerPolicy.maxDelaySeconds(0))
        assertEquals(125, DismissTimerPolicy.maxDelaySeconds(125))
    }

    @Test
    fun delayIsClampedToNonNegativeMediaMaximum() {
        assertEquals(0, DismissTimerPolicy.clampDelaySeconds(-4, 20))
        assertEquals(20, DismissTimerPolicy.clampDelaySeconds(45, 20))
        assertEquals(7, DismissTimerPolicy.clampDelaySeconds(7, 20))
    }

    @Test
    fun contentModeSpecificMaxDelaySeconds() {
        // YouTube with unknown duration gets fallback 300s
        assertEquals(
            DismissTimerPolicy.YOUTUBE_FALLBACK_MAX_DELAY_SECONDS,
            DismissTimerPolicy.maxDelaySecondsForContent(
                contentMode = com.routinealarm.app.model.ContentMode.YOUTUBE,
                mediaDurationSeconds = null,
            ),
        )
        // YouTube with detected duration uses the detected duration
        assertEquals(
            180,
            DismissTimerPolicy.maxDelaySecondsForContent(
                contentMode = com.routinealarm.app.model.ContentMode.YOUTUBE,
                mediaDurationSeconds = 180,
            ),
        )
        // Local with unknown duration gets standard 60s
        assertEquals(
            DismissTimerPolicy.UNKNOWN_MEDIA_MAX_DELAY_SECONDS,
            DismissTimerPolicy.maxDelaySecondsForContent(
                contentMode = com.routinealarm.app.model.ContentMode.LOCAL,
                mediaDurationSeconds = null,
            ),
        )
        // Local with known duration uses the duration
        assertEquals(
            45,
            DismissTimerPolicy.maxDelaySecondsForContent(
                contentMode = com.routinealarm.app.model.ContentMode.LOCAL,
                mediaDurationSeconds = 45,
            ),
        )
    }
}

