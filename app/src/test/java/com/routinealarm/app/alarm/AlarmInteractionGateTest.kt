package com.routinealarm.app.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmInteractionGateTest {
    @Test
    fun mediaAndDismissStayLockedBeforeDeadline() {
        assertFalse(
            AlarmInteractionGate.isUnlocked(
                ringStartedAtMillis = 10_000L,
                delaySeconds = 5,
                nowMillis = 14_999L,
            ),
        )
    }

    @Test
    fun mediaAndDismissUnlockExactlyAtDeadline() {
        assertTrue(
            AlarmInteractionGate.isUnlocked(
                ringStartedAtMillis = 10_000L,
                delaySeconds = 5,
                nowMillis = 15_000L,
            ),
        )
    }

    @Test
    fun zeroDelayUnlocksImmediately() {
        assertTrue(AlarmInteractionGate.isUnlocked(10_000L, 0, 10_000L))
        assertTrue(AlarmInteractionGate.isUnlocked(10_000L, 0, 10_001L))
    }
}
