package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmScheduleRule
import com.routinealarm.app.model.RepeatRule
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmRecurrenceCalculator {
    fun nextOccurrence(
        now: ZonedDateTime,
        schedule: AlarmScheduleRule,
        zoneId: ZoneId = now.zone,
    ): ZonedDateTime? {
        val zonedNow = now.withZoneSameInstant(zoneId)
        return when (val repeat = schedule.repeatRule) {
            is RepeatRule.OneTime -> listOf(repeat.date)
                .asSequence()
                .filterNot(schedule.excludeDates::contains)
                .map { resolveLocalDateTime(it.atTime(schedule.localTime), zoneId) }
                .firstOrNull { it.isAfter(zonedNow) }

            is RepeatRule.Weekly -> nextWeeklyOccurrence(zonedNow, schedule, repeat, zoneId)
        }
    }

    private fun nextWeeklyOccurrence(
        now: ZonedDateTime,
        schedule: AlarmScheduleRule,
        repeat: RepeatRule.Weekly,
        zoneId: ZoneId,
    ): ZonedDateTime? {
        val searchDays = schedule.excludeDates.size * DAYS_PER_WEEK + BASE_SEARCH_DAYS
        return generateSequence(now.toLocalDate()) { it.plusDays(1) }
            .take(searchDays)
            .filter { date -> date !in schedule.excludeDates }
            .filter { date -> date in schedule.includeDates || date.dayOfWeek in repeat.weekdays }
            .map { date -> resolveLocalDateTime(date.atTime(schedule.localTime), zoneId) }
            .firstOrNull { candidate -> candidate.isAfter(now) }
    }

    private fun resolveLocalDateTime(localDateTime: LocalDateTime, zoneId: ZoneId): ZonedDateTime {
        val rules = zoneId.rules
        val validOffsets = rules.getValidOffsets(localDateTime)
        if (validOffsets.isNotEmpty()) {
            return ZonedDateTime.ofLocal(localDateTime, zoneId, validOffsets.first())
        }
        val transition = requireNotNull(rules.getTransition(localDateTime))
        return transition.dateTimeAfter.atZone(zoneId)
    }

    private const val DAYS_PER_WEEK = 7
    private const val BASE_SEARCH_DAYS = 14
}
