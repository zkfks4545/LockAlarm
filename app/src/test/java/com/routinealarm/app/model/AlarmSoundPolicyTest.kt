package com.routinealarm.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSoundPolicyTest {
    @Test
    fun localMusicAlwaysHasPriority() {
        assertEquals(
            SoundSource.LOCAL_AUDIO,
            AlarmSoundPolicy.resolve(VisualKind.VIDEO, "content://video", "content://music"),
        )
    }

    @Test
    fun videoAudioIsUsedWhenThereIsNoLocalMusic() {
        assertEquals(
            SoundSource.VISUAL_MEDIA,
            AlarmSoundPolicy.resolve(VisualKind.VIDEO, "content://video", null),
        )
    }

    @Test
    fun imageWithoutMusicUsesDefaultAlarm() {
        assertEquals(
            SoundSource.DEFAULT_ALARM,
            AlarmSoundPolicy.resolve(VisualKind.IMAGE, "content://image", null),
        )
    }

    @Test
    fun missingVideoUriUsesDefaultAlarm() {
        assertEquals(
            SoundSource.DEFAULT_ALARM,
            AlarmSoundPolicy.resolve(VisualKind.VIDEO, null, null),
        )
    }
}
