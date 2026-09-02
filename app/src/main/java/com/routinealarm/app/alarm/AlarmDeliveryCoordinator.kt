package com.routinealarm.app.alarm

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.data.local.OccurrenceClaimDisposition
import com.routinealarm.app.model.AlarmSpec

enum class AlarmDeliveryState {
    IGNORED,
    QUEUED,
    STARTED,
}

data class AlarmDeliveryOutcome(
    val state: AlarmDeliveryState,
    val alarm: AlarmSpec? = null,
    val sessionId: String = "",
)

object AlarmDeliveryCoordinator {
    fun handle(context: Context, intent: Intent): AlarmDeliveryOutcome {
        val requestedId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val requestedTriggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, -1L)
        val requestedRevision = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_REVISION, -1L)
        val occurrenceId = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID).orEmpty()
        val sessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty()
        val occurrenceKind = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_KIND)
            ?.let { stored -> AlarmOccurrenceKind.entries.firstOrNull { it.name == stored } }
            ?: AlarmOccurrenceKind.REGULAR
        val repository = AlarmRepository(context)
        val alarm = repository.load(requestedId)
            ?: return AlarmDeliveryOutcome(AlarmDeliveryState.IGNORED)
        if (!alarm.enabled || occurrenceId.isBlank() || sessionId.isBlank()) {
            return AlarmDeliveryOutcome(AlarmDeliveryState.IGNORED)
        }
        AlarmSessionStore(context).load()?.let { activeSession ->
            repository.ensureSessionOccurrence(
                occurrenceId = activeSession.occurrenceId,
                alarmId = activeSession.alarmId,
                scheduleRevision = activeSession.scheduleRevision,
                sessionId = activeSession.sessionId,
                scheduledAtMillis = activeSession.startedAtMillis,
                snoozed = activeSession.state == AlarmSessionState.SNOOZED,
            )
        }
        val claim = if (occurrenceKind == AlarmOccurrenceKind.REGULAR) {
            repository.claimRegularOccurrence(
                occurrenceId = occurrenceId,
                alarmId = requestedId,
                scheduleRevision = requestedRevision,
                sessionId = sessionId,
                scheduledAtMillis = requestedTriggerAt,
            )
        } else {
            repository.claimSnoozeOccurrence(
                occurrenceId = occurrenceId,
                alarmId = requestedId,
                scheduleRevision = requestedRevision,
                sessionId = sessionId,
            )
        }
        if (claim.disposition == OccurrenceClaimDisposition.STALE) {
            return AlarmDeliveryOutcome(AlarmDeliveryState.IGNORED)
        }
        if (claim.disposition == OccurrenceClaimDisposition.DUPLICATE) {
            return AlarmDeliveryOutcome(AlarmDeliveryState.IGNORED)
        }
        if (occurrenceKind == AlarmOccurrenceKind.REGULAR) {
            AlarmScheduler(context).scheduleNextAfterDelivery(alarm, requestedTriggerAt)
        }
        if (claim.disposition == OccurrenceClaimDisposition.WAIT) {
            return AlarmDeliveryOutcome(AlarmDeliveryState.QUEUED, alarm, sessionId)
        }

        ContextCompat.startForegroundService(
            context,
            AlarmPlaybackService.startIntent(
                context = context,
                alarmId = alarm.id,
                occurrenceKind = occurrenceKind,
                occurrenceId = occurrenceId,
                scheduleRevision = requestedRevision,
                sessionId = sessionId,
                preemptSnooze = claim.disposition ==
                    OccurrenceClaimDisposition.PREEMPT_SNOOZE_AND_START,
            ),
        )
        return AlarmDeliveryOutcome(AlarmDeliveryState.STARTED, alarm, sessionId)
    }
}
