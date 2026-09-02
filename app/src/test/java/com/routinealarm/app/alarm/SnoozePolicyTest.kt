package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozePolicyTest {
    @Test
    fun schedulesExactlyFiveMinutesAfterUserAction() {
        val actionAt = 1_722_476_800_000L

        val result = SnoozePolicy.dueAt(actionAt)

        assertEquals(actionAt + 300_000L, result)
    }

    @Test
    fun wallClockChangeKeepsElapsedDeadlineOnSameBoot() {
        val result = SnoozePolicy.reanchoredWallDueAt(
            nowWallMillis = 10_000_000L,
            nowElapsedRealtime = 200_000L,
            dueAtElapsedRealtime = 320_000L,
        )

        assertEquals(10_120_000L, result)
    }
}
