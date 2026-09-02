package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVolumeRampPolicyTest {
    @Test
    fun defaultRampStartsAudibleAndReachesFullGain() {
        val start = PlaybackVolumeRampPolicy.gainAt(0L)
        val middle = PlaybackVolumeRampPolicy.gainAt(
            PlaybackVolumeRampPolicy.DEFAULT_DURATION_MILLIS / 2L,
        )
        val end = PlaybackVolumeRampPolicy.gainAt(
            PlaybackVolumeRampPolicy.DEFAULT_DURATION_MILLIS,
        )

        assertTrue(start > 0f)
        assertTrue(middle > start)
        assertEquals(1f, end, 0f)
    }

    @Test
    fun gainIsClampedAndMonotonicForInvalidInputs() {
        val gains = listOf(
            PlaybackVolumeRampPolicy.gainAt(-1_000L, startGain = -1f),
            PlaybackVolumeRampPolicy.gainAt(0L, startGain = 0f),
            PlaybackVolumeRampPolicy.gainAt(15_000L, startGain = 0.3f),
            PlaybackVolumeRampPolicy.gainAt(30_000L, startGain = 0.3f),
            PlaybackVolumeRampPolicy.gainAt(60_000L, startGain = 2f),
        )

        assertTrue(gains.all { it in 0f..1f })
        assertTrue(gains[0] > 0f)
        assertTrue(gains[1] <= gains[2])
        assertTrue(gains[2] <= gains[3])
        assertEquals(1f, gains[4], 0f)
    }

    @Test
    fun defaultTickIsApproximatelyQuarterSecondAndStopsAtDeadline() {
        assertEquals(250L, PlaybackVolumeRampPolicy.TICK_INTERVAL_MILLIS)
        assertEquals(
            250L,
            PlaybackVolumeRampPolicy.nextTickDelayMillis(1_000L),
        )
        assertEquals(
            100L,
            PlaybackVolumeRampPolicy.nextTickDelayMillis(29_900L),
        )
        assertEquals(
            0L,
            PlaybackVolumeRampPolicy.nextTickDelayMillis(30_000L),
        )
    }
}
