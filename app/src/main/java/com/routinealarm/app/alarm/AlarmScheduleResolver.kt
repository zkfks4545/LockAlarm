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
    /**
     * Resolves the date used when a saved one-time alarm is activated.
     *
     * Activation is deliberately today-first. A stale date from a previous
     * automatic calculation is re-anchored to today when today's time is
     * still ahead. An explicitly selected future date remains authoritative;
     * an explicitly selected date in the past follows the same today-first
     * rule instead of blindly becoming tomorrow.
     */
    fun prepareForActivation(
        alarm: AlarmSpec,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): AlarmSpec {
        val localTime = LocalTime.ofSecondOfDay(
            alarm.localTimeMinutes.coerceIn(0, 1439) * 60L,
        )
        val today = now.toLocalDate()
        val todayAtTime = today.atTime(localTime).atZone(now.zone)
        if (alarm.repeatType != RepeatType.ONE_TIME) return alarm

        val selectedDate = alarm.oneTimeDateEpochDay?.let(LocalDate::ofEpochDay)
        val activationDate = when {
            // A future date selected by the user is still the requested date.
            alarm.oneTimeDateUserSelected &&
                selectedDate != null &&
                selectedDate.isAfter(today) -> selectedDate
            // A missing or stale date means that activation should use today's
            // time when it is still available, otherwise the next day.
            todayAtTime.isAfter(now) -> today
            else -> today.plusDays(1)
        }
        return alarm.copy(oneTimeDateEpochDay = activationDate.toEpochDay())
    }

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
            RepeatType.DAILY -> RepeatRule.Weekly(DayOfWeek.values().toSet())
        }
        return AlarmRecurrenceCalculator.nextOccurrence(
            now = now,
            schedule = AlarmScheduleRule(
                localTime = localTime,
                repeatRule = repeatRule,
                includeDates = if (alarm.repeatType != RepeatType.ONE_TIME) {
                    alarm.includeDatesEpochDay.map(LocalDate::ofEpochDay).toSet()
                } else {
                    emptySet()
                },
                excludeDates = if (alarm.repeatType != RepeatType.ONE_TIME) {
                    alarm.excludeDatesEpochDay.map(LocalDate::ofEpochDay).toSet()
                } else {
                    emptySet()
                },
            ),
            zoneId = zoneId,
        )?.toInstant()?.toEpochMilli()
    }

    /**
     * Resolves the next repeat occurrence after the occurrence being dismissed.
     *
     * The dismissal action means that today's configured occurrence has been
     * handled, even when the action arrives a little before the configured
     * wall-clock time because of recovery or a test delivery. The reference is
     * therefore the later of now and today's configured time, so a daily alarm
     * is never re-armed for the same day after dismissal.
     */
    fun nextTriggerAfterDismissal(
        alarm: AlarmSpec,
        dismissedAtMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (alarm.repeatType == RepeatType.ONE_TIME) return null

        val dismissedAt = Instant.ofEpochMilli(dismissedAtMillis).atZone(zoneId)
        val localTime = LocalTime.ofSecondOfDay(
            alarm.localTimeMinutes.coerceIn(0, 1439) * 60L,
        )
        val todayOccurrence = dismissedAt.toLocalDate()
            .atTime(localTime)
            .atZone(zoneId)
        val reference = maxOf(dismissedAt, todayOccurrence).plusSeconds(1)
        return nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = reference.toInstant().toEpochMilli(),
            zoneId = zoneId,
        )
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
