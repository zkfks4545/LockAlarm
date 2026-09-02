package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmScheduleRule
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatRule
import com.routinealarm.app.model.RepeatType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduleResolver {
    fun rollPastOneTimeToTomorrow(
        alarm: AlarmSpec,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): AlarmSpec {
        if (alarm.repeatType != RepeatType.ONE_TIME) return alarm
        val selectedDate = alarm.oneTimeDateEpochDay?.let(LocalDate::ofEpochDay)
            ?: Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(now.zone).toLocalDate()
        val selectedTime = LocalTime.ofSecondOfDay(
            alarm.localTimeMinutes.coerceIn(0, 1439) * 60L,
        )
        val selectedDateTime = selectedDate.atTime(selectedTime).atZone(now.zone)
        return if (!selectedDateTime.isAfter(now)) {
            alarm.copy(oneTimeDateEpochDay = now.toLocalDate().plusDays(1).toEpochDay())
        } else {
            alarm
        }
    }

    fun nextTriggerAtMillis(
        alarm: AlarmSpec,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val localTime = LocalTime.ofSecondOfDay(alarm.localTimeMinutes.coerceIn(0, 1439) * 60L)
        val repeatRule = when (alarm.repeatType) {
            RepeatType.ONE_TIME -> RepeatRule.OneTime(
                alarm.oneTimeDateEpochDay?.let(LocalDate::ofEpochDay)
                    ?: Instant.ofEpochMilli(alarm.triggerAtMillis).atZone(zoneId).toLocalDate(),
            )
            RepeatType.WEEKLY -> {
                val days = alarm.weekdays.mapNotNull { value ->
                    runCatching { DayOfWeek.of(value) }.getOrNull()
                }.toSet()
                if (days.isEmpty()) return null
                RepeatRule.Weekly(days)
            }
        }
        return AlarmRecurrenceCalculator.nextOccurrence(
            now = now,
            schedule = AlarmScheduleRule(
                localTime = localTime,
                repeatRule = repeatRule,
                includeDates = if (alarm.repeatType == RepeatType.WEEKLY) {
                    alarm.includeDatesEpochDay.map(LocalDate::ofEpochDay).toSet()
                } else {
                    emptySet()
                },
                excludeDates = if (alarm.repeatType == RepeatType.WEEKLY) {
                    alarm.excludeDatesEpochDay.map(LocalDate::ofEpochDay).toSet()
                } else {
                    emptySet()
                },
            ),
            zoneId = zoneId,
        )?.toInstant()?.toEpochMilli()
    }

    fun newAlarm(now: ZonedDateTime = ZonedDateTime.now()): AlarmSpec {
        val next = now.plusMinutes(1).withSecond(0).withNano(0)
        return AlarmSpec(
            label = "새 알람",
            triggerAtMillis = next.toInstant().toEpochMilli(),
            repeatType = RepeatType.ONE_TIME,
            localTimeMinutes = next.hour * 60 + next.minute,
            oneTimeDateEpochDay = next.toLocalDate().toEpochDay(),
        )
    }
}
