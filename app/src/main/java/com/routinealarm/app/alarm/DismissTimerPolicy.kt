package com.routinealarm.app.alarm

object DismissTimerPolicy {
    const val UNKNOWN_MEDIA_MAX_DELAY_SECONDS = 60

    fun maxDelaySeconds(mediaDurationSeconds: Int?): Int =
        mediaDurationSeconds?.coerceAtLeast(0) ?: UNKNOWN_MEDIA_MAX_DELAY_SECONDS

    fun clampDelaySeconds(delaySeconds: Int, maxDelaySeconds: Int): Int =
        delaySeconds.coerceAtLeast(0).coerceAtMost(maxDelaySeconds.coerceAtLeast(0))
}
