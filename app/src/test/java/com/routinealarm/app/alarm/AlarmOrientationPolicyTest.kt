package com.routinealarm.app.alarm

import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.ui.VideoDisplaySize
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmOrientationPolicyTest {
    @Test
    fun normalYouTubeStartsLandscape() {
        assertEquals(
            InitialAlarmOrientation.LANDSCAPE,
            AlarmOrientationPolicy.initial(ContentMode.YOUTUBE, false, null),
        )
    }

    @Test
    fun youtubeShortsStartsPortrait() {
        assertEquals(
            InitialAlarmOrientation.PORTRAIT,
            AlarmOrientationPolicy.initial(ContentMode.YOUTUBE, true, null),
        )
    }

    @Test
    fun localContentUsesItsDisplayedRatio() {
        assertEquals(
            InitialAlarmOrientation.PORTRAIT,
            AlarmOrientationPolicy.initial(
                ContentMode.LOCAL,
                false,
                VideoDisplaySize(width = 1_080, height = 1_920),
            ),
        )
    }

    @Test
    fun missingLocalVisualAllowsSensorOrientation() {
        assertEquals(
            InitialAlarmOrientation.SENSOR,
            AlarmOrientationPolicy.initial(ContentMode.LOCAL, false, null),
        )
    }
}
