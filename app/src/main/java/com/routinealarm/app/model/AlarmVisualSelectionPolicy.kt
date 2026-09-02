package com.routinealarm.app.model

object AlarmVisualSelectionPolicy {
    fun apply(
        current: AlarmSpec,
        visualUri: String,
        visualKind: VisualKind,
    ): AlarmSpec = current.copy(
        visualUri = visualUri,
        visualKind = visualKind,
        audioUri = null,
        soundSource = AlarmSoundPolicy.resolve(
            visualKind = visualKind,
            visualUri = visualUri,
            audioUri = null,
        ),
    )
}
