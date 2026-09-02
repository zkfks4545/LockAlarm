package com.routinealarm.app.alarm

import java.time.ZonedDateTime

object AlarmTimeCalculator {
    fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        val candidate = now
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }
}

