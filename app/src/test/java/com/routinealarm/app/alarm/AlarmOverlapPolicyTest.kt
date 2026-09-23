package com.routinealarm.app.alarm

import com.routinealarm.app.data.local.AlarmOccurrenceStatus
import com.routinealarm.app.data.local.AlarmOverlapPolicy
import com.routinealarm.app.data.local.OccurrenceClaimDisposition
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmOverlapPolicyTest {
    @Test
    fun startsWhenNoOccurrenceIsActive() {
        assertEquals(
            OccurrenceClaimDisposition.START,
            AlarmOverlapPolicy.disposition(null),
        )
    }

    @Test
    fun preemptsAnAlarmThatIsAlreadyActive() {
        assertEquals(
            OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.FIRING),
        )
        assertEquals(
            OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.CLAIMED),
        )
    }

    @Test
    fun regularAlarmPreemptsAnySnoozePhase() {
        assertEquals(
            OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.SNOOZED),
        )
        assertEquals(
            OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.SNOOZE_CLAIMED),
        )
    }
}
