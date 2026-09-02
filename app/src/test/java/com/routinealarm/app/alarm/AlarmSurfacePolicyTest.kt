package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSurfacePolicyTest {
    @Test
    fun screenOffWaitsForScreenOnSignal() {
        assertEquals(
            AlarmSurfaceTarget.WAIT_FOR_SCREEN,
            AlarmSurfacePolicy.target(
                isInteractive = false,
                isKeyguardLocked = true,
                canDrawOverlays = true,
            ),
        )
    }

    @Test
    fun lockedInteractiveScreenUsesFullScreenActivity() {
        assertEquals(
            AlarmSurfaceTarget.FULL_SCREEN_ACTIVITY,
            AlarmSurfacePolicy.target(
                isInteractive = true,
                isKeyguardLocked = true,
                canDrawOverlays = true,
            ),
        )
    }

    @Test
    fun unlockedScreenUsesOverlayWhenPermitted() {
        assertEquals(
            AlarmSurfaceTarget.APPLICATION_OVERLAY,
            AlarmSurfacePolicy.target(
                isInteractive = true,
                isKeyguardLocked = false,
                canDrawOverlays = true,
            ),
        )
    }

    @Test
    fun missingOverlayPermissionFallsBackToActivity() {
        assertEquals(
            AlarmSurfaceTarget.FULL_SCREEN_ACTIVITY,
            AlarmSurfacePolicy.target(
                isInteractive = true,
                isKeyguardLocked = false,
                canDrawOverlays = false,
            ),
        )
    }
}
