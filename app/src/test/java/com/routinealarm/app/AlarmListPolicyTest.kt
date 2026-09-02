package com.routinealarm.app

import com.routinealarm.app.model.AlarmSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmListPolicyTest {
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
}
