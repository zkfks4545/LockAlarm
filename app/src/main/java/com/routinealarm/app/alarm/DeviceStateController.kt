package com.routinealarm.app.alarm

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RestorePolicy
import kotlin.math.roundToInt

class DeviceStateController(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val sessionStore = AlarmSessionStore(context)

    fun applyInitialValuesOnce(
        alarm: AlarmSpec,
        occurrenceId: String,
        scheduleRevision: Long,
        sessionId: String,
    ) {
        val existingSession = sessionStore.load()
        if (existingSession != null) {
            if (
                existingSession.alarmId == alarm.id &&
                existingSession.occurrenceId == occurrenceId &&
                existingSession.sessionId == sessionId &&
                !existingSession.presetApplied
            ) {
                applyPreset(alarm)
                sessionStore.markPresetApplied(alarm.id)
            }
            return
        }

        val resolver = context.contentResolver
        val previousBrightness = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            DEFAULT_BRIGHTNESS,
        )
        val previousMode = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        sessionStore.begin(
            AlarmSession(
                alarmId = alarm.id,
                occurrenceId = occurrenceId,
                scheduleRevision = scheduleRevision,
                sessionId = sessionId,
                startedAtMillis = System.currentTimeMillis(),
                ringStartedAtMillis = System.currentTimeMillis(),
                previousBrightness = previousBrightness,
                previousBrightnessMode = previousMode,
                previousMediaVolume = previousVolume,
                restorePolicy = alarm.restorePolicy,
                presetApplied = false,
                snoozeCount = 0,
                snoozeDueAtMillis = null,
                snoozeDueAtElapsedRealtime = null,
                snoozeBootCount = null,
                state = AlarmSessionState.FIRING,
            ),
        )

        applyPreset(alarm)
        sessionStore.markPresetApplied(alarm.id)
    }

    private fun applyPreset(alarm: AlarmSpec) {
        val resolver = context.contentResolver
        if (Settings.System.canWrite(context)) {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS,
                percentToBrightness(alarm.brightnessPercent),
            )
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = DeviceVolumeRampPolicy.targetVolume(maxVolume, alarm.mediaVolumePercent)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        }
    }

    fun finishAndMaybeRestore() {
        val session = sessionStore.load() ?: return
        if (session.restorePolicy == RestorePolicy.RESTORE_PREVIOUS) {
            restorePreviousBrightness(session)
            runCatching {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    session.previousMediaVolume,
                    0,
                )
            }
        }
        sessionStore.clear()
    }

    private fun restorePreviousBrightness(session: AlarmSession) {
        if (!Settings.System.canWrite(context)) return

        val resolver = context.contentResolver
        // The alarm keeps the display in manual mode while it is ringing. Set
        // the saved level first so it takes effect immediately, then restore
        // the saved automatic/manual mode. Writing the mode first can leave a
        // previously automatic display at the alarm's boosted level until the
        // next sensor update on some devices.
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS,
            session.previousBrightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
        )
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            session.previousBrightnessMode,
        )
    }

    private fun percentToBrightness(percent: Int): Int =
        (MIN_BRIGHTNESS + (MAX_BRIGHTNESS - MIN_BRIGHTNESS) * percent.coerceIn(0, 100) / 100f)
            .roundToInt()

    private companion object {
        const val MIN_BRIGHTNESS = 1
        const val MAX_BRIGHTNESS = 255
        const val DEFAULT_BRIGHTNESS = 128
    }
}
