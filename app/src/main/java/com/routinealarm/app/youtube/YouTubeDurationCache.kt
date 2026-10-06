package com.routinealarm.app.youtube

/** Process-local duration reuse for videos that the user has previewed. */
object YouTubeDurationCache {
    private const val MAX_ENTRIES = 64
    private val durationsByVideoId = LinkedHashMap<String, Int>(MAX_ENTRIES, 0.75f, true)

    @Synchronized
    fun durationFor(url: String): Int? = YouTubeEmbed.videoId(url)
        ?.let(durationsByVideoId::get)

    @Synchronized
    fun durationForSelection(previousUrl: String, previousDurationSeconds: Int?, nextUrl: String): Int? {
        val nextVideoId = YouTubeEmbed.videoId(nextUrl) ?: return null
        val previousVideoId = YouTubeEmbed.videoId(previousUrl)
        return if (nextVideoId == previousVideoId) {
            previousDurationSeconds?.takeIf { it > 0 } ?: durationsByVideoId[nextVideoId]
        } else {
            durationsByVideoId[nextVideoId]
        }
    }

    @Synchronized
    fun remember(url: String, durationSeconds: Int) {
        val videoId = YouTubeEmbed.videoId(url) ?: return
        if (durationSeconds <= 0) return
        durationsByVideoId[videoId] = durationSeconds
        if (durationsByVideoId.size > MAX_ENTRIES) {
            durationsByVideoId.remove(durationsByVideoId.keys.first())
        }
    }
}
