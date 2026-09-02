package com.routinealarm.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alarm_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarmId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("alarmId"), Index("status")],
)
data class AlarmOccurrenceEntity(
    @PrimaryKey val occurrenceId: String,
    val alarmId: Int,
    val scheduleRevision: Long,
    val sessionId: String,
    val scheduledAtMillis: Long,
    val claimedAtMillis: Long,
    val status: String,
)

enum class OccurrenceClaimDisposition {
    START,
    WAIT,
    PREEMPT_SNOOZE_AND_START,
    DUPLICATE,
    STALE,
}

data class OccurrenceClaim(
    val disposition: OccurrenceClaimDisposition,
    val occurrence: AlarmOccurrenceEntity? = null,
    val displacedOccurrence: AlarmOccurrenceEntity? = null,
)

object AlarmOccurrenceStatus {
    const val CLAIMED = "CLAIMED"
    const val FIRING = "FIRING"
    const val SNOOZED = "SNOOZED"
    const val SNOOZE_CLAIMED = "SNOOZE_CLAIMED"
    const val WAITING = "WAITING"
    const val DISMISSED = "DISMISSED"
    const val CANCELLED = "CANCELLED"
    const val PREEMPTED = "PREEMPTED"
}
