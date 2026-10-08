package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPreAlertPolicyTest {
    private val trigger = 1_800_000L

    @Test
    fun schedulesExactlyTenMinutesBeforeFutureAlarm() {
        assertEquals(
            1_200_000L,
            AlarmPreAlertPolicy.reminderAtMillis(trigger, nowMillis = 0L),
        )
    }

    @Test
    fun doesNotCreateLateOrImmediateReminder() {
        assertNull(AlarmPreAlertPolicy.reminderAtMillis(trigger, nowMillis = 1_200_000L))
        assertNull(AlarmPreAlertPolicy.reminderAtMillis(trigger, nowMillis = trigger))
    }

    @Test
    fun skipTicketOnlyMatchesCurrentUpcomingOccurrence() {
        val alarm = AlarmSpec(
            id = 7,
            triggerAtMillis = trigger,
            enabled = true,
            repeatType = RepeatType.DAILY,
            scheduleRevision = 4L,
        )
        assertTrue(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm, 4L, trigger, 1_200_000L))
        assertFalse(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm, 3L, trigger, 1_200_000L))
        assertFalse(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm, 4L, trigger + 1L, 1_200_000L))
        assertFalse(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm, 4L, trigger, 1_199_999L))
        assertFalse(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm, 4L, trigger, trigger))
        assertFalse(AlarmPreAlertPolicy.isCurrentUpcomingTicket(alarm.copy(enabled = false), 4L, trigger, 1_200_000L))
    }

    @Test
    fun skipOnlyAdvancesOneDailyOccurrence() {
        val zone = ZoneId.of("Asia/Seoul")
        val due = ZonedDateTime.of(2026, 8, 1, 7, 0, 0, 0, zone)
        val alarm = AlarmSpec(
            id = 7,
            triggerAtMillis = due.toInstant().toEpochMilli(),
            enabled = true,
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 7 * 60,
            scheduleRevision = 4L,
        )

        assertEquals(
            due.plusDays(1).toInstant().toEpochMilli(),
            AlarmPreAlertPolicy.nextAfterSkippedOccurrence(alarm, zone),
        )
        assertNull(
            AlarmPreAlertPolicy.nextAfterSkippedOccurrence(
                alarm.copy(repeatType = RepeatType.ONE_TIME), zone,
            ),
        )
    }

    @Test
    fun skipWeeklyOccurrenceHonorsExcludedNextDate() {
        val zone = ZoneId.of("Asia/Seoul")
        val due = ZonedDateTime.of(2026, 8, 3, 7, 0, 0, 0, zone) // Monday
        val alarm = AlarmSpec(
            id = 7,
            triggerAtMillis = due.toInstant().toEpochMilli(),
            enabled = true,
            repeatType = RepeatType.WEEKLY,
            localTimeMinutes = 7 * 60,
            weekdays = setOf(1, 3),
            excludeDatesEpochDay = setOf(LocalDate.of(2026, 8, 5).toEpochDay()),
            scheduleRevision = 4L,
        )

        assertEquals(
            due.plusWeeks(1).toInstant().toEpochMilli(),
            AlarmPreAlertPolicy.nextAfterSkippedOccurrence(alarm, zone),
        )
    }
}
