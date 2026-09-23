package com.routinealarm.app.data.local

object AlarmOverlapPolicy {
    fun disposition(activeOccurrenceStatus: String?): OccurrenceClaimDisposition = when (
        activeOccurrenceStatus
    ) {
        null -> OccurrenceClaimDisposition.START
        AlarmOccurrenceStatus.CLAIMED,
        AlarmOccurrenceStatus.FIRING,
        AlarmOccurrenceStatus.SNOOZED,
        AlarmOccurrenceStatus.SNOOZE_CLAIMED,
        -> OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START
        else -> OccurrenceClaimDisposition.START
    }
}
