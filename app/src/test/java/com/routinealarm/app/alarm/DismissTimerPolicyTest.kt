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
}
