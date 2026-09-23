package com.routinealarm.app.alarm

/** The identity and timing values carried by a scheduled snooze delivery. */
data class SnoozeDeliveryTicket(
    val alarmId: Int,
    val occurrenceId: String,
    val scheduleRevision: Long,
    val sessionId: String,
    val snoozeCount: Int,
    val dueAtMillis: Long,
)

data class SnoozeDeliveryClock(
    val nowWallMillis: Long,
    val nowElapsedRealtime: Long,
    val currentBootCount: Int,
)

/**
 * Pure validation shared by the broadcast receiver and the playback service.
 * A wall-clock due time is retained for reboot recovery; elapsed time is used
 * whenever the stored and current boot counters prove that both clocks belong
 * to the same boot.
 */
object SnoozeDeliveryValidation {
    const val NO_SNOOZE_COUNT = -1
    const val NO_DUE_AT_MILLIS = -1L

    fun acceptsPendingSession(
        session: AlarmSession?,
        ticket: SnoozeDeliveryTicket,
        clock: SnoozeDeliveryClock,
    ): Boolean {
        val pendingSession = session ?: return false
        if (!matchesPendingReservation(pendingSession, ticket)) return false

        val storedBoot = pendingSession.snoozeBootCount
        val sameBoot = storedBoot != null &&
            storedBoot >= 0 &&
            clock.currentBootCount >= 0 &&
            storedBoot == clock.currentBootCount &&
            pendingSession.snoozeDueAtElapsedRealtime != null
        return if (sameBoot) {
            clock.nowElapsedRealtime >= pendingSession.snoozeDueAtElapsedRealtime!!
        } else {
            clock.nowWallMillis >= ticket.dueAtMillis
        }
    }

    /** True when this ticket belongs to the still-pending current reservation. */
    fun matchesPendingReservation(
        session: AlarmSession?,
        ticket: SnoozeDeliveryTicket,
    ): Boolean = session != null &&
        session.state == AlarmSessionState.SNOOZED &&
        matchesIdentityAndCycle(session, ticket) &&
        session.snoozeDueAtMillis == ticket.dueAtMillis

    /** Used for a duplicate service start after the session became FIRING. */
    fun matchesResumedSession(
        session: AlarmSession?,
        ticket: SnoozeDeliveryTicket,
    ): Boolean = session != null &&
        session.state == AlarmSessionState.FIRING &&
        matchesIdentityAndCycle(session, ticket)

    private fun matchesIdentityAndCycle(
        session: AlarmSession,
        ticket: SnoozeDeliveryTicket,
    ): Boolean = ticket.alarmId > 0 &&
        ticket.occurrenceId.isNotBlank() &&
        ticket.scheduleRevision >= 0L &&
        ticket.sessionId.isNotBlank() &&
        ticket.snoozeCount > 0 &&
        ticket.dueAtMillis > 0L &&
        session.alarmId == ticket.alarmId &&
        session.occurrenceId == ticket.occurrenceId &&
        session.scheduleRevision == ticket.scheduleRevision &&
        session.sessionId == ticket.sessionId &&
        session.snoozeCount == ticket.snoozeCount
}
