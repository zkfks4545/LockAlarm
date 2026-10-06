package com.routinealarm.app.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeDurationCacheTest {
    @Test
    fun previewedDurationIsReusedOnlyForTheSameVideoId() {
        val firstUrl = "https://youtu.be/AAAABBBBCCC"
        val sameVideoUrl = "https://www.youtube.com/watch?v=AAAABBBBCCC"
        val otherUrl = "https://youtu.be/DDDDFFFFGGG"

        YouTubeDurationCache.remember(firstUrl, 321)
        assertEquals(321, YouTubeDurationCache.durationFor(sameVideoUrl))
        assertNull(YouTubeDurationCache.durationFor(otherUrl))
        assertEquals(
            321,
            YouTubeDurationCache.durationForSelection(firstUrl, 321, sameVideoUrl),
        )
        assertNull(YouTubeDurationCache.durationForSelection(firstUrl, 321, otherUrl))
    }

    @Test
    fun changingToAPreviewedVideoLoadsOnlyThatVideosDuration() {
        val firstUrl = "https://youtu.be/HHHHIIIIJJJ"
        val secondUrl = "https://youtu.be/KKKKLLLLMMM"
        YouTubeDurationCache.remember(secondUrl, 87)

        assertEquals(
            87,
            YouTubeDurationCache.durationForSelection(firstUrl, 500, secondUrl),
        )
        assertNull(YouTubeDurationCache.durationForSelection(firstUrl, 500, "invalid URL"))
    }

    @Test
    fun invalidAndUnknownDurationsAreNotCached() {
        val url = "https://youtu.be/NNNNOOOOPPP"
        YouTubeDurationCache.remember(url, 0)
        YouTubeDurationCache.remember("not a YouTube URL", 200)

        assertNull(YouTubeDurationCache.durationFor(url))
        assertNull(YouTubeDurationCache.durationForSelection(url, null, url))
    }
}
