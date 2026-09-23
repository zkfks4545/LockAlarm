package com.routinealarm.app.alarm

import android.content.Context
import android.provider.Settings
import com.routinealarm.app.model.RestorePolicy

data class AlarmSession(
    val alarmId: Int,
    val occurrenceId: String,
    val scheduleRevision: Long,
    val sessionId: String,
    val startedAtMillis: Long,
    val ringStartedAtMillis: Long,
    val previousBrightness: Int,
    val previousBrightnessMode: Int,
    val previousMediaVolume: Int,
    val restorePolicy: RestorePolicy,
    val presetApplied: Boolean,
    val snoozeCount: Int,
    val snoozeDueAtMillis: Long?,
    val snoozeDueAtElapsedRealtime: Long?,
    val snoozeBootCount: Int?,
    val state: AlarmSessionState,
)

enum class AlarmSessionState {
    FIRING,
    SNOOZED,
}

class AlarmSessionStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AlarmSession? {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return null
        val alarmId = preferences.getInt(KEY_ALARM_ID, -1)
        val startedAtMillis = preferences.getLong(KEY_STARTED_AT, System.currentTimeMillis())
        val legacyIdentity = AlarmOccurrenceIds.legacy(alarmId, startedAtMillis)
        return AlarmSession(
            alarmId = alarmId,
            occurrenceId = preferences.getString(KEY_OCCURRENCE_ID, null)
                ?: legacyIdentity.occurrenceId,
            scheduleRevision = preferences.getLong(KEY_SCHEDULE_REVISION, 1L),
            sessionId = preferences.getString(KEY_SESSION_ID, null) ?: legacyIdentity.sessionId,
            startedAtMillis = startedAtMillis,
            ringStartedAtMillis = preferences.getLong(
                KEY_RING_STARTED_AT,
                preferences.getLong(KEY_STARTED_AT, System.currentTimeMillis()),
            ),
            previousBrightness = preferences.getInt(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS),
            previousBrightnessMode = preferences.getInt(KEY_BRIGHTNESS_MODE, DEFAULT_BRIGHTNESS_MODE),
            previousMediaVolume = preferences.getInt(KEY_MEDIA_VOLUME, 0),
            restorePolicy = preferences.getString(KEY_RESTORE_POLICY, null)
                ?.let { stored -> RestorePolicy.entries.firstOrNull { it.name == stored } }
                ?: RestorePolicy.RESTORE_PREVIOUS,
            presetApplied = preferences.getBoolean(KEY_PRESET_APPLIED, true),
            snoozeCount = preferences.getInt(KEY_SNOOZE_COUNT, 0),
            snoozeDueAtMillis = preferences.getLong(KEY_SNOOZE_DUE_AT, NO_SNOOZE)
                .takeIf { it != NO_SNOOZE },
            snoozeDueAtElapsedRealtime = preferences.getLong(
                KEY_SNOOZE_DUE_AT_ELAPSED,
                NO_SNOOZE,
            ).takeIf { it != NO_SNOOZE },
            snoozeBootCount = preferences.getInt(KEY_SNOOZE_BOOT_COUNT, NO_BOOT_COUNT)
                .takeIf { it != NO_BOOT_COUNT },
            state = preferences.getString(KEY_STATE, null)
                ?.let { stored -> AlarmSessionState.entries.firstOrNull { it.name == stored } }
                ?: AlarmSessionState.FIRING,
        )
    }

    fun begin(session: AlarmSession) {
        preferences.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_ALARM_ID, session.alarmId)
            .putString(KEY_OCCURRENCE_ID, session.occurrenceId)
            .putLong(KEY_SCHEDULE_REVISION, session.scheduleRevision)
            .putString(KEY_SESSION_ID, session.sessionId)
            .putLong(KEY_STARTED_AT, session.startedAtMillis)
            .putLong(KEY_RING_STARTED_AT, session.ringStartedAtMillis)
            .putInt(KEY_BRIGHTNESS, session.previousBrightness)
            .putInt(KEY_BRIGHTNESS_MODE, session.previousBrightnessMode)
            .putInt(KEY_MEDIA_VOLUME, session.previousMediaVolume)
            .putString(KEY_RESTORE_POLICY, session.restorePolicy.name)
            .putBoolean(KEY_PRESET_APPLIED, session.presetApplied)
            .putInt(KEY_SNOOZE_COUNT, session.snoozeCount)
            .putLong(KEY_SNOOZE_DUE_AT, session.snoozeDueAtMillis ?: NO_SNOOZE)
            .putLong(
                KEY_SNOOZE_DUE_AT_ELAPSED,
                session.snoozeDueAtElapsedRealtime ?: NO_SNOOZE,
            )
            .putInt(KEY_SNOOZE_BOOT_COUNT, session.snoozeBootCount ?: NO_BOOT_COUNT)
            .putString(KEY_STATE, session.state.name)
            .commit()
    }

    fun markSnoozed(
        alarmId: Int,
        dueAtMillis: Long,
        dueAtElapsedRealtime: Long,
        bootCount: Int,
    ): Boolean {
        val session = load() ?: return false
        if (session.alarmId != alarmId || session.state != AlarmSessionState.FIRING) return false
        val nextSession = AlarmSnoozePolicy.snoozedSession(
            session = session,
            dueAtMillis = dueAtMillis,
            dueAtElapsedRealtime = dueAtElapsedRealtime,
            bootCount = bootCount,
        ) ?: return false
        begin(nextSession)
        return true
    }

    fun resumeSnoozed(
        ticket: SnoozeDeliveryTicket,
        clock: SnoozeDeliveryClock,
    ): Boolean {
        val session = load() ?: return false
        if (!SnoozeDeliveryValidation.acceptsPendingSession(session, ticket, clock)) {
            return false
        }
        val nextState = AlarmSessionStateMachine.nextState(
            currentState = session.state,
            snoozeCount = session.snoozeCount,
            action = AlarmSessionAction.RESUME_SNOOZE,
        ) ?: return false
        begin(
            session.copy(
                ringStartedAtMillis = clock.nowWallMillis,
                snoozeDueAtMillis = null,
                snoozeDueAtElapsedRealtime = null,
                snoozeBootCount = null,
                state = nextState,
            ),
        )
        return true
    }

    fun updateSnoozeWallDueAt(dueAtMillis: Long) {
        val session = load() ?: return
        if (session.state != AlarmSessionState.SNOOZED) return
        begin(session.copy(snoozeDueAtMillis = dueAtMillis))
    }

    fun markPresetApplied(alarmId: Int) {
        val session = load() ?: return
        if (session.alarmId != alarmId || session.presetApplied) return
        begin(session.copy(presetApplied = true))
    }

    fun currentBootCount(): Int = Settings.Global.getInt(
        context.contentResolver,
        Settings.Global.BOOT_COUNT,
        NO_BOOT_COUNT,
    )

    fun clear() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "active_alarm_session"
        const val KEY_ACTIVE = "active"
        const val KEY_ALARM_ID = "alarm_id"
        const val KEY_OCCURRENCE_ID = "occurrence_id"
        const val KEY_SCHEDULE_REVISION = "schedule_revision"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_RING_STARTED_AT = "ring_started_at"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_BRIGHTNESS_MODE = "brightness_mode"
        const val KEY_MEDIA_VOLUME = "media_volume"
        const val KEY_RESTORE_POLICY = "restore_policy"
        const val KEY_PRESET_APPLIED = "preset_applied"
        const val KEY_SNOOZE_COUNT = "snooze_count"
        const val KEY_SNOOZE_DUE_AT = "snooze_due_at"
        const val KEY_SNOOZE_DUE_AT_ELAPSED = "snooze_due_at_elapsed"
        const val KEY_SNOOZE_BOOT_COUNT = "snooze_boot_count"
        const val KEY_STATE = "state"
        const val DEFAULT_BRIGHTNESS = 128
        const val DEFAULT_BRIGHTNESS_MODE = 0
        const val NO_SNOOZE = -1L
        const val NO_BOOT_COUNT = -1
    }
}
