package com.routinealarm.app

import com.routinealarm.app.model.AlarmSpec

/** Stable home-list ordering: the configured clock time is the only primary key. */
object AlarmListPolicy {
    fun sortForDisplay(alarms: List<AlarmSpec>): List<AlarmSpec> = alarms.sortedWith(
        compareBy<AlarmSpec> { it.localTimeMinutes }
            .thenBy { it.id },
    )
}
