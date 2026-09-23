package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmSessionStateMachineTest {
    @Test
    fun firingRingCanSnoozeAtAnyCompletedCycle() {
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
    fun repeatedSnoozeIsAllowed() {
        assertEquals(
            AlarmSessionState.SNOOZED,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.FIRING,
                snoozeCount = 1,
                action = AlarmSessionAction.SNOOZE,
            ),
        )
        assertEquals(
            AlarmSessionState.SNOOZED,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.FIRING,
                snoozeCount = 7,
                action = AlarmSessionAction.SNOOZE,
            ),
        )
    }

    @Test
    fun scheduledSnoozeCanResumeAnyPositiveCycle() {
        assertEquals(
            AlarmSessionState.FIRING,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.SNOOZED,
                snoozeCount = 1,
                action = AlarmSessionAction.RESUME_SNOOZE,
            ),
        )
        assertEquals(
            AlarmSessionState.FIRING,
            AlarmSessionStateMachine.nextState(
                AlarmSessionState.SNOOZED,
                snoozeCount = 7,
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
