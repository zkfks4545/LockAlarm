package com.routinealarm.app.alarm

import com.routinealarm.app.model.ContentMode

object DismissTimerPolicy {
    const val UNKNOWN_MEDIA_MAX_DELAY_SECONDS = 60
    const val YOUTUBE_FALLBACK_MAX_DELAY_SECONDS = 300

    fun maxDelaySeconds(mediaDurationSeconds: Int?): Int =
        mediaDurationSeconds?.coerceAtLeast(0) ?: UNKNOWN_MEDIA_MAX_DELAY_SECONDS

    fun maxDelaySecondsForContent(
        contentMode: ContentMode,
        mediaDurationSeconds: Int?,
    ): Int {
        if (mediaDurationSeconds != null) return mediaDurationSeconds.coerceAtLeast(0)
        return if (contentMode == ContentMode.YOUTUBE) {
            YOUTUBE_FALLBACK_MAX_DELAY_SECONDS
        } else {
            UNKNOWN_MEDIA_MAX_DELAY_SECONDS
        }
    }

    fun clampDelaySeconds(delaySeconds: Int, maxDelaySeconds: Int): Int =
        delaySeconds.coerceAtLeast(0).coerceAtMost(maxDelaySeconds.coerceAtLeast(0))
}
