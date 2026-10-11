package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RestorePolicy
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmRingUiPolicyTest {
    @Test
    fun clockUses24HourHoursAndMinuteOnly() {
        assertEquals(
            "12:04",
            AlarmClockFormatter.format(
                epochMillis = Instant.parse("2026-08-13T03:04:05Z").toEpochMilli(),
                zoneId = ZoneId.of("Asia/Seoul"),
            ),
        )
    }

    @Test
    fun timerIsLockedBeforeDeadlineAndCompleteAtDeadline() {
        val before = AlarmTimerPolicy.state(
            ringStartedAtMillis = 10_000L,
            delaySeconds = 5,
            nowMillis = 14_999L,
        )
        val complete = AlarmTimerPolicy.state(
            ringStartedAtMillis = 10_000L,
            delaySeconds = 5,
            nowMillis = 15_000L,
        )

        assertFalse(before.isComplete)
        assertEquals(1L, before.remainingSeconds)
        assertTrue(complete.isComplete)
        assertEquals(1f, complete.progress)
        assertEquals(0L, complete.remainingSeconds)
    }

    @Test
    fun zeroSecondTimerIsCompleteImmediately() {
        val timer = AlarmTimerPolicy.state(
            ringStartedAtMillis = 10_000L,
            delaySeconds = 0,
            nowMillis = 10_000L,
        )

        assertTrue(timer.isComplete)
        assertEquals(1f, timer.progress)
        assertEquals(0L, timer.remainingSeconds)
        assertEquals(10_000L, AlarmInteractionGate.unlockAtMillis(10_000L, 0))
        assertTrue(AlarmInteractionGate.isUnlocked(10_000L, 0, 10_000L))
    }

    @Test
    fun remainingSecondsUntilUnlockCountsDownFromActualDeadline() {
        val unlockAtMillis = 10_000L

        assertEquals(
            5L,
            AlarmTimerPolicy.remainingSecondsUntil(unlockAtMillis, nowMillis = 5_001L),
        )
        assertEquals(
            4L,
            AlarmTimerPolicy.remainingSecondsUntil(unlockAtMillis, nowMillis = 6_001L),
        )
        assertEquals(
            3L,
            AlarmTimerPolicy.remainingSecondsUntil(unlockAtMillis, nowMillis = 7_001L),
        )
        assertEquals(
            1L,
            AlarmTimerPolicy.remainingSecondsUntil(unlockAtMillis, nowMillis = 9_001L),
        )
        assertEquals(
            0L,
            AlarmTimerPolicy.remainingSecondsUntil(unlockAtMillis, nowMillis = unlockAtMillis),
        )
    }

    @Test
    fun swipeNeedsAtLeast160DpOfDominantUpwardTravel() {
        assertTrue(
            AlarmDismissGesturePolicy.shouldDismiss(
                startX = 100f,
                startY = 300f,
                endX = 100f,
                endY = 140f,
            ),
        )
        assertFalse(
            AlarmDismissGesturePolicy.shouldDismiss(
                startX = 100f,
                startY = 300f,
                endX = 100f,
                endY = 141f,
            ),
        )
    }

    @Test
    fun shortOrHorizontalDragIsRejected() {
        assertFalse(
            AlarmDismissGesturePolicy.shouldDismiss(
                startX = 100f,
                startY = 300f,
                endX = 340f,
                endY = 100f,
            ),
        )
        assertFalse(
            AlarmDismissGesturePolicy.shouldDismiss(
                startX = 100f,
                startY = 300f,
                endX = 100f,
                endY = 150f,
            ),
        )
    }

    @Test
    fun snoozeKeepsSessionIdentityAndCanRepeatAfterEachResume() {
        val session = AlarmSession(
            alarmId = 7,
            occurrenceId = "occurrence-7",
            scheduleRevision = 3L,
            sessionId = "session-7",
            startedAtMillis = 1_000L,
            ringStartedAtMillis = 1_000L,
            previousBrightness = 80,
            previousBrightnessMode = 0,
            previousMediaVolume = 50,
            restorePolicy = RestorePolicy.RESTORE_PREVIOUS,
            presetApplied = true,
            snoozeCount = 0,
            snoozeDueAtMillis = null,
            snoozeDueAtElapsedRealtime = null,
            snoozeBootCount = null,
            state = AlarmSessionState.FIRING,
        )
        val alarm = AlarmSpec(
            id = session.alarmId,
            triggerAtMillis = session.startedAtMillis,
            enabled = true,
            dismissDelaySeconds = 5,
        )

        assertTrue(AlarmSnoozePolicy.canSnooze(alarm, session))
        val snoozed = AlarmSnoozePolicy.snoozedSession(
            session = session,
            dueAtMillis = 306_000L,
            dueAtElapsedRealtime = 306_000L,
            bootCount = 4,
        ) ?: error("expected first snooze transition")

        assertEquals(session.sessionId, snoozed.sessionId)
        assertEquals(session.ringStartedAtMillis, snoozed.ringStartedAtMillis)
        assertEquals(1, snoozed.snoozeCount)
        assertFalse(AlarmSnoozePolicy.canSnooze(alarm, snoozed))
        val resumed = snoozed.copy(
            ringStartedAtMillis = 306_000L,
            snoozeDueAtMillis = null,
            snoozeDueAtElapsedRealtime = null,
            snoozeBootCount = null,
            state = AlarmSessionState.FIRING,
        )
        assertTrue(
            AlarmSnoozePolicy.canSnooze(alarm, resumed),
        )
        val secondSnoozed = AlarmSnoozePolicy.snoozedSession(
            session = resumed,
            dueAtMillis = 612_000L,
            dueAtElapsedRealtime = 612_000L,
            bootCount = 4,
        ) ?: error("expected repeated snooze transition")
        assertEquals(2, secondSnoozed.snoozeCount)
    }

    @Test
    fun snoozeIsAvailableBeforeDismissTimerButOnlyForMatchingFiringAlarm() {
        val session = AlarmSession(
            alarmId = 7,
            occurrenceId = "occurrence-7",
            scheduleRevision = 3L,
            sessionId = "session-7",
            startedAtMillis = 1_000L,
            ringStartedAtMillis = 1_000L,
            previousBrightness = 80,
            previousBrightnessMode = 0,
            previousMediaVolume = 50,
            restorePolicy = RestorePolicy.RESTORE_PREVIOUS,
            presetApplied = true,
            snoozeCount = 0,
            snoozeDueAtMillis = null,
            snoozeDueAtElapsedRealtime = null,
            snoozeBootCount = null,
            state = AlarmSessionState.FIRING,
        )
        val alarm = AlarmSpec(id = 7, triggerAtMillis = 1_000L, enabled = true, dismissDelaySeconds = 60)

        assertFalse(AlarmInteractionGate.isUnlocked(1_000L, 60, 1_001L))
        assertTrue(AlarmSnoozePolicy.canSnooze(alarm, session))
        assertFalse(AlarmSnoozePolicy.canSnooze(alarm.copy(id = 8), session))
        assertFalse(AlarmSnoozePolicy.canSnooze(alarm.copy(enabled = false), session))
        assertFalse(AlarmSnoozePolicy.canSnooze(alarm, session.copy(state = AlarmSessionState.SNOOZED)))
    }

    @Test
    fun snoozeConfirmationRejectsOldCycleOrRingingSession() {
        val pending = AlarmSession(
            alarmId = 7,
            occurrenceId = "occurrence-7",
            scheduleRevision = 3L,
            sessionId = "session-7",
            startedAtMillis = 1_000L,
            ringStartedAtMillis = 1_000L,
            previousBrightness = 80,
            previousBrightnessMode = 0,
            previousMediaVolume = 50,
            restorePolicy = RestorePolicy.RESTORE_PREVIOUS,
            presetApplied = true,
            snoozeCount = 2,
            snoozeDueAtMillis = 301_000L,
            snoozeDueAtElapsedRealtime = 301_000L,
            snoozeBootCount = 1,
            state = AlarmSessionState.SNOOZED,
        )

        assertTrue(AlarmSnoozeConfirmationPolicy.matchesPendingSession(pending, 7, "session-7", 2, 301_000L))
        assertFalse(AlarmSnoozeConfirmationPolicy.matchesPendingSession(pending, 7, "session-7", 1, 301_000L))
        assertFalse(AlarmSnoozeConfirmationPolicy.matchesPendingSession(pending, 7, "session-7", 2, 300_000L))
        assertFalse(AlarmSnoozeConfirmationPolicy.matchesPendingSession(pending, 7, "old-session", 2, 301_000L))
        assertFalse(AlarmSnoozeConfirmationPolicy.matchesPendingSession(pending.copy(state = AlarmSessionState.FIRING), 7, "session-7", 2, 301_000L))
        assertFalse(AlarmSnoozeConfirmationPolicy.matchesPendingSession(null, 7, "session-7", 2, 301_000L))
    }
}
