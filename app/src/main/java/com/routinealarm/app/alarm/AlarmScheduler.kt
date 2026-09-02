package com.routinealarm.app.alarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.routinealarm.app.MainActivity
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.data.local.AlarmOccurrenceStatus
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatType

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(alarm: AlarmSpec) {
        check(canScheduleExactAlarms()) { "정확한 알람 권한이 필요합니다." }
        require(alarm.triggerAtMillis > System.currentTimeMillis()) { "알람 시간은 미래여야 합니다." }

        val identity = AlarmOccurrenceIds.regular(
            alarmId = alarm.id,
            scheduleRevision = alarm.scheduleRevision,
            triggerAtMillis = alarm.triggerAtMillis,
        )
        alarmManager.cancel(legacyBroadcastPendingIntent(alarm.id, AlarmOccurrenceKind.REGULAR))
        alarmManager.cancel(legacyActivityPendingIntent(alarm.id, AlarmOccurrenceKind.REGULAR))
        val showAlarm = PendingIntent.getActivity(
            context,
            alarm.id + SHOW_REQUEST_OFFSET,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, showAlarm),
            alarmPendingIntent(
                alarmId = alarm.id,
                kind = AlarmOccurrenceKind.REGULAR,
                triggerAtMillis = alarm.triggerAtMillis,
                scheduleRevision = alarm.scheduleRevision,
                occurrenceId = identity.occurrenceId,
                sessionId = identity.sessionId,
            ),
        )
    }

    fun scheduleSnooze(session: AlarmSession, triggerAtMillis: Long) {
        check(canScheduleExactAlarms()) { "정확한 알람 권한이 필요합니다." }
        require(triggerAtMillis > System.currentTimeMillis()) { "스누즈 시간은 미래여야 합니다." }
        alarmManager.cancel(
            legacyBroadcastPendingIntent(session.alarmId, AlarmOccurrenceKind.SNOOZE),
        )
        alarmManager.cancel(
            legacyActivityPendingIntent(session.alarmId, AlarmOccurrenceKind.SNOOZE),
        )
        val showAlarm = PendingIntent.getActivity(
            context,
            session.alarmId + SNOOZE_SHOW_REQUEST_OFFSET,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showAlarm),
            alarmPendingIntent(
                alarmId = session.alarmId,
                kind = AlarmOccurrenceKind.SNOOZE,
                triggerAtMillis = triggerAtMillis,
                scheduleRevision = session.scheduleRevision,
                occurrenceId = session.occurrenceId,
                sessionId = session.sessionId,
            ),
        )
    }

    fun cancel(alarmId: Int) {
        alarmManager.cancel(alarmPendingIntent(alarmId, AlarmOccurrenceKind.REGULAR))
        alarmManager.cancel(legacyBroadcastPendingIntent(alarmId, AlarmOccurrenceKind.REGULAR))
        alarmManager.cancel(legacyActivityPendingIntent(alarmId, AlarmOccurrenceKind.REGULAR))
        cancelSnooze(alarmId)
    }

    fun cancelSnooze(alarmId: Int) {
        alarmManager.cancel(alarmPendingIntent(alarmId, AlarmOccurrenceKind.SNOOZE))
        alarmManager.cancel(legacyBroadcastPendingIntent(alarmId, AlarmOccurrenceKind.SNOOZE))
        alarmManager.cancel(legacyActivityPendingIntent(alarmId, AlarmOccurrenceKind.SNOOZE))
    }

    fun scheduleNextAfterDelivery(alarm: AlarmSpec, deliveredTriggerAt: Long) {
        if (alarm.repeatType != RepeatType.WEEKLY) return
        val repository = AlarmRepository(context)
        val next = AlarmScheduleResolver.nextTriggerAtMillis(alarm, deliveredTriggerAt + 1_000L) ?: return
        val updated = repository.advanceTriggerAfterDelivery(
            alarmId = alarm.id,
            scheduleRevision = alarm.scheduleRevision,
            deliveredTriggerAtMillis = deliveredTriggerAt,
            nextTriggerAtMillis = next,
        ) ?: return
        if (canScheduleExactAlarms()) schedule(updated)
    }

    fun rescheduleStoredAlarm(reason: String? = null) {
        val sessionStore = AlarmSessionStore(context)
        val session = sessionStore.load()
        val repository = AlarmRepository(context)
        if (session != null) {
            repository.ensureSessionOccurrence(
                occurrenceId = session.occurrenceId,
                alarmId = session.alarmId,
                scheduleRevision = session.scheduleRevision,
                sessionId = session.sessionId,
                scheduledAtMillis = session.startedAtMillis,
                snoozed = session.state == AlarmSessionState.SNOOZED,
            )
        }
        if (session?.state == AlarmSessionState.SNOOZED) {
            val nowWall = System.currentTimeMillis()
            val sameBoot = session.snoozeBootCount == sessionStore.currentBootCount()
            val dueAtElapsed = session.snoozeDueAtElapsedRealtime
            val effectiveDueAt = if (sameBoot && dueAtElapsed != null) {
                SnoozePolicy.reanchoredWallDueAt(
                    nowWallMillis = nowWall,
                    nowElapsedRealtime = SystemClock.elapsedRealtime(),
                    dueAtElapsedRealtime = dueAtElapsed,
                )
            } else {
                session.snoozeDueAtMillis
            }
            if (effectiveDueAt != null && effectiveDueAt > nowWall) {
                sessionStore.updateSnoozeWallDueAt(effectiveDueAt)
                val notificationFactory = AlarmNotificationFactory(context)
                notificationFactory.createChannel()
                val notification = if (canScheduleExactAlarms()) {
                    scheduleSnooze(session, effectiveDueAt)
                    notificationFactory.buildSnoozed(
                        session.alarmId,
                        session.sessionId,
                        effectiveDueAt,
                    )
                } else {
                    notificationFactory.buildSnoozePermissionBlocked(session.alarmId)
                }
                context.getSystemService(NotificationManager::class.java).notify(
                    AlarmNotificationFactory.notificationId(session.alarmId),
                    notification,
                )
            } else if (effectiveDueAt != null && effectiveDueAt <= nowWall) {
                cancelSnooze(session.alarmId)
                context.getSystemService(NotificationManager::class.java)
                    .cancel(AlarmNotificationFactory.notificationId(session.alarmId))
                repository.finishOccurrence(
                    session.occurrenceId,
                    session.sessionId,
                    AlarmOccurrenceStatus.CANCELLED,
                )
                DeviceStateController(context).finishAndMaybeRestore()
                val alarm = repository.load(session.alarmId)
                if (alarm?.repeatType == RepeatType.ONE_TIME) repository.disable(session.alarmId)
            }
        }
        if (session?.state == AlarmSessionState.FIRING) {
            if (reason == Intent.ACTION_BOOT_COMPLETED || reason == Intent.ACTION_MY_PACKAGE_REPLACED) {
                repository.finishOccurrence(
                    session.occurrenceId,
                    session.sessionId,
                    AlarmOccurrenceStatus.CANCELLED,
                )
                DeviceStateController(context).finishAndMaybeRestore()
                val alarm = repository.load(session.alarmId)
                if (alarm?.repeatType == RepeatType.ONE_TIME) repository.disable(session.alarmId)
            }
        }
        val activeSession = sessionStore.load()
        repository.enabledAlarms().forEach { stored ->
            if (
                stored.id == activeSession?.alarmId &&
                stored.repeatType == RepeatType.ONE_TIME
            ) {
                return@forEach
            }
            val alarm = if (stored.triggerAtMillis <= System.currentTimeMillis()) {
                val next = AlarmScheduleResolver.nextTriggerAtMillis(stored)
                if (next == null) {
                    repository.disable(stored.id)
                    return@forEach
                }
                repository.save(stored.copy(triggerAtMillis = next))
            } else {
                stored
            }
            if (canScheduleExactAlarms()) {
                schedule(alarm)
            }
        }
    }

    private fun alarmPendingIntent(
        alarmId: Int,
        kind: AlarmOccurrenceKind,
        triggerAtMillis: Long = 0L,
        scheduleRevision: Long = 0L,
        occurrenceId: String = "",
        sessionId: String = "",
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId + if (kind == AlarmOccurrenceKind.SNOOZE) SNOOZE_REQUEST_OFFSET else 0,
        Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_OCCURRENCE_KIND, kind.name)
            .putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
            .putExtra(EXTRA_SCHEDULE_REVISION, scheduleRevision)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            .putExtra(EXTRA_SESSION_ID, sessionId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun legacyBroadcastPendingIntent(
        alarmId: Int,
        kind: AlarmOccurrenceKind,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        alarmId + if (kind == AlarmOccurrenceKind.SNOOZE) SNOOZE_REQUEST_OFFSET else 0,
        Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_OCCURRENCE_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun legacyActivityPendingIntent(
        alarmId: Int,
        kind: AlarmOccurrenceKind,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        alarmId + if (kind == AlarmOccurrenceKind.SNOOZE) SNOOZE_REQUEST_OFFSET else 0,
        Intent(context, AlarmDispatchActivity::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_OCCURRENCE_KIND, kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_OCCURRENCE_KIND = "occurrence_kind"
        const val EXTRA_TRIGGER_AT = "trigger_at"
        const val EXTRA_SCHEDULE_REVISION = "schedule_revision"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
        const val EXTRA_SESSION_ID = "session_id"
        private const val SHOW_REQUEST_OFFSET = 20_000
        private const val SNOOZE_REQUEST_OFFSET = 40_000
        private const val SNOOZE_SHOW_REQUEST_OFFSET = 60_000
    }
}

enum class AlarmOccurrenceKind {
    REGULAR,
    SNOOZE,
}
