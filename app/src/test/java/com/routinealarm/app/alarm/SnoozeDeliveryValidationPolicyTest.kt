package com.routinealarm.app.alarm

import com.routinealarm.app.model.RestorePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeDeliveryValidationPolicyTest {
    private val session = AlarmSession(
        alarmId = 7,
        occurrenceId = "occurrence-7",
        scheduleRevision = 12L,
        sessionId = "session-7",
        startedAtMillis = 900_000L,
        ringStartedAtMillis = 900_000L,
        previousBrightness = 80,
        previousBrightnessMode = 0,
        previousMediaVolume = 4,
        restorePolicy = RestorePolicy.RESTORE_PREVIOUS,
        presetApplied = true,
        snoozeCount = 1,
        snoozeDueAtMillis = 1_000_000L,
        snoozeDueAtElapsedRealtime = 400_000L,
        snoozeBootCount = 10,
        state = AlarmSessionState.SNOOZED,
    )

    private val ticket = SnoozeDeliveryTicket(
        alarmId = session.alarmId,
        occurrenceId = session.occurrenceId,
        scheduleRevision = session.scheduleRevision,
        sessionId = session.sessionId,
        snoozeCount = session.snoozeCount,
        dueAtMillis = session.snoozeDueAtMillis!!,
    )

    @Test
    fun currentCycleAtElapsedDeadlineIsAccepted() {
        assertTrue(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 1_000_000L,
                    nowElapsedRealtime = 400_000L,
                    currentBootCount = 10,
                ),
            ),
        )
    }

    @Test
    fun previousCycleIsRejectedEvenWhenDueAndIdentityMatch() {
        assertFalse(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket.copy(snoozeCount = 0),
                clock = dueClock(),
            ),
        )
    }

    @Test
    fun highCycleTicketIsAcceptedWithoutAnArtificialOneCycleLimit() {
        assertTrue(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session.copy(snoozeCount = 7),
                ticket = ticket.copy(snoozeCount = 7),
                clock = dueClock(),
            ),
        )
    }

    @Test
    fun changedDueIsRejectedWithinTheSameCycle() {
        assertFalse(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket.copy(dueAtMillis = 1_000_001L),
                clock = dueClock(),
            ),
        )
    }

    @Test
    fun sameBootUsesElapsedDeadlineInsteadOfWallClock() {
        assertFalse(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 9_000_000L,
                    nowElapsedRealtime = 399_999L,
                    currentBootCount = 10,
                ),
            ),
        )
        assertTrue(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 1L,
                    nowElapsedRealtime = 400_000L,
                    currentBootCount = 10,
                ),
            ),
        )
    }

    @Test
    fun rebootUsesWallDeadline() {
        assertFalse(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 999_999L,
                    nowElapsedRealtime = 999_999L,
                    currentBootCount = 11,
                ),
            ),
        )
        assertTrue(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session,
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 1_000_000L,
                    nowElapsedRealtime = 1L,
                    currentBootCount = 11,
                ),
            ),
        )
    }

    @Test
    fun unknownBootFallsBackToWallDeadline() {
        assertFalse(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session.copy(snoozeBootCount = null),
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 999_999L,
                    nowElapsedRealtime = 999_999L,
                    currentBootCount = -1,
                ),
            ),
        )
        assertTrue(
            SnoozeDeliveryValidation.acceptsPendingSession(
                session = session.copy(snoozeBootCount = null),
                ticket = ticket,
                clock = SnoozeDeliveryClock(
                    nowWallMillis = 1_000_000L,
                    nowElapsedRealtime = 1L,
                    currentBootCount = -1,
                ),
            ),
        )
    }

    @Test
    fun resumedSessionStillRequiresTheCurrentCycle() {
        val resumed = session.copy(
            state = AlarmSessionState.FIRING,
            snoozeDueAtMillis = null,
            snoozeDueAtElapsedRealtime = null,
            snoozeBootCount = null,
        )
        assertTrue(SnoozeDeliveryValidation.matchesResumedSession(resumed, ticket))
        assertFalse(
            SnoozeDeliveryValidation.matchesResumedSession(
                resumed,
                ticket.copy(snoozeCount = 0),
            ),
        )
    }

    private fun dueClock(): SnoozeDeliveryClock = SnoozeDeliveryClock(
        nowWallMillis = 1_000_000L,
        nowElapsedRealtime = 400_000L,
        currentBootCount = 10,
    )
}
