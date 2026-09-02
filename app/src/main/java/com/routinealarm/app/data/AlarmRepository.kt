package com.routinealarm.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.routinealarm.app.data.local.AlarmEntity
import com.routinealarm.app.data.local.AlarmOccurrenceEntity
import com.routinealarm.app.data.local.AlarmOccurrenceStatus
import com.routinealarm.app.data.local.OccurrenceClaim
import com.routinealarm.app.data.local.RoutineAlarmDatabase
import com.routinealarm.app.data.local.RecentContentEntity
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.model.RepeatType
import com.routinealarm.app.model.RestorePolicy
import com.routinealarm.app.model.SoundSource
import com.routinealarm.app.model.VisualKind
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

data class RecentContent(
    val key: String,
    val type: RecentContentType,
    val reference: String,
    val displayName: String?,
    val visualKind: VisualKind?,
    val usedAtMillis: Long,
)

enum class RecentContentType {
    VISUAL_FILE,
    AUDIO_FILE,
    YOUTUBE,
}

class AlarmRepository(private val context: Context) {
    private val dao = RoutineAlarmDatabase.get(context).alarmDao()

    init {
        migrateLegacyOnce()
    }

    fun observeAll(): Flow<List<AlarmSpec>> = dao.observeAlarms().map { rows -> rows.map { it.toModel() } }

    fun observeRecentContents(): Flow<List<RecentContent>> =
        dao.observeRecentContents(RECENT_CONTENT_LIMIT).map { rows ->
            rows.map { it.toModel() }
        }

    fun clearRecentContents() = io { dao.clearRecentContents() }

    fun load(alarmId: Int): AlarmSpec? = io { dao.findAlarm(alarmId)?.toModel() }

    fun enabledAlarms(): List<AlarmSpec> = io { dao.enabledAlarms().map { it.toModel() } }

    fun save(alarm: AlarmSpec): AlarmSpec = io {
        dao.saveAlarm(alarm.toEntity()).toModel().also { saved ->
            rememberRecentContents(saved)
        }
    }

    fun rememberRecentContent(alarm: AlarmSpec) = io {
        rememberRecentContents(alarm)
    }

    fun advanceTriggerAfterDelivery(
        alarmId: Int,
        scheduleRevision: Long,
        deliveredTriggerAtMillis: Long,
        nextTriggerAtMillis: Long,
    ): AlarmSpec? = io {
        val updated = dao.advanceTrigger(
            alarmId = alarmId,
            scheduleRevision = scheduleRevision,
            deliveredTriggerAtMillis = deliveredTriggerAtMillis,
            nextTriggerAtMillis = nextTriggerAtMillis,
            updatedAtMillis = System.currentTimeMillis(),
        )
        if (updated == 1) dao.findAlarm(alarmId)?.toModel() else null
    }

    fun claimRegularOccurrence(
        occurrenceId: String,
        alarmId: Int,
        scheduleRevision: Long,
        sessionId: String,
        scheduledAtMillis: Long,
    ): OccurrenceClaim = io {
        dao.claimRegularOccurrence(
            occurrenceId = occurrenceId,
            alarmId = alarmId,
            scheduleRevision = scheduleRevision,
            sessionId = sessionId,
            scheduledAtMillis = scheduledAtMillis,
            claimedAtMillis = System.currentTimeMillis(),
        )
    }

    fun claimSnoozeOccurrence(
        occurrenceId: String,
        alarmId: Int,
        scheduleRevision: Long,
        sessionId: String,
    ): OccurrenceClaim = io {
        dao.claimSnoozeOccurrence(occurrenceId, alarmId, scheduleRevision, sessionId)
    }

    fun markOccurrenceFiring(occurrenceId: String, sessionId: String): Boolean = io {
        dao.transitionOccurrenceFromAny(
            occurrenceId,
            sessionId,
            listOf(AlarmOccurrenceStatus.CLAIMED, AlarmOccurrenceStatus.SNOOZE_CLAIMED),
            AlarmOccurrenceStatus.FIRING,
        ) == 1
    }

    fun occurrenceIsFiring(occurrenceId: String, sessionId: String): Boolean = io {
        dao.findOccurrence(occurrenceId)?.let {
            it.sessionId == sessionId && it.status == AlarmOccurrenceStatus.FIRING
        } == true
    }

    fun deferClaimedOccurrence(occurrenceId: String, sessionId: String): Boolean = io {
        dao.transitionOccurrence(
            occurrenceId,
            sessionId,
            AlarmOccurrenceStatus.CLAIMED,
            AlarmOccurrenceStatus.WAITING,
        ) == 1
    }

    fun ensureSessionOccurrence(
        occurrenceId: String,
        alarmId: Int,
        scheduleRevision: Long,
        sessionId: String,
        scheduledAtMillis: Long,
        snoozed: Boolean,
    ): Boolean = io {
        val existing = dao.findOccurrence(occurrenceId)
        if (existing != null) {
            existing.alarmId == alarmId && existing.sessionId == sessionId
        } else {
            dao.insertOccurrence(
                AlarmOccurrenceEntity(
                    occurrenceId = occurrenceId,
                    alarmId = alarmId,
                    scheduleRevision = scheduleRevision,
                    sessionId = sessionId,
                    scheduledAtMillis = scheduledAtMillis,
                    claimedAtMillis = System.currentTimeMillis(),
                    status = if (snoozed) {
                        AlarmOccurrenceStatus.SNOOZED
                    } else {
                        AlarmOccurrenceStatus.FIRING
                    },
                ),
            ) != -1L
        }
    }

    fun markOccurrenceSnoozed(occurrenceId: String, sessionId: String): Boolean = io {
        dao.transitionOccurrence(
            occurrenceId,
            sessionId,
            AlarmOccurrenceStatus.FIRING,
            AlarmOccurrenceStatus.SNOOZED,
        ) == 1
    }

    fun restoreOccurrenceFiring(occurrenceId: String, sessionId: String): Boolean = io {
        dao.transitionOccurrence(
            occurrenceId,
            sessionId,
            AlarmOccurrenceStatus.SNOOZED,
            AlarmOccurrenceStatus.FIRING,
        ) == 1
    }

    fun finishOccurrence(
        occurrenceId: String,
        sessionId: String,
        status: String,
    ): Boolean = io {
        dao.transitionOccurrenceFromAny(
            occurrenceId,
            sessionId,
            listOf(
                AlarmOccurrenceStatus.CLAIMED,
                AlarmOccurrenceStatus.FIRING,
                AlarmOccurrenceStatus.SNOOZED,
                AlarmOccurrenceStatus.SNOOZE_CLAIMED,
            ),
            status,
        ) == 1
    }

    fun claimNextWaitingOccurrence(): AlarmOccurrenceEntity? = io {
        dao.claimNextWaitingOccurrence()
    }

    fun setEnabled(alarmId: Int, enabled: Boolean) = io {
        dao.setEnabled(alarmId, enabled, System.currentTimeMillis())
    }

    fun setHomePreviewEnabled(alarmId: Int, enabled: Boolean) = io {
        dao.setHomePreviewEnabled(alarmId, enabled, System.currentTimeMillis())
    }

    fun disable(alarmId: Int) = setEnabled(alarmId, false)

    fun delete(alarmId: Int) = io { dao.deleteAlarm(alarmId) }

    private fun migrateLegacyOnce() {
        val state = context.getSharedPreferences(MIGRATION_PREFERENCES, Context.MODE_PRIVATE)
        if (state.getBoolean(KEY_MIGRATED, false)) return
        val legacy = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        io {
            if (legacy.contains(KEY_TRIGGER_AT)) {
                val trigger = legacy.getLong(KEY_TRIGGER_AT, 0L)
                val local = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())
                val imported = AlarmSpec(
                    id = legacy.getInt(KEY_ID, AlarmSpec.LEGACY_ALARM_ID),
                    label = "기존 알람",
                    triggerAtMillis = trigger,
                    enabled = legacy.getBoolean(KEY_ENABLED, false) && trigger > System.currentTimeMillis(),
                    repeatType = RepeatType.ONE_TIME,
                    localTimeMinutes = local.hour * 60 + local.minute,
                    oneTimeDateEpochDay = local.toLocalDate().toEpochDay(),
                    brightnessPercent = legacy.getInt(KEY_BRIGHTNESS, 80),
                    mediaVolumePercent = legacy.getInt(KEY_VOLUME, 70),
                    dismissDelaySeconds = legacy.getInt(KEY_DISMISS_DELAY, 5),
                    dismissTimerEnabled = legacy.getInt(KEY_DISMISS_DELAY, 5) > 0,
                    restorePolicy = legacy.storedEnum(KEY_RESTORE_POLICY, RestorePolicy.RESTORE_PREVIOUS),
                    contentMode = legacy.storedEnum(KEY_CONTENT_MODE, ContentMode.LOCAL),
                    visualUri = legacy.getString(KEY_VISUAL_URI, null),
                    visualKind = legacy.storedEnum(KEY_VISUAL_KIND, VisualKind.NONE),
                    youtubeUrl = legacy.getString(KEY_YOUTUBE_URL, null),
                )
                dao.saveAlarm(imported.toEntity())
                rememberRecentContents(imported)
            }
        }
        state.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun AlarmSpec.toEntity() = AlarmEntity(
        id = id,
        label = label.trim().ifBlank { "알람" }.take(40),
        triggerAtMillis = triggerAtMillis,
        enabled = enabled,
        repeatType = repeatType.name,
        localTimeMinutes = localTimeMinutes.coerceIn(0, 1439),
        oneTimeDateEpochDay = oneTimeDateEpochDay,
        weekdaysCsv = weekdays.sorted().joinToString(","),
        includeDatesCsv = includeDatesEpochDay.sorted().joinToString(","),
        excludeDatesCsv = excludeDatesEpochDay.sorted().joinToString(","),
        brightnessPercent = brightnessPercent.coerceIn(0, 100),
        mediaVolumePercent = mediaVolumePercent.coerceIn(0, 100),
        dismissDelaySeconds = if (dismissTimerEnabled) {
            dismissDelaySeconds.coerceIn(0, 60)
        } else {
            0
        },
        dismissTimerEnabled = dismissTimerEnabled,
        restorePolicy = restorePolicy.name,
        contentMode = contentMode.name,
        visualUri = visualUri,
        visualKind = visualKind.name,
        soundSource = soundSource.name,
        audioUri = audioUri,
        youtubeUrl = youtubeUrl,
        scheduleRevision = scheduleRevision.coerceAtLeast(1L),
        homePreviewEnabled = homePreviewEnabled,
        updatedAtMillis = System.currentTimeMillis(),
    )

    private fun AlarmEntity.toModel() = AlarmSpec(
        id = id,
        label = label,
        triggerAtMillis = triggerAtMillis,
        enabled = enabled,
        repeatType = parseEnum(repeatType, RepeatType.ONE_TIME),
        localTimeMinutes = localTimeMinutes,
        oneTimeDateEpochDay = oneTimeDateEpochDay,
        weekdays = weekdaysCsv.intSet(),
        includeDatesEpochDay = includeDatesCsv.longSet(),
        excludeDatesEpochDay = excludeDatesCsv.longSet(),
        brightnessPercent = brightnessPercent,
        mediaVolumePercent = mediaVolumePercent,
        dismissDelaySeconds = if (dismissTimerEnabled) {
            dismissDelaySeconds.coerceIn(0, 60)
        } else {
            0
        },
        dismissTimerEnabled = dismissTimerEnabled,
        restorePolicy = parseEnum(restorePolicy, RestorePolicy.RESTORE_PREVIOUS),
        contentMode = parseEnum(contentMode, ContentMode.LOCAL),
        visualUri = visualUri,
        visualKind = parseEnum(visualKind, VisualKind.NONE),
        soundSource = parseEnum(soundSource, SoundSource.DEFAULT_ALARM),
        audioUri = audioUri,
        youtubeUrl = youtubeUrl,
        homePreviewEnabled = homePreviewEnabled,
        scheduleRevision = scheduleRevision,
    )

    private fun RecentContentEntity.toModel() = RecentContent(
        key = key,
        type = parseEnum(type, RecentContentType.VISUAL_FILE),
        reference = reference,
        displayName = displayName,
        visualKind = visualKind?.let { parseEnum(it, VisualKind.NONE) },
        usedAtMillis = usedAtMillis,
    )

    private fun String.intSet(): Set<Int> = split(',').mapNotNull(String::toIntOrNull).toSet()

    private fun String.longSet(): Set<Long> = split(',').mapNotNull(String::toLongOrNull).toSet()

    private suspend fun rememberRecentContents(alarm: AlarmSpec) {
        val now = System.currentTimeMillis()
        if (alarm.contentMode == ContentMode.LOCAL) {
            alarm.visualUri?.takeIf(String::isNotBlank)?.let { reference ->
                rememberRecent(
                    type = RecentContentType.VISUAL_FILE,
                    reference = reference,
                    displayName = fileDisplayName(reference),
                    visualKind = alarm.visualKind,
                    usedAtMillis = now,
                )
            }
            alarm.audioUri?.takeIf(String::isNotBlank)?.let { reference ->
                rememberRecent(
                    type = RecentContentType.AUDIO_FILE,
                    reference = reference,
                    displayName = fileDisplayName(reference),
                    visualKind = null,
                    usedAtMillis = now,
                )
            }
        } else {
            alarm.youtubeUrl?.trim()?.takeIf(String::isNotBlank)?.let { reference ->
                rememberRecent(
                    type = RecentContentType.YOUTUBE,
                    reference = reference,
                    displayName = null,
                    visualKind = null,
                    usedAtMillis = now,
                )
            }
        }
    }

    private suspend fun rememberRecent(
        type: RecentContentType,
        reference: String,
        displayName: String?,
        visualKind: VisualKind?,
        usedAtMillis: Long,
    ) {
        dao.upsertCappedRecentContent(
            RecentContentEntity(
                key = "${type.name}|$reference",
                type = type.name,
                reference = reference,
                displayName = displayName,
                visualKind = visualKind?.name,
                usedAtMillis = usedAtMillis,
            ),
            RECENT_CONTENT_LIMIT,
        )
    }

    private fun fileDisplayName(value: String): String? = runCatching {
        val uri = Uri.parse(value)
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private inline fun <reified T : Enum<T>> parseEnum(stored: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == stored } ?: fallback

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.storedEnum(
        key: String,
        fallback: T,
    ): T = getString(key, null)?.let { parseEnum(it, fallback) } ?: fallback

    private fun <T> io(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private companion object {
        const val RECENT_CONTENT_LIMIT = 30
        const val MIGRATION_PREFERENCES = "local_database_migration"
        const val KEY_MIGRATED = "legacy_alarm_v1"
        const val LEGACY_PREFERENCES = "alarm_spec"
        const val KEY_ID = "id"
        const val KEY_TRIGGER_AT = "trigger_at"
        const val KEY_ENABLED = "enabled"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_VOLUME = "volume"
        const val KEY_DISMISS_DELAY = "dismiss_delay"
        const val KEY_RESTORE_POLICY = "restore_policy"
        const val KEY_CONTENT_MODE = "content_mode"
        const val KEY_VISUAL_URI = "visual_uri"
        const val KEY_VISUAL_KIND = "visual_kind"
        const val KEY_YOUTUBE_URL = "youtube_url"
    }
}
