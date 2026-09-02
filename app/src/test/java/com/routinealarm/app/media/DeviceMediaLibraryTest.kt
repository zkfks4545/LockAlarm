package com.routinealarm.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMediaLibraryTest {
    @Test
    fun `supported image formats are separated into their tabs`() {
        assertEquals(MediaLibraryTab.IMAGE, tabForMimeType("image/jpeg"))
        assertEquals(MediaLibraryTab.GIF, tabForMimeType("image/gif"))
        assertEquals(MediaLibraryTab.WEBP, tabForMimeType("image/webp"))
    }

    @Test
    fun `supported video and music formats are accepted`() {
        assertEquals(MediaLibraryTab.VIDEO, tabForMimeType("video/mp4"))
        assertEquals(MediaLibraryTab.VIDEO, tabForMimeType("video/webm"))
        assertEquals(MediaLibraryTab.MUSIC, tabForMimeType("audio/mpeg"))
        assertEquals(MediaLibraryTab.MUSIC, tabForMimeType("audio/mp3"))
        assertEquals(MediaLibraryTab.MUSIC, tabForMimeType("audio/x-m4a"))
        assertEquals(MediaLibraryTab.MUSIC, tabForMimeType("audio/flac"))
    }

    @Test
    fun `unsupported formats are excluded`() {
        assertNull(tabForMimeType("application/pdf"))
        assertNull(tabForMimeType("image/tiff"))
        assertNull(tabForMimeType("audio/x-unknown"))
    }

    @Test
    fun `file extension recovers media when provider MIME is missing`() {
        assertEquals(MediaLibraryTab.IMAGE, tabForFileName("holiday.JPG"))
        assertEquals(MediaLibraryTab.GIF, tabForFileName("animation.gif"))
        assertEquals(MediaLibraryTab.WEBP, tabForFileName("sticker.webp"))
        listOf("mp4", "m4v", "3gp", "3g2", "webm", "mkv", "mov").forEach { extension ->
            assertEquals(MediaLibraryTab.VIDEO, tabForFileName("clip.$extension"))
        }
        assertEquals(MediaLibraryTab.MUSIC, tabForFileName("alarm.MP3"))
        assertNull(tabForFileName("manual.pdf"))
    }

    @Test
    fun `generic or manufacturer MIME falls back to supported display name extension`() {
        assertEquals(
            MediaLibraryTab.VIDEO,
            tabForMedia("", "camera.mp4"),
        )
        assertEquals(
            MediaLibraryTab.VIDEO,
            tabForMedia("application/octet-stream", "camera.m4v"),
        )
        assertEquals(
            MediaLibraryTab.VIDEO,
            tabForMedia("video/x-manufacturer-recording", "camera.mov"),
        )
        assertNull(tabForMedia("application/octet-stream", "manual.pdf"))
    }

    @Test
    fun `video items remain visible without a usable duration while music keeps old filter`() {
        assertEquals(true, shouldIncludeMediaItem(MediaLibraryTab.VIDEO, null))
        assertEquals(true, shouldIncludeMediaItem(MediaLibraryTab.VIDEO, 0L))
        assertEquals(true, shouldIncludeMediaItem(MediaLibraryTab.VIDEO, 1_000L))
        assertEquals(false, shouldIncludeMediaItem(MediaLibraryTab.MUSIC, null))
        assertEquals(false, shouldIncludeMediaItem(MediaLibraryTab.MUSIC, 0L))
        assertEquals(true, shouldIncludeMediaItem(MediaLibraryTab.MUSIC, 1_000L))
    }

    @Test
    fun `video access can use Android 14 selected visual permission while music cannot`() {
        assertEquals(
            true,
            mediaLibraryAccessGranted(
                tab = MediaLibraryTab.VIDEO,
                directlyGranted = false,
                selectedVisualGranted = true,
                supportsSelectedVisual = true,
            ),
        )
        assertEquals(
            false,
            mediaLibraryAccessGranted(
                tab = MediaLibraryTab.MUSIC,
                directlyGranted = false,
                selectedVisualGranted = true,
                supportsSelectedVisual = true,
            ),
        )
        assertEquals(
            false,
            mediaLibraryAccessGranted(
                tab = MediaLibraryTab.VIDEO,
                directlyGranted = false,
                selectedVisualGranted = true,
                supportsSelectedVisual = false,
            ),
        )
    }

    @Test
    fun `selected visual permission is reported as partial access only without direct permission`() {
        assertEquals(
            true,
            selectedVisualOnlyAccess(
                tab = MediaLibraryTab.VIDEO,
                directlyGranted = false,
                selectedVisualGranted = true,
                supportsSelectedVisual = true,
            ),
        )
        assertEquals(
            false,
            selectedVisualOnlyAccess(
                tab = MediaLibraryTab.VIDEO,
                directlyGranted = true,
                selectedVisualGranted = true,
                supportsSelectedVisual = true,
            ),
        )
        assertEquals(
            false,
            selectedVisualOnlyAccess(
                tab = MediaLibraryTab.MUSIC,
                directlyGranted = false,
                selectedVisualGranted = true,
                supportsSelectedVisual = true,
            ),
        )
    }

    @Test
    fun `selected visual access has a clear video limitation notice`() {
        assertEquals(
            "현재 선택한 영상만 접근 가능하며 전체 목록은 보이지 않습니다.",
            mediaLibraryAccessNotice(MediaLibraryTab.VIDEO, true),
        )
        assertEquals(
            "현재 선택한 영상만 접근 가능하며 전체 목록은 보이지 않습니다.",
            mediaLibraryEmptyMessage(MediaLibraryTab.VIDEO, "", 0, selectedVisualOnly = true),
        )
        assertEquals(null, mediaLibraryAccessNotice(MediaLibraryTab.MUSIC, true))
    }

    @Test
    fun `empty messages distinguish no rows from unsupported rows and search`() {
        assertEquals(
            true,
            mediaLibraryEmptyMessage(MediaLibraryTab.VIDEO, "", 0)
                .contains("0건"),
        )
        assertEquals(
            true,
            mediaLibraryEmptyMessage(MediaLibraryTab.VIDEO, "", 4)
                .contains("4개 항목"),
        )
        assertEquals(
            "검색 결과가 없습니다.",
            mediaLibraryEmptyMessage(MediaLibraryTab.VIDEO, "camera", 4),
        )
    }

    @Test
    fun `filename search ignores case`() {
        val result = filterAndSortMedia(
            items = listOf(item("Morning Song.MP3", 1), item("evening.mp3", 2)),
            search = "SONG",
            sort = MediaLibrarySort.NEWEST,
        )

        assertEquals(listOf("Morning Song.MP3"), result.map { it.name })
    }

    @Test
    fun `newest sort orders date descending`() {
        val result = filterAndSortMedia(
            items = listOf(item("old", 100), item("new", 300), item("middle", 200)),
            search = "",
            sort = MediaLibrarySort.NEWEST,
        )

        assertEquals(listOf("new", "middle", "old"), result.map { it.name })
    }

    @Test
    fun `name sort ignores case and orders ascending`() {
        val result = filterAndSortMedia(
            items = listOf(item("zebra", 1), item("Alpha", 2), item("beta", 3)),
            search = "",
            sort = MediaLibrarySort.NAME,
        )

        assertEquals(listOf("Alpha", "beta", "zebra"), result.map { it.name })
    }

    @Test
    fun `duration formats short and long media`() {
        assertEquals("3:05", formatMediaDuration(185_000L))
        assertEquals("1:02:03", formatMediaDuration(3_723_000L))
    }

    private fun item(name: String, date: Long) = DeviceMediaItem(
        id = date,
        uri = "content://test/$date",
        name = name,
        mimeType = "audio/mpeg",
        tab = MediaLibraryTab.MUSIC,
        dateAddedSeconds = date,
        durationMillis = 1_000L,
    )
}
