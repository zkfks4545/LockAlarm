package com.routinealarm.app.data.local

object AlarmOverlapPolicy {
    fun disposition(activeOccurrenceStatus: String?): OccurrenceClaimDisposition = when (
        activeOccurrenceStatus
    ) {
        null -> OccurrenceClaimDisposition.START
        AlarmOccurrenceStatus.SNOOZED ->
            OccurrenceClaimDisposition.PREEMPT_SNOOZE_AND_START
        else -> OccurrenceClaimDisposition.WAIT
    }
}
