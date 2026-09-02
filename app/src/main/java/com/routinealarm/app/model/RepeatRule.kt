package com.routinealarm.app.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

sealed interface RepeatRule {
    data class OneTime(val date: LocalDate) : RepeatRule

    data class Weekly(val weekdays: Set<DayOfWeek>) : RepeatRule {
        init {
            require(weekdays.isNotEmpty()) { "반복 요일을 하나 이상 선택해야 합니다." }
        }
    }
}

data class AlarmScheduleRule(
    val localTime: LocalTime,
    val repeatRule: RepeatRule,
    val includeDates: Set<LocalDate> = emptySet(),
    val excludeDates: Set<LocalDate> = emptySet(),
) {
    init {
        require(repeatRule is RepeatRule.Weekly || includeDates.isEmpty()) {
            "추가 날짜는 요일 반복 알람에서만 사용할 수 있습니다."
        }
    }
}

