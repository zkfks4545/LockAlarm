package com.routinealarm.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmVisualSelectionPolicyTest {
    @Test
    fun `selecting an image removes previous music and uses default sound`() {
        val updated = AlarmVisualSelectionPolicy.apply(
            current = alarmWithMusic(VisualKind.VIDEO, "content://old-video"),
            visualUri = "content://new-image",
            visualKind = VisualKind.IMAGE,
        )

        assertEquals("content://new-image", updated.visualUri)
        assertEquals(VisualKind.IMAGE, updated.visualKind)
        assertNull(updated.audioUri)
        assertEquals(SoundSource.DEFAULT_ALARM, updated.soundSource)
    }

    @Test
    fun `selecting a video after another video removes previous music and uses video sound`() {
        val updated = AlarmVisualSelectionPolicy.apply(
            current = alarmWithMusic(VisualKind.VIDEO, "content://old-video"),
            visualUri = "content://new-video",
            visualKind = VisualKind.VIDEO,
        )

        assertEquals("content://new-video", updated.visualUri)
        assertEquals(VisualKind.VIDEO, updated.visualKind)
        assertNull(updated.audioUri)
        assertEquals(SoundSource.VISUAL_MEDIA, updated.soundSource)
    }

    @Test
    fun `selecting a video after an image also clears stale audio`() {
        val updated = AlarmVisualSelectionPolicy.apply(
            current = alarmWithMusic(VisualKind.IMAGE, "content://old-image"),
            visualUri = "content://new-video",
            visualKind = VisualKind.VIDEO,
        )

        assertNull(updated.audioUri)
        assertEquals(SoundSource.VISUAL_MEDIA, updated.soundSource)
    }

    private fun alarmWithMusic(visualKind: VisualKind, visualUri: String) = AlarmSpec(
        triggerAtMillis = 1_000L,
        contentMode = ContentMode.LOCAL,
        visualKind = visualKind,
        visualUri = visualUri,
        audioUri = "content://old-music",
        soundSource = SoundSource.LOCAL_AUDIO,
    )
}
