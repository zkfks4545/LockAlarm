package com.routinealarm.app.alarm

enum class AlarmSurfaceTarget {
    WAIT_FOR_SCREEN,
    FULL_SCREEN_ACTIVITY,
    APPLICATION_OVERLAY,
}

object AlarmSurfacePolicy {
    fun target(
        isInteractive: Boolean,
        isKeyguardLocked: Boolean,
        canDrawOverlays: Boolean,
    ): AlarmSurfaceTarget = when {
        !isInteractive -> AlarmSurfaceTarget.WAIT_FOR_SCREEN
        isKeyguardLocked -> AlarmSurfaceTarget.FULL_SCREEN_ACTIVITY
        canDrawOverlays -> AlarmSurfaceTarget.APPLICATION_OVERLAY
        else -> AlarmSurfaceTarget.FULL_SCREEN_ACTIVITY
    }
}
