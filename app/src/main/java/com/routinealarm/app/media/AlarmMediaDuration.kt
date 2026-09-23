package com.routinealarm.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.routinealarm.app.alarm.DismissTimerPolicy
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.model.VisualKind

object AlarmMediaDuration {
    data class LookupResult(val maxDurationSeconds: Int?)

    fun lookup(context: Context, alarm: AlarmSpec): LookupResult {
        if (alarm.contentMode != ContentMode.LOCAL) return LookupResult(null)

        val maxDurationMillis = candidateUris(alarm)
            .mapNotNull { uriString -> readDurationMillis(context, Uri.parse(uriString)) }
            .maxOrNull()
        return LookupResult(durationSecondsFromMillis(maxDurationMillis))
    }

    fun hasDurationCandidate(alarm: AlarmSpec): Boolean =
        alarm.contentMode == ContentMode.LOCAL && candidateUris(alarm).isNotEmpty()

    fun effectiveDismissDelaySeconds(context: Context, alarm: AlarmSpec): Int {
        if (!alarm.dismissTimerEnabled) return 0
        val mediaDurationSeconds = lookup(context, alarm).maxDurationSeconds
        return DismissTimerPolicy.clampDelaySeconds(
            delaySeconds = alarm.dismissDelaySeconds,
            maxDelaySeconds = DismissTimerPolicy.maxDelaySeconds(mediaDurationSeconds),
        )
    }

    fun durationSecondsFromMillis(durationMillis: Long?): Int? {
        if (durationMillis == null || durationMillis <= 0L) return null
        val seconds = durationMillis / 1_000L +
            if (durationMillis % 1_000L == 0L) 0L else 1L
        return seconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun candidateUris(alarm: AlarmSpec): List<String> = buildList {
        alarm.audioUri
            ?.takeIf(String::isNotBlank)
            ?.let(::add)
        if (alarm.visualKind == VisualKind.VIDEO || alarm.visualKind == VisualKind.ANIMATED_IMAGE) {
            alarm.visualUri
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
        }
    }.distinct()

    private fun readDurationMillis(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
