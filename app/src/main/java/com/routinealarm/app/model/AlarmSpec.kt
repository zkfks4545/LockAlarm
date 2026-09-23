package com.routinealarm.app.model

data class AlarmSpec(
    val id: Int = 0,
    val label: String = "알람",
    val triggerAtMillis: Long,
    val enabled: Boolean = false,
    val repeatType: RepeatType = RepeatType.ONE_TIME,
    val localTimeMinutes: Int = 7 * 60,
    val oneTimeDateEpochDay: Long? = null,
    /** Whether the one-time date was explicitly chosen by the user. */
    val oneTimeDateUserSelected: Boolean = false,
    val weekdays: Set<Int> = emptySet(),
    val includeDatesEpochDay: Set<Long> = emptySet(),
    val excludeDatesEpochDay: Set<Long> = emptySet(),
    val brightnessPercent: Int = 80,
    val mediaVolumePercent: Int = 70,
    /** Delay in seconds; the editor and runtime clamp it to the selected media duration. */
    val dismissDelaySeconds: Int = 5,
    /** Whether the dismiss lock timer is enabled. A zero delay is still valid when enabled. */
    val dismissTimerEnabled: Boolean = true,
    val restorePolicy: RestorePolicy = RestorePolicy.RESTORE_PREVIOUS,
    val contentMode: ContentMode = ContentMode.LOCAL,
    val visualUri: String? = null,
    val visualKind: VisualKind = VisualKind.NONE,
    val soundSource: SoundSource = SoundSource.DEFAULT_ALARM,
    val audioUri: String? = null,
    val youtubeUrl: String? = null,
    /** Presentation-only home-card preview toggle; it never changes scheduling. */
    val homePreviewEnabled: Boolean = true,
    val scheduleRevision: Long = 0L,
) {
    companion object {
        const val LEGACY_ALARM_ID = 1001
    }
}

enum class RepeatType {
    ONE_TIME,
    WEEKLY,
    DAILY,
}

enum class RestorePolicy {
    RESTORE_PREVIOUS,
    KEEP_CURRENT,
}

enum class ContentMode {
    LOCAL,
    YOUTUBE,
}

enum class VisualKind {
    NONE,
    IMAGE,
    ANIMATED_IMAGE,
    VIDEO,
}

enum class SoundSource {
    DEFAULT_ALARM,
    VISUAL_MEDIA,
    LOCAL_AUDIO,
}
