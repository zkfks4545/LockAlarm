package com.routinealarm.app

import com.routinealarm.app.model.AlarmSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stable home-list ordering: the configured clock time is the only primary key. */
object AlarmListPolicy {
    fun sortForDisplay(alarms: List<AlarmSpec>): List<AlarmSpec> = alarms.sortedWith(
        compareBy<AlarmSpec> { it.localTimeMinutes }
            .thenBy { it.id },
    )

    fun tomorrowResumeStatus(
        alarm: AlarmSpec,
        todayEpochDay: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TomorrowResumeStatus {
        val nextAlarmDay = Instant.ofEpochMilli(alarm.triggerAtMillis)
            .atZone(zoneId).toLocalDate()
        return if (nextAlarmDay.toEpochDay() == todayEpochDay + 1L) {
            TomorrowResumeStatus("내일 알람이 울립니다", null)
        } else {
            val formatted = nextAlarmDay.format(
                DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN),
            )
            TomorrowResumeStatus("내일 다시 켜집니다", "다음 알람: $formatted")
        }
    }

    /** Short enough to sit beside the repeat/date label in a compact card. */
    fun compactTomorrowResumeLabel(
        alarm: AlarmSpec,
        todayEpochDay: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val nextAlarmDay = Instant.ofEpochMilli(alarm.triggerAtMillis)
            .atZone(zoneId).toLocalDate()
        return if (nextAlarmDay.toEpochDay() == todayEpochDay + 1L) {
            "내일 알람이 울립니다"
        } else {
            "내일 켜짐 · ${nextAlarmDay.monthValue}/${nextAlarmDay.dayOfMonth} 울림"
        }
    }
}

data class TomorrowResumeStatus(val headline: String, val detail: String?)
