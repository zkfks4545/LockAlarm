package com.routinealarm.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmMediaDurationTest {
    @Test
    fun durationRoundsUpToAllowTheFullMedia() {
        assertEquals(1, AlarmMediaDuration.durationSecondsFromMillis(1L))
        assertEquals(120, AlarmMediaDuration.durationSecondsFromMillis(120_000L))
        assertEquals(121, AlarmMediaDuration.durationSecondsFromMillis(120_001L))
    }

    @Test
    fun missingOrZeroDurationFallsBackToUnknown() {
        assertNull(AlarmMediaDuration.durationSecondsFromMillis(null))
        assertNull(AlarmMediaDuration.durationSecondsFromMillis(0L))
        assertNull(AlarmMediaDuration.durationSecondsFromMillis(-1L))
    }
}
