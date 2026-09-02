package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmScheduleRule
import com.routinealarm.app.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AlarmRecurrenceCalculatorTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun weeklyRuleSelectsEarliestConfiguredWeekday() {
        val now = ZonedDateTime.of(2026, 8, 2, 8, 0, 0, 0, zone) // Sunday
        val rule = weekly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2026, 8, 3, 7, 0, 0, 0, zone), result)
    }

    @Test
    fun exclusionWinsOverWeeklyAndIncludedDate() {
        val excludedMonday = LocalDate.of(2026, 8, 3)
        val now = ZonedDateTime.of(2026, 8, 2, 8, 0, 0, 0, zone)
        val rule = weekly(DayOfWeek.MONDAY).copy(
            includeDates = setOf(excludedMonday),
            excludeDates = setOf(excludedMonday),
        )

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2026, 8, 10, 7, 0, 0, 0, zone), result)
    }

    @Test
    fun includedDateCanRunOutsideConfiguredWeekdays() {
        val includedTuesday = LocalDate.of(2026, 8, 4)
        val now = ZonedDateTime.of(2026, 8, 3, 8, 0, 0, 0, zone)
        val rule = weekly(DayOfWeek.FRIDAY).copy(includeDates = setOf(includedTuesday))

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2026, 8, 4, 7, 0, 0, 0, zone), result)
    }

    @Test
    fun sameDateFromWeeklyAndIncludeProducesOneCandidate() {
        val monday = LocalDate.of(2026, 8, 3)
        val now = ZonedDateTime.of(2026, 8, 2, 8, 0, 0, 0, zone)
        val rule = weekly(DayOfWeek.MONDAY).copy(includeDates = setOf(monday))

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2026, 8, 3, 7, 0, 0, 0, zone), result)
    }

    @Test
    fun oneTimeRuleReturnsNullAfterItsTimePasses() {
        val date = LocalDate.of(2026, 8, 1)
        val now = ZonedDateTime.of(2026, 8, 1, 7, 0, 1, 0, zone)
        val rule = AlarmScheduleRule(LocalTime.of(7, 0), RepeatRule.OneTime(date))

        assertNull(AlarmRecurrenceCalculator.nextOccurrence(now, rule))
    }

    @Test
    fun weeklyRuleCrossesYearBoundary() {
        val now = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, zone)
        val rule = AlarmScheduleRule(
            localTime = LocalTime.MIDNIGHT,
            repeatRule = RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY)),
        )

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone), result)
    }

    @Test
    fun oneTimeRuleSupportsLeapDay() {
        val leapDay = LocalDate.of(2028, 2, 29)
        val now = ZonedDateTime.of(2028, 2, 28, 12, 0, 0, 0, zone)
        val rule = AlarmScheduleRule(LocalTime.of(7, 0), RepeatRule.OneTime(leapDay))

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2028, 2, 29, 7, 0, 0, 0, zone), result)
    }

    @Test
    fun dstGapMovesToFirstValidTimeAfterGap() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, newYork)
        val rule = AlarmScheduleRule(
            localTime = LocalTime.of(2, 30),
            repeatRule = RepeatRule.OneTime(LocalDate.of(2026, 3, 8)),
        )

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, newYork), result)
    }

    @Test
    fun dstOverlapUsesEarlierOffsetOnce() {
        val newYork = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 10, 31, 12, 0, 0, 0, newYork)
        val localTime = LocalDate.of(2026, 11, 1).atTime(1, 30)
        val rule = AlarmScheduleRule(
            localTime = localTime.toLocalTime(),
            repeatRule = RepeatRule.OneTime(localTime.toLocalDate()),
        )

        val result = AlarmRecurrenceCalculator.nextOccurrence(now, rule)

        assertEquals(
            ZonedDateTime.ofLocal(localTime, newYork, ZoneOffset.of("-04:00")),
            result,
        )
    }

    private fun weekly(vararg weekdays: DayOfWeek) = AlarmScheduleRule(
        localTime = LocalTime.of(7, 0),
        repeatRule = RepeatRule.Weekly(weekdays.toSet()),
    )
}
