package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmScheduleResolverTest {
    @Test
    fun pastTimeTodayRollsToTomorrow() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 18 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        )

        val adjusted = AlarmScheduleResolver.rollPastOneTimeToTomorrow(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 2).toEpochDay(), adjusted.oneTimeDateEpochDay)
    }

    @Test
    fun futureTimeTodayStaysToday() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 19 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        )

        val adjusted = AlarmScheduleResolver.rollPastOneTimeToTomorrow(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), adjusted.oneTimeDateEpochDay)
    }

    @Test
    fun currentMinuteCountsAsPastAndRollsToTomorrow() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 45, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 18 * 60 + 30,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
        )

        val adjusted = AlarmScheduleResolver.rollPastOneTimeToTomorrow(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 2).toEpochDay(), adjusted.oneTimeDateEpochDay)
    }

    @Test
    fun futureSelectedDateIsNotOverwritten() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 7 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 5).toEpochDay(),
        )

        val adjusted = AlarmScheduleResolver.rollPastOneTimeToTomorrow(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 5).toEpochDay(), adjusted.oneTimeDateEpochDay)
    }

    @Test
    fun weeklyAlarmIsNeverRolledByOneTimeNormalization() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.WEEKLY,
            localTimeMinutes = 7 * 60,
            weekdays = setOf(1),
        )

        val adjusted = AlarmScheduleResolver.rollPastOneTimeToTomorrow(alarm, now)

        assertEquals(alarm, adjusted)
    }

    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun oneTimeAlarmUsesSelectedDateAndTime() {
        val now = ZonedDateTime.of(2026, 8, 1, 12, 0, 0, 0, zone)
        val date = LocalDate.of(2026, 8, 2)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 7 * 60 + 30,
            oneTimeDateEpochDay = date.toEpochDay(),
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 7, 30, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun weeklyAlarmHonorsAddedAndExcludedDates() {
        val now = ZonedDateTime.of(2026, 8, 1, 12, 0, 0, 0, zone)
        val addedSunday = LocalDate.of(2026, 8, 2)
        val excludedMonday = LocalDate.of(2026, 8, 3)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.WEEKLY,
            localTimeMinutes = 7 * 60,
            weekdays = setOf(1),
            includeDatesEpochDay = setOf(addedSunday.toEpochDay()),
            excludeDatesEpochDay = setOf(excludedMonday.toEpochDay()),
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun weeklyAlarmWithoutWeekdaysIsInvalid() {
        val now = ZonedDateTime.of(2026, 8, 1, 12, 0, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.WEEKLY,
            localTimeMinutes = 7 * 60,
        )

        assertNull(
            AlarmScheduleResolver.nextTriggerAtMillis(
                alarm = alarm,
                nowMillis = now.toInstant().toEpochMilli(),
                zoneId = zone,
            ),
        )
    }

    @Test
    fun activationMovesLegacyAutoRolledDateBackToTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.plusDays(1).toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 19 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 2).toEpochDay(),
            oneTimeDateUserSelected = false,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun activationMovesPastTimeToTomorrowForUnselectedDate() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 18 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
            oneTimeDateUserSelected = false,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 2).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun activationKeepsExplicitFutureDate() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.plusDays(1).toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 7 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 5).toEpochDay(),
            oneTimeDateUserSelected = true,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 5).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun activationRebasesExplicitPastDateToTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 19 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 7, 31).toEpochDay(),
            oneTimeDateUserSelected = true,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun activationMovesExplicitPastDateToTomorrowWhenTodayTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 8, 1, 19, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 19 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 7, 31).toEpochDay(),
            oneTimeDateUserSelected = true,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 2).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun activationKeepsExplicitTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = 19 * 60,
            oneTimeDateEpochDay = LocalDate.of(2026, 8, 1).toEpochDay(),
            oneTimeDateUserSelected = true,
        )

        val prepared = AlarmScheduleResolver.prepareForActivation(alarm, now)

        assertEquals(LocalDate.of(2026, 8, 1).toEpochDay(), prepared.oneTimeDateEpochDay)
    }

    @Test
    fun dailyAlarmUsesTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 19 * 60,
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 1, 19, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun dailyAlarmMovesPastTimeToTomorrow() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 18 * 60,
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 18, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun dailyAlarmSkipsExcludedToday() {
        val now = ZonedDateTime.of(2026, 8, 1, 18, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 19 * 60,
            excludeDatesEpochDay = setOf(LocalDate.of(2026, 8, 1).toEpochDay()),
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 19, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun dailyDismissalAlwaysStartsAgainTomorrowEvenIfDismissedBeforeConfiguredTime() {
        val dismissedAt = ZonedDateTime.of(2026, 8, 1, 6, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = dismissedAt.toInstant().toEpochMilli(),
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 7 * 60,
        )

        val result = AlarmScheduleResolver.nextTriggerAfterDismissal(
            alarm = alarm,
            dismissedAtMillis = dismissedAt.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun dailyDismissalSkipsTodayWhenConfiguredTimeHasPassed() {
        val dismissedAt = ZonedDateTime.of(2026, 8, 1, 7, 5, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = dismissedAt.toInstant().toEpochMilli(),
            repeatType = RepeatType.DAILY,
            localTimeMinutes = 7 * 60,
        )

        val result = AlarmScheduleResolver.nextTriggerAfterDismissal(
            alarm = alarm,
            dismissedAtMillis = dismissedAt.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 2, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }

    @Test
    fun weeklyAlarmUsesSelectedTodayBeforeMovingToNextDay() {
        val now = ZonedDateTime.of(2026, 8, 3, 6, 30, 0, 0, zone)
        val alarm = AlarmSpec(
            triggerAtMillis = now.toInstant().toEpochMilli(),
            repeatType = RepeatType.WEEKLY,
            localTimeMinutes = 7 * 60,
            weekdays = setOf(1),
        )

        val result = AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = now.toInstant().toEpochMilli(),
            zoneId = zone,
        )

        assertEquals(
            ZonedDateTime.of(2026, 8, 3, 7, 0, 0, 0, zone).toInstant().toEpochMilli(),
            result,
        )
    }
}
