package com.routinealarm.app.alarm

import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.ui.VideoDisplaySize

enum class InitialAlarmOrientation {
    LANDSCAPE,
    PORTRAIT,
    SENSOR,
}

object AlarmOrientationPolicy {
    fun initial(
        contentMode: ContentMode,
        youtubeIsShorts: Boolean,
        localDisplaySize: VideoDisplaySize?,
    ): InitialAlarmOrientation = when {
        contentMode == ContentMode.YOUTUBE && youtubeIsShorts -> InitialAlarmOrientation.PORTRAIT
        contentMode == ContentMode.YOUTUBE -> InitialAlarmOrientation.LANDSCAPE
        localDisplaySize?.isLandscape == true -> InitialAlarmOrientation.LANDSCAPE
        localDisplaySize != null -> InitialAlarmOrientation.PORTRAIT
        else -> InitialAlarmOrientation.SENSOR
    }
}
