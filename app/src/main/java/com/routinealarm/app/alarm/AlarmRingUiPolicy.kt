package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

data class AlarmTimerState(
    val progress: Float,
    val remainingSeconds: Long,
    val isComplete: Boolean,
)

object AlarmTimerPolicy {
    fun remainingSecondsUntil(
        unlockAtMillis: Long,
        nowMillis: Long,
    ): Long {
        if (nowMillis >= unlockAtMillis) return 0L
        val remainingMillis = unlockAtMillis - nowMillis
        return remainingMillis / 1_000L +
            if (remainingMillis % 1_000L == 0L) 0L else 1L
    }

    fun state(
        ringStartedAtMillis: Long,
        delaySeconds: Int,
        nowMillis: Long,
    ): AlarmTimerState {
        val durationMillis = delaySeconds.coerceAtLeast(0).toLong() * 1_000L
        val elapsedMillis = (nowMillis - ringStartedAtMillis).coerceAtLeast(0L)
        if (durationMillis == 0L) {
            return AlarmTimerState(
                progress = 1f,
                remainingSeconds = 0L,
                isComplete = true,
            )
        }
        val progress = (elapsedMillis.toDouble() / durationMillis)
            .coerceIn(0.0, 1.0)
            .toFloat()
        val isComplete = progress >= 1f
        return AlarmTimerState(
            progress = progress,
            remainingSeconds = ceil(
                (durationMillis - elapsedMillis).coerceAtLeast(0L) / 1_000.0,
            ).toLong(),
            isComplete = isComplete,
        )
    }

    fun isComplete(
        ringStartedAtMillis: Long,
        delaySeconds: Int,
        nowMillis: Long,
    ): Boolean = state(ringStartedAtMillis, delaySeconds, nowMillis).isComplete
}

object AlarmClockFormatter {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun format(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        formatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))
}

object AlarmDismissGesturePolicy {
    const val MIN_UPWARD_DISTANCE_DP = 160f

    fun shouldDismiss(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        minimumUpwardDistanceDp: Float = MIN_UPWARD_DISTANCE_DP,
    ): Boolean {
        val upwardDistance = startY - endY
        val horizontalDistance = abs(endX - startX)
        return upwardDistance >= minimumUpwardDistanceDp && upwardDistance > horizontalDistance
    }
}

object AlarmSnoozePolicy {
    fun canSnooze(
        alarm: AlarmSpec,
        session: AlarmSession,
        nowMillis: Long,
    ): Boolean = alarm.enabled &&
        alarm.id == session.alarmId &&
        session.state == AlarmSessionState.FIRING &&
        session.snoozeCount == 0 &&
        AlarmInteractionGate.isUnlocked(
            ringStartedAtMillis = session.ringStartedAtMillis,
            delaySeconds = alarm.dismissDelaySeconds,
            nowMillis = nowMillis,
        )

    fun snoozedSession(
        session: AlarmSession,
        dueAtMillis: Long,
        dueAtElapsedRealtime: Long,
        bootCount: Int,
    ): AlarmSession? = AlarmSessionStateMachine.nextState(
        currentState = session.state,
        snoozeCount = session.snoozeCount,
        action = AlarmSessionAction.SNOOZE,
    )?.let { nextState ->
        session.copy(
            snoozeCount = session.snoozeCount + 1,
            snoozeDueAtMillis = dueAtMillis,
            snoozeDueAtElapsedRealtime = dueAtElapsedRealtime,
            snoozeBootCount = bootCount,
            state = nextState,
        )
    }
}
