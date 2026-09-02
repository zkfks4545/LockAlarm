package com.routinealarm.app.model

object AlarmSoundPolicy {
    fun resolve(
        visualKind: VisualKind,
        visualUri: String?,
        audioUri: String?,
    ): SoundSource = when {
        !audioUri.isNullOrBlank() -> SoundSource.LOCAL_AUDIO
        visualKind == VisualKind.VIDEO && !visualUri.isNullOrBlank() -> SoundSource.VISUAL_MEDIA
        else -> SoundSource.DEFAULT_ALARM
    }
}
