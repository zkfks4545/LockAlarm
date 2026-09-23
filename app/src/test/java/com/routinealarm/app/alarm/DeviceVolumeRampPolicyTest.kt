package com.routinealarm.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceVolumeRampPolicyTest {
    @Test
    fun raisesLowVolumeByAtMostFivePercentOfMaximumPerTick() {
        assertEquals(
            6,
            DeviceVolumeRampPolicy.nextVolume(
                currentVolume = 5,
                maxVolume = 20,
                targetPercent = 80,
            ),
        )
    }

    @Test
    fun neverRaisesAboveConfiguredTarget() {
        assertEquals(
            7,
            DeviceVolumeRampPolicy.nextVolume(
                currentVolume = 6,
                maxVolume = 20,
                targetPercent = 35,
            ),
        )
    }

    @Test
    fun leavesVolumeAtOrAboveTargetUntouched() {
        assertEquals(
            12,
            DeviceVolumeRampPolicy.nextVolume(
                currentVolume = 12,
                maxVolume = 20,
                targetPercent = 50,
            ),
        )
        assertEquals(
            18,
            DeviceVolumeRampPolicy.nextVolume(
                currentVolume = 18,
                maxVolume = 20,
                targetPercent = 50,
            ),
        )
    }

    @Test
    fun usesAtLeastOneDeviceStepForSmallMaximum() {
        assertEquals(
            1,
            DeviceVolumeRampPolicy.stepVolume(maxVolume = 15),
        )
        assertEquals(
            1,
            DeviceVolumeRampPolicy.nextVolume(
                currentVolume = 0,
                maxVolume = 15,
                targetPercent = 10,
            ),
        )
    }
}
