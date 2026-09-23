package com.routinealarm.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY localTimeMinutes ASC, id ASC")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :alarmId")
    suspend fun findAlarm(alarmId: Int): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY triggerAtMillis ASC")
    suspend fun enabledAlarms(): List<AlarmEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlarm(alarm: AlarmEntity): Long

    @Update
    suspend fun updateAlarm(alarm: AlarmEntity): Int

    @Transaction
    suspend fun saveAlarm(alarm: AlarmEntity): AlarmEntity {
        val current = alarm.id.takeIf { it > 0 }?.let { findAlarm(it) }
        if (current == null) {
            val revision = alarm.scheduleRevision.coerceAtLeast(1L)
            val normalized = alarm.copy(scheduleRevision = revision)
            val insertedId = insertAlarm(normalized).toInt()
            return normalized.copy(id = if (alarm.id == 0) insertedId else alarm.id)
        }
        val updated = alarm.copy(scheduleRevision = current.scheduleRevision + 1L)
        check(updateAlarm(updated) == 1) { "알람 변경 내용을 저장하지 못했습니다." }
        return updated
    }

    @Query(
        "UPDATE alarms SET enabled = :enabled, scheduleRevision = scheduleRevision + 1, " +
            "updatedAtMillis = :updatedAt WHERE id = :alarmId",
    )
    suspend fun setEnabled(alarmId: Int, enabled: Boolean, updatedAt: Long)

    @Query(
        "UPDATE alarms SET homePreviewEnabled = :enabled, updatedAtMillis = :updatedAt " +
            "WHERE id = :alarmId",
    )
    suspend fun setHomePreviewEnabled(alarmId: Int, enabled: Boolean, updatedAt: Long)

    @Query(
        "UPDATE alarms SET triggerAtMillis = :nextTriggerAtMillis, updatedAtMillis = :updatedAtMillis " +
            "WHERE id = :alarmId AND scheduleRevision = :scheduleRevision " +
            "AND triggerAtMillis = :deliveredTriggerAtMillis AND enabled = 1",
    )
    suspend fun advanceTrigger(
        alarmId: Int,
        scheduleRevision: Long,
        deliveredTriggerAtMillis: Long,
        nextTriggerAtMillis: Long,
        updatedAtMillis: Long,
    ): Int

    @Query("DELETE FROM alarms WHERE id = :alarmId")
    suspend fun deleteAlarm(alarmId: Int)

    @Query("SELECT * FROM alarm_occurrences WHERE occurrenceId = :occurrenceId")
    suspend fun findOccurrence(occurrenceId: String): AlarmOccurrenceEntity?

    @Query(
        "SELECT * FROM alarm_occurrences WHERE status IN " +
            "('CLAIMED', 'FIRING', 'SNOOZED', 'SNOOZE_CLAIMED') " +
            "ORDER BY claimedAtMillis ASC LIMIT 1",
    )
    suspend fun findActiveOccurrence(): AlarmOccurrenceEntity?

    @Query(
        "SELECT * FROM alarm_occurrences WHERE status = 'WAITING' " +
            "ORDER BY scheduledAtMillis ASC, claimedAtMillis ASC",
    )
    suspend fun waitingOccurrences(): List<AlarmOccurrenceEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: AlarmOccurrenceEntity): Long

    @Query(
        "UPDATE alarm_occurrences SET status = :newStatus " +
            "WHERE occurrenceId = :occurrenceId AND sessionId = :sessionId AND status = :expectedStatus",
    )
    suspend fun transitionOccurrence(
        occurrenceId: String,
        sessionId: String,
        expectedStatus: String,
        newStatus: String,
    ): Int

    @Query(
        "UPDATE alarm_occurrences SET status = :newStatus " +
            "WHERE occurrenceId = :occurrenceId AND sessionId = :sessionId " +
            "AND status IN (:expectedStatuses)",
    )
    suspend fun transitionOccurrenceFromAny(
        occurrenceId: String,
        sessionId: String,
        expectedStatuses: List<String>,
        newStatus: String,
    ): Int

    @Transaction
    suspend fun claimRegularOccurrence(
        occurrenceId: String,
        alarmId: Int,
        scheduleRevision: Long,
        sessionId: String,
        scheduledAtMillis: Long,
        claimedAtMillis: Long,
    ): OccurrenceClaim {
        val alarm = findAlarm(alarmId)
        if (
            alarm == null ||
            !alarm.enabled ||
            alarm.scheduleRevision != scheduleRevision ||
            alarm.triggerAtMillis != scheduledAtMillis
        ) {
            return OccurrenceClaim(OccurrenceClaimDisposition.STALE)
        }
        findOccurrence(occurrenceId)?.let {
            return OccurrenceClaim(OccurrenceClaimDisposition.DUPLICATE, it)
        }

        // WAITING belongs to the removed FIFO policy. Clear any rows left by
        // an older build before claiming the new-alarm-first occurrence so a
        // later service restart cannot revive them.
        cancelWaitingOccurrences()
        val active = findActiveOccurrence()
        val disposition = AlarmOverlapPolicy.disposition(active?.status)
        if (disposition == OccurrenceClaimDisposition.PREEMPT_ACTIVE_AND_START && active != null) {
            transitionOccurrenceFromAny(
                occurrenceId = active.occurrenceId,
                sessionId = active.sessionId,
                expectedStatuses = listOf(
                    AlarmOccurrenceStatus.CLAIMED,
                    AlarmOccurrenceStatus.FIRING,
                    AlarmOccurrenceStatus.SNOOZED,
                    AlarmOccurrenceStatus.SNOOZE_CLAIMED,
                ),
                newStatus = AlarmOccurrenceStatus.PREEMPTED,
            )
        }
        val occurrence = AlarmOccurrenceEntity(
            occurrenceId = occurrenceId,
            alarmId = alarmId,
            scheduleRevision = scheduleRevision,
            sessionId = sessionId,
            scheduledAtMillis = scheduledAtMillis,
            claimedAtMillis = claimedAtMillis,
            status = AlarmOccurrenceStatus.CLAIMED,
        )
        if (insertOccurrence(occurrence) == -1L) {
            return OccurrenceClaim(
                OccurrenceClaimDisposition.DUPLICATE,
                findOccurrence(occurrenceId),
            )
        }
        return OccurrenceClaim(disposition, occurrence, active)
    }

    @Query(
        "UPDATE alarm_occurrences SET status = 'CANCELLED' " +
            "WHERE status = 'WAITING'",
    )
    suspend fun cancelWaitingOccurrences(): Int

    @Transaction
    suspend fun claimSnoozeOccurrence(
        occurrenceId: String,
        alarmId: Int,
        scheduleRevision: Long,
        sessionId: String,
    ): OccurrenceClaim {
        val alarm = findAlarm(alarmId)
        if (alarm == null || !alarm.enabled || alarm.scheduleRevision != scheduleRevision) {
            return OccurrenceClaim(OccurrenceClaimDisposition.STALE)
        }
        val occurrence = findOccurrence(occurrenceId)
            ?: return OccurrenceClaim(OccurrenceClaimDisposition.STALE)
        if (
            occurrence.alarmId != alarmId ||
            occurrence.scheduleRevision != scheduleRevision ||
            occurrence.sessionId != sessionId
        ) {
            return OccurrenceClaim(OccurrenceClaimDisposition.STALE, occurrence)
        }
        val transitioned = transitionOccurrence(
            occurrenceId,
            sessionId,
            AlarmOccurrenceStatus.SNOOZED,
            AlarmOccurrenceStatus.SNOOZE_CLAIMED,
        )
        return if (transitioned == 1) {
            OccurrenceClaim(
                OccurrenceClaimDisposition.START,
                occurrence.copy(status = AlarmOccurrenceStatus.SNOOZE_CLAIMED),
            )
        } else {
            OccurrenceClaim(OccurrenceClaimDisposition.DUPLICATE, findOccurrence(occurrenceId))
        }
    }

    @Transaction
    suspend fun claimNextWaitingOccurrence(): AlarmOccurrenceEntity? {
        // FIFO waiting was removed in 0.15.8. Cancel legacy rows defensively
        // so no caller can revive an occurrence created by an older build.
        cancelWaitingOccurrences()
        return null
    }

    @Query("SELECT * FROM recent_contents ORDER BY usedAtMillis DESC, `key` ASC LIMIT :limit")
    fun observeRecentContents(limit: Int): Flow<List<RecentContentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentContent(content: RecentContentEntity)

    @Query(
        "DELETE FROM recent_contents WHERE `key` NOT IN " +
            "(SELECT `key` FROM recent_contents ORDER BY usedAtMillis DESC, `key` ASC LIMIT :limit)",
    )
    suspend fun trimRecentContents(limit: Int)

    @Transaction
    suspend fun upsertCappedRecentContent(content: RecentContentEntity, limit: Int) {
        upsertRecentContent(content)
        trimRecentContents(limit)
    }

    @Query("DELETE FROM recent_contents")
    suspend fun clearRecentContents()
}
