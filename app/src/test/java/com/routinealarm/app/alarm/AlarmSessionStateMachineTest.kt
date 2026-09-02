package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmSessionStateMachineTest {
    @Test
    fun firstRingCanSnoozeOnce() {
        assertEquals(
            AlarmSessionState.SNOOZED,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.FIRING,
                snoozeCount = 0,
                action = AlarmSessionAction.SNOOZE,
            ),
        )
    }

    @Test
    fun secondSnoozeIsRejected() {
        assertNull(
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.FIRING,
                snoozeCount = 1,
                action = AlarmSessionAction.SNOOZE,
            ),
        )
    }

    @Test
    fun scheduledSnoozeCanResumeOneRing() {
        assertEquals(
            AlarmSessionState.FIRING,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.SNOOZED,
                snoozeCount = 1,
                action = AlarmSessionAction.RESUME_SNOOZE,
            ),
        )
    }

    @Test
    fun duplicateSnoozeDeliveryDoesNotCreateAnotherTransition() {
        assertNull(
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.FIRING,
                snoozeCount = 1,
                action = AlarmSessionAction.RESUME_SNOOZE,
            ),
        )
    }
}
