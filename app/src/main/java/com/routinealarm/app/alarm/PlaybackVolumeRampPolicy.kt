package com.routinealarm.app.alarm

/**
 * Pure timing policy for the per-player alarm-volume fade-in.
 *
 * The device media-stream preset is still applied once by [DeviceStateController]. This
 * policy only controls a MediaPlayer's local gain and never changes the device stream volume.
 */
object PlaybackVolumeRampPolicy {
    const val DEFAULT_START_GAIN = 0.10f
    const val DEFAULT_DURATION_MILLIS = 30_000L
    const val TICK_INTERVAL_MILLIS = 250L
    const val MIN_START_GAIN = 0.01f

    fun gainAt(
        elapsedMillis: Long,
        startGain: Float = DEFAULT_START_GAIN,
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
    ): Float {
        val safeStartGain = sanitizeStartGain(startGain)
        val safeDurationMillis = durationMillis.coerceAtLeast(1L)
        if (elapsedMillis <= 0L) return safeStartGain
        if (elapsedMillis >= safeDurationMillis) return 1f

        val progress = elapsedMillis.toDouble() / safeDurationMillis.toDouble()
        return (safeStartGain + ((1f - safeStartGain) * progress.toFloat()))
            .coerceIn(safeStartGain, 1f)
    }

    fun nextTickDelayMillis(
        elapsedMillis: Long,
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
    ): Long {
        val safeDurationMillis = durationMillis.coerceAtLeast(1L)
        val remainingMillis = (safeDurationMillis - elapsedMillis).coerceAtLeast(0L)
        return minOf(TICK_INTERVAL_MILLIS, remainingMillis)
    }

    private fun sanitizeStartGain(value: Float): Float = when {
        !value.isFinite() -> DEFAULT_START_GAIN
        else -> value.coerceIn(MIN_START_GAIN, 1f)
    }
}
