package com.routinealarm.app.alarm

data class AlarmOccurrenceIdentity(
    val occurrenceId: String,
    val sessionId: String,
)

object AlarmOccurrenceIds {
    fun regular(alarmId: Int, scheduleRevision: Long, triggerAtMillis: Long): AlarmOccurrenceIdentity {
        val occurrenceId = "regular:$alarmId:$scheduleRevision:$triggerAtMillis"
        return AlarmOccurrenceIdentity(
            occurrenceId = occurrenceId,
            sessionId = "session:$occurrenceId",
        )
    }

    fun legacy(alarmId: Int, startedAtMillis: Long): AlarmOccurrenceIdentity {
        val occurrenceId = "legacy:$alarmId:$startedAtMillis"
        return AlarmOccurrenceIdentity(
            occurrenceId = occurrenceId,
            sessionId = "session:$occurrenceId",
        )
    }
}
