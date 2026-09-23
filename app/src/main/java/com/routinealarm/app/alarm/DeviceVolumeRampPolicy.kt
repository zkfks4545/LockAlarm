package com.routinealarm.app.alarm

import kotlin.math.roundToInt

/**
 * Calculates the next device media-stream volume while an alarm is firing.
 *
 * The guard is one-way: it only raises a stream that the user moved below the
 * alarm's configured target. A user-selected value above that target is never
 * reduced by the alarm.
 */
object DeviceVolumeRampPolicy {
    const val DEFAULT_INTERVAL_MILLIS = 2_000L
    const val DEFAULT_STEP_PERCENT = 5

    fun targetVolume(maxVolume: Int, targetPercent: Int): Int {
        val safeMax = maxVolume.coerceAtLeast(0)
        return (safeMax * targetPercent.coerceIn(0, 100) / 100f).roundToInt()
    }

    fun stepVolume(
        maxVolume: Int,
        stepPercent: Int = DEFAULT_STEP_PERCENT,
    ): Int {
        val safeMax = maxVolume.coerceAtLeast(0)
        if (safeMax == 0) return 0
        return (safeMax * stepPercent.coerceAtLeast(0) / 100f)
            .roundToInt()
            .coerceAtLeast(1)
    }

    fun nextVolume(
        currentVolume: Int,
        maxVolume: Int,
        targetPercent: Int,
        stepPercent: Int = DEFAULT_STEP_PERCENT,
    ): Int {
        val safeMax = maxVolume.coerceAtLeast(0)
        val current = currentVolume.coerceIn(0, safeMax)
        val target = targetVolume(safeMax, targetPercent)
        if (current >= target) return current
        return (current + stepVolume(safeMax, stepPercent)).coerceAtMost(target)
    }
}
