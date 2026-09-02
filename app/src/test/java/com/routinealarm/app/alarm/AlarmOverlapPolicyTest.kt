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
    fun waitsBehindAnAlarmThatIsAlreadyRinging() {
        assertEquals(
            OccurrenceClaimDisposition.WAIT,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.FIRING),
        )
        assertEquals(
            OccurrenceClaimDisposition.WAIT,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.CLAIMED),
        )
    }

    @Test
    fun regularAlarmPreemptsAWaitingSnooze() {
        assertEquals(
            OccurrenceClaimDisposition.PREEMPT_SNOOZE_AND_START,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.SNOOZED),
        )
    }

    @Test
    fun snoozeThatHasStartedRingingIsTreatedAsRinging() {
        assertEquals(
            OccurrenceClaimDisposition.WAIT,
            AlarmOverlapPolicy.disposition(AlarmOccurrenceStatus.SNOOZE_CLAIMED),
        )
    }
}
