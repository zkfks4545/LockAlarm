package com.routinealarm.app.alarm

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlaybackPositionStoreTest {
    @After
    fun tearDown() {
        AlarmPlaybackPositionStore.clearAll()
    }

    @Test
    fun replacementSurfaceContinuesFromStoredPosition() {
        val first = AlarmPlaybackPositionStore.attach("session") { }
        first.updatePosition(12_345L)

        val replacement = AlarmPlaybackPositionStore.attach("session") { }

        assertEquals(12_345L, replacement.positionMillis())
    }

    @Test
    fun attachingReplacementSupersedesPreviousSurface() {
        var superseded = false
        AlarmPlaybackPositionStore.attach("session") { superseded = true }

        AlarmPlaybackPositionStore.attach("session") { }

        assertTrue(superseded)
    }

    @Test
    fun supersededSurfaceCanCaptureItsFinalPositionBeforeReplacementTakesOwnership() {
        lateinit var first: AlarmPlaybackLease
        first = AlarmPlaybackPositionStore.attach("session") {
            first.updatePosition(4_321L)
        }

        val replacement = AlarmPlaybackPositionStore.attach("session") { }

        assertEquals(4_321L, replacement.positionMillis())
    }

    @Test
    fun staleSurfaceCannotOverwriteCurrentPosition() {
        val first = AlarmPlaybackPositionStore.attach("session") { }
        first.updatePosition(1_000L)
        val replacement = AlarmPlaybackPositionStore.attach("session") { }
        replacement.updatePosition(2_000L)

        first.updatePosition(9_000L)

        assertEquals(2_000L, replacement.positionMillis())
        assertFalse(replacement.positionMillis() == 9_000L)
    }
}
