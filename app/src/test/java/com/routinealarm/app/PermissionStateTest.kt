package com.routinealarm.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {
    @Test
    fun allRequiredAccessesMustBeGranted() {
        assertTrue(
            PermissionState(
                exactAlarm = true,
                writeSettings = true,
                fullScreenIntent = true,
                notifications = true,
                drawOverlays = true,
            ).allGranted,
        )
    }

    @Test
    fun missingAccessesAreNamedForTheGate() {
        val state = PermissionState(
            exactAlarm = false,
            writeSettings = true,
            fullScreenIntent = true,
            notifications = false,
            drawOverlays = true,
        )

        assertFalse(state.allGranted)
        assertEquals(listOf("정확한 알람", "알림 표시"), state.missingAccessNames)
    }
}
