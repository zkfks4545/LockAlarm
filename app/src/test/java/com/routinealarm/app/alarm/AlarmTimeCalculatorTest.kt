package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeCalculatorTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun returnsSameDayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 8, 1, 7, 30, 20, 0, zone)

        val result = AlarmTimeCalculator.nextOccurrence(now, 8, 0)

        assertEquals(ZonedDateTime.of(2026, 8, 1, 8, 0, 0, 0, zone), result)
    }

    @Test
    fun returnsNextDayWhenTimeHasPassed() {
        val now = ZonedDateTime.of(2026, 8, 1, 8, 0, 1, 0, zone)

        val result = AlarmTimeCalculator.nextOccurrence(now, 8, 0)

        assertEquals(ZonedDateTime.of(2026, 8, 2, 8, 0, 0, 0, zone), result)
    }
}

