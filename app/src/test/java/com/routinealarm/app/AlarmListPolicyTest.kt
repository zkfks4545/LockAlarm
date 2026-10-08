package com.routinealarm.app

import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatType
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmListPolicyTest {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun sortsByConfiguredTimeWithoutPuttingEnabledAlarmsFirst() {
        val laterEnabled = AlarmSpec(id = 1, triggerAtMillis = 1L, localTimeMinutes = 9 * 60, enabled = true)
        val earlierDisabled = AlarmSpec(id = 2, triggerAtMillis = 2L, localTimeMinutes = 7 * 60, enabled = false)
        val sameTimeLowerId = AlarmSpec(id = 3, triggerAtMillis = 3L, localTimeMinutes = 7 * 60, enabled = true)

        assertEquals(
            listOf(earlierDisabled, sameTimeLowerId, laterEnabled),
            AlarmListPolicy.sortForDisplay(listOf(laterEnabled, earlierDisabled, sameTimeLowerId)),
        )
    }

    @Test
    fun tomorrowResumeSaysItWillRingTomorrowOnlyWhenTheNextTriggerIsTomorrow() {
        val today = LocalDate.of(2026, 10, 8)
        val tomorrowTrigger = today.plusDays(1).atTime(7, 35)
            .atZone(zone).toInstant().toEpochMilli()
        val alarm = AlarmSpec(
            triggerAtMillis = tomorrowTrigger,
            enabled = true,
            repeatType = RepeatType.DAILY,
            resumeOnEpochDay = today.plusDays(1).toEpochDay(),
        )

        assertEquals(
            TomorrowResumeStatus("내일 알람이 울립니다", null),
            AlarmListPolicy.tomorrowResumeStatus(alarm, today.toEpochDay(), zone),
        )
    }

    @Test
    fun tomorrowResumeShowsTheActualNextWeeklyAlarmDate() {
        val today = LocalDate.of(2026, 10, 8)
        val nextMondayTrigger = today.plusDays(4).atTime(7, 35)
            .atZone(zone).toInstant().toEpochMilli()
        val alarm = AlarmSpec(
            triggerAtMillis = nextMondayTrigger,
            enabled = true,
            repeatType = RepeatType.WEEKLY,
            weekdays = setOf(1),
            resumeOnEpochDay = today.plusDays(1).toEpochDay(),
        )

        assertEquals(
            TomorrowResumeStatus("내일 다시 켜집니다", "다음 알람: 10월 12일 (월)"),
            AlarmListPolicy.tomorrowResumeStatus(alarm, today.toEpochDay(), zone),
        )
    }
}
