package com.routinealarm.app.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeEmbedTest {
    @Test
    fun extractsWatchUrl() {
        assertEquals("dQw4w9WgXcQ", YouTubeEmbed.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun extractsShortAndEmbedUrls() {
        assertEquals("dQw4w9WgXcQ", YouTubeEmbed.videoId("https://youtu.be/dQw4w9WgXcQ?t=3"))
        assertEquals("dQw4w9WgXcQ", YouTubeEmbed.videoId("https://youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeEmbed.videoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test
    fun distinguishesShortsFromNormalYouTubeVideos() {
        assertTrue(YouTubeEmbed.isShortsUrl("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertFalse(YouTubeEmbed.isShortsUrl("https://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(YouTubeEmbed.isShortsUrl("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun rejectsLookalikeHostsAndInvalidIds() {
        assertFalse(YouTubeEmbed.isSupportedUrl("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"))
        assertFalse(YouTubeEmbed.isSupportedUrl("https://youtube.com/watch?v=too-short"))
    }

    @Test
    fun previewStateDistinguishesEmptyUnsupportedAndReadyUrls() {
        assertEquals(YouTubePreviewState.EMPTY, YouTubeEmbed.previewState("   "))
        assertEquals(
            YouTubePreviewState.UNSUPPORTED,
            YouTubeEmbed.previewState("https://vimeo.com/123456"),
        )
        assertEquals(
            YouTubePreviewState.READY,
            YouTubeEmbed.previewState("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun thumbnailUrlUsesTheOfficialImageForSupportedVideoOnly() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            YouTubeEmbed.thumbnailUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(null, YouTubeEmbed.thumbnailUrl("https://example.com/video"))
    }

    @Test
    fun thumbnailHtmlEmbedsTheOfficialThumbnailWithoutDownloadCode() {
        val html = requireNotNull(YouTubeEmbed.thumbnailHtml("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(html.contains("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"))
        assertTrue(html.contains("object-fit:cover"))
    }

    @Test
    fun generatedPlayerAutoplaysWithoutYouTubeControlsAndLoops() {
        val html = requireNotNull(
            YouTubeEmbed.playerHtml(
                "https://youtu.be/dQw4w9WgXcQ",
                "com.example.alarm",
            ),
        )
        assertTrue(html.contains("autoplay:1"))
        assertTrue(html.contains("controls:0"))
        assertTrue(html.contains("disablekb:1"))
        assertTrue(html.contains("fs:0"))
        assertTrue(html.contains("#player iframe{pointer-events:none}"))
        assertFalse(html.contains("controls:1"))
        assertTrue(html.contains("loop:1"))
        assertTrue(html.contains("origin:'https://com.example.alarm'"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
    }

    @Test
    fun generatedPlayerRestoresAndReportsPlaybackPosition() {
        val html = requireNotNull(
            YouTubeEmbed.playerHtml(
                value = "https://youtu.be/dQw4w9WgXcQ",
                startPositionMillis = 12_345L,
            ),
        )
        assertTrue(html.contains("seekTo(12.345,true)"))
        assertTrue(html.contains("RoutineAlarmBridge.onPosition"))
        assertTrue(html.contains("RoutineAlarmBridge.onReady"))
    }

    @Test
    fun appIdentityUsesHttpsApplicationIdOrigin() {
        assertEquals("https://com.routinealarm.app", YouTubeEmbed.appOrigin("com.routinealarm.app"))
    }

    @Test
    fun generatedPlayerExplainsEmbeddingAndClientIdentityFailures() {
        val html = requireNotNull(YouTubeEmbed.playerHtml("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(html.contains("event.data===101 || event.data===150"))
        assertTrue(html.contains("게시자가 앱 내 재생을 허용하지 않았습니다"))
        assertTrue(html.contains("event.data===153"))
        assertTrue(html.contains("YouTube가 이 앱을 확인하지 못했습니다"))
    }
}
