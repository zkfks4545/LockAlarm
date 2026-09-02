package com.routinealarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTimerPolicyTest {
    @Test
    fun remainingUsesElapsedClockAndStopsAtZero() {
        assertEquals(
            2_500L,
            CountdownTimerPolicy.remainingMillis(5_000L, 10_000L, 12_500L, running = true),
        )
        assertEquals(
            0L,
            CountdownTimerPolicy.remainingMillis(5_000L, 10_000L, 20_000L, running = true),
        )
    }

    @Test
    fun invalidDurationIsRejectedAndDisplayRoundsUp() {
        assertNull(CountdownTimerPolicy.parseDurationSeconds("0"))
        assertEquals(90L, CountdownTimerPolicy.parseDurationSeconds("90"))
        assertEquals("01:30", CountdownTimerPolicy.format(89_001L))
    }
}
