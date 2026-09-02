package com.routinealarm.app.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.util.Locale

enum class MediaLibraryTab(val label: String) {
    IMAGE("이미지"),
    GIF("GIF"),
    WEBP("WebP"),
    VIDEO("영상"),
    MUSIC("음악"),
}

enum class MediaLibrarySort(val label: String) {
    NEWEST("최근순"),
    NAME("이름순"),
}

data class DeviceMediaItem(
    val id: Long,
    val uri: String,
    val name: String,
    val mimeType: String,
    val tab: MediaLibraryTab,
    val dateAddedSeconds: Long,
    val durationMillis: Long? = null,
)

data class DeviceMediaQueryResult(
    val items: List<DeviceMediaItem> = emptyList(),
    val errorMessage: String? = null,
    val scannedRowCount: Int = 0,
)

object DeviceMediaLibrary {
    fun query(context: Context, tab: MediaLibraryTab): DeviceMediaQueryResult = runCatching {
        val itemsById = linkedMapOf<Long, DeviceMediaItem>()
        val queryFailures = mutableListOf<Throwable>()
        var successfulQueries = 0
        var scannedRowCount = 0

        mediaQuerySources(tab).forEach { source ->
            val sourceResult = runCatching {
                val cursor = context.contentResolver.query(
                    source.collection,
                    projectionFor(tab, source.includesDuration),
                    selectionFor(tab),
                    null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC",
                ) ?: error("MediaStore returned no cursor")
                cursor.use { currentCursor ->
                    val idIndex = currentCursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idIndex < 0) error("MediaStore result has no _id column")
                    val nameIndex = currentCursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val mimeIndex = currentCursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                    val dateIndex = currentCursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                    val durationIndex = if (source.includesDuration) {
                        currentCursor.getColumnIndex(
                            if (tab == MediaLibraryTab.MUSIC) {
                                MediaStore.Audio.Media.DURATION
                            } else {
                                MediaStore.Video.Media.DURATION
                            },
                        )
                    } else {
                        -1
                    }
                    while (currentCursor.moveToNext()) {
                        scannedRowCount += 1
                        val id = currentCursor.safeLong(idIndex) ?: continue
                        val rawMimeType = currentCursor.safeString(mimeIndex)
                            .orEmpty()
                            .lowercase(Locale.ROOT)
                        val displayName = currentCursor.safeDisplayName(nameIndex, id)
                        val actualTab = tabForMedia(rawMimeType, displayName) ?: continue
                        if (actualTab != tab) continue
                        val duration = currentCursor.safeLong(durationIndex)?.takeIf { it > 0L }
                        if (!shouldIncludeMediaItem(tab, duration)) continue
                        val item = DeviceMediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(source.collection, id).toString(),
                            name = displayName,
                            mimeType = rawMimeType.takeIf { tabForMimeType(it) == actualTab }
                                ?: representativeMimeType(actualTab),
                            tab = actualTab,
                            dateAddedSeconds = currentCursor.safeLong(dateIndex) ?: 0L,
                            durationMillis = duration,
                        )
                        itemsById[id] = preferredMediaItem(itemsById[id], item)
                    }
                }
            }
            sourceResult.onSuccess { successfulQueries += 1 }
                .onFailure { queryFailures += it }
        }
        if (successfulQueries == 0) {
            throw queryFailures.firstOrNull() ?: error("MediaStore query failed")
        }
        DeviceMediaQueryResult(
            items = itemsById.values.toList(),
            scannedRowCount = scannedRowCount,
        )
    }.getOrElse { error ->
        DeviceMediaQueryResult(
            errorMessage = when (error) {
                is SecurityException -> "기기 미디어 접근 권한이 없어 MediaStore를 조회하지 못했습니다. 권한을 다시 허용하거나 아래 선택기를 사용해 주세요."
                else -> "MediaStore 조회에 실패했습니다. 권한과 기기 미디어 상태를 확인하거나 아래 선택기를 사용해 주세요."
            },
        )
    }

    private fun Cursor.safeString(index: Int): String? =
        if (index < 0) null else runCatching { getString(index) }.getOrNull()

    private fun Cursor.safeLong(index: Int): Long? =
        if (index < 0 || isNull(index)) null else runCatching { getLong(index) }.getOrNull()

    private fun Cursor.safeDisplayName(index: Int, id: Long): String =
        safeString(index)?.trim().takeUnless { it.isNullOrEmpty() } ?: "파일 $id"

    private fun mediaQuerySources(tab: MediaLibraryTab): List<MediaQuerySource> = when (tab) {
        MediaLibraryTab.IMAGE,
        MediaLibraryTab.GIF,
        MediaLibraryTab.WEBP,
        -> listOf(MediaQuerySource(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false))

        MediaLibraryTab.VIDEO -> listOf(
            MediaQuerySource(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true),
            MediaQuerySource(externalFilesCollection(), false),
        )

        MediaLibraryTab.MUSIC -> listOf(MediaQuerySource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true))
    }

    private fun projectionFor(tab: MediaLibraryTab, includesDuration: Boolean): Array<String> = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.MediaColumns.DATE_ADDED)
        if (includesDuration && tab == MediaLibraryTab.VIDEO) add(MediaStore.Video.Media.DURATION)
        if (includesDuration && tab == MediaLibraryTab.MUSIC) add(MediaStore.Audio.Media.DURATION)
    }.toTypedArray()

    private fun selectionFor(tab: MediaLibraryTab): String? =
        if (tab == MediaLibraryTab.MUSIC) "${MediaStore.Audio.Media.IS_MUSIC} != 0" else null

    private fun externalFilesCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Files.getContentUri("external")
    }

    private fun preferredMediaItem(
        current: DeviceMediaItem?,
        candidate: DeviceMediaItem,
    ): DeviceMediaItem = when {
        current == null -> candidate
        current.name.startsWith("파일 ") && !candidate.name.startsWith("파일 ") -> candidate
        current.mimeType.endsWith("/*") && !candidate.mimeType.endsWith("/*") -> candidate
        current.durationMillis == null && candidate.durationMillis != null -> candidate
        else -> current
    }
}

private data class MediaQuerySource(
    val collection: Uri,
    val includesDuration: Boolean,
)

fun tabForMimeType(mimeType: String?): MediaLibraryTab? = when (mimeType?.lowercase(Locale.ROOT)) {
    "image/gif" -> MediaLibraryTab.GIF
    "image/webp" -> MediaLibraryTab.WEBP
    "image/jpeg",
    "image/png",
    "image/bmp",
    "image/heic",
    "image/heif",
    "image/avif",
    -> MediaLibraryTab.IMAGE

    "video/mp4",
    "video/3gpp",
    "video/webm",
    "video/x-matroska",
    "video/quicktime",
    -> MediaLibraryTab.VIDEO

    "audio/mpeg",
    "audio/mp3",
    "audio/x-mpeg",
    "audio/mp4",
    "audio/x-m4a",
    "audio/aac",
    "audio/flac",
    "audio/ogg",
    "audio/opus",
    "audio/wav",
    "audio/x-wav",
    "audio/3gpp",
    "audio/amr",
    -> MediaLibraryTab.MUSIC

    else -> null
}

/** Uses the provider MIME first, then recovers missing or generic MIME with the display-name extension. */
fun tabForMedia(mimeType: String?, fileName: String?): MediaLibraryTab? =
    tabForMimeType(mimeType) ?: tabForFileName(fileName)

fun shouldIncludeMediaItem(tab: MediaLibraryTab, durationMillis: Long?): Boolean =
    tab != MediaLibraryTab.MUSIC || durationMillis?.let { it > 0L } == true

/** Handles providers that expose a media file as application/octet-stream or omit MIME_TYPE. */
fun tabForFileName(fileName: String?): MediaLibraryTab? {
    val extension = fileName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase(Locale.ROOT)
        ?: return null
    return when (extension) {
        "jpg", "jpeg", "png", "bmp", "heic", "heif", "avif" -> MediaLibraryTab.IMAGE
        "gif" -> MediaLibraryTab.GIF
        "webp" -> MediaLibraryTab.WEBP
        "mp4", "m4v", "3gp", "3gpp", "3g2", "webm", "mkv", "mov" -> MediaLibraryTab.VIDEO
        "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "amr" -> MediaLibraryTab.MUSIC
        else -> null
    }
}

private fun representativeMimeType(tab: MediaLibraryTab): String = when (tab) {
    MediaLibraryTab.IMAGE -> "image/*"
    MediaLibraryTab.GIF -> "image/gif"
    MediaLibraryTab.WEBP -> "image/webp"
    MediaLibraryTab.VIDEO -> "video/*"
    MediaLibraryTab.MUSIC -> "audio/*"
}

fun filterAndSortMedia(
    items: List<DeviceMediaItem>,
    search: String,
    sort: MediaLibrarySort,
): List<DeviceMediaItem> {
    val query = search.trim()
    val filtered = if (query.isEmpty()) {
        items
    } else {
        items.filter { it.name.contains(query, ignoreCase = true) }
    }
    return when (sort) {
        MediaLibrarySort.NEWEST -> filtered.sortedWith(
            compareByDescending<DeviceMediaItem> { it.dateAddedSeconds }
                .thenBy { it.name.lowercase(Locale.getDefault()) },
        )
        MediaLibrarySort.NAME -> filtered.sortedWith(
            compareBy<DeviceMediaItem> { it.name.lowercase(Locale.getDefault()) }
                .thenByDescending { it.dateAddedSeconds },
        )
    }
}

fun requiredMediaPermission(tab: MediaLibraryTab): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        when (tab) {
            MediaLibraryTab.MUSIC -> Manifest.permission.READ_MEDIA_AUDIO
            MediaLibraryTab.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
            MediaLibraryTab.IMAGE,
            MediaLibraryTab.GIF,
            MediaLibraryTab.WEBP,
            -> Manifest.permission.READ_MEDIA_IMAGES
        }
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun requestedMediaPermissions(tab: MediaLibraryTab): Array<String> = buildList {
    add(requiredMediaPermission(tab))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && tab != MediaLibraryTab.MUSIC) {
        add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }
}.toTypedArray()

fun hasMediaLibraryAccess(context: Context, tab: MediaLibraryTab): Boolean {
    val directlyGranted = ContextCompat.checkSelfPermission(
        context,
        requiredMediaPermission(tab),
    ) == PackageManager.PERMISSION_GRANTED
    val selectedVisualGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    ) == PackageManager.PERMISSION_GRANTED
    return mediaLibraryAccessGranted(
        tab = tab,
        directlyGranted = directlyGranted,
        selectedVisualGranted = selectedVisualGranted,
        supportsSelectedVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
}

fun hasSelectedVisualOnlyAccess(context: Context, tab: MediaLibraryTab): Boolean {
    val directlyGranted = ContextCompat.checkSelfPermission(
        context,
        requiredMediaPermission(tab),
    ) == PackageManager.PERMISSION_GRANTED
    val selectedVisualGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    ) == PackageManager.PERMISSION_GRANTED
    return selectedVisualOnlyAccess(
        tab = tab,
        directlyGranted = directlyGranted,
        selectedVisualGranted = selectedVisualGranted,
        supportsSelectedVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
}

fun mediaLibraryAccessGranted(
    tab: MediaLibraryTab,
    directlyGranted: Boolean,
    selectedVisualGranted: Boolean,
    supportsSelectedVisual: Boolean,
): Boolean = directlyGranted || selectedVisualOnlyAccess(
    tab = tab,
    directlyGranted = directlyGranted,
    selectedVisualGranted = selectedVisualGranted,
    supportsSelectedVisual = supportsSelectedVisual,
)

fun selectedVisualOnlyAccess(
    tab: MediaLibraryTab,
    directlyGranted: Boolean,
    selectedVisualGranted: Boolean,
    supportsSelectedVisual: Boolean,
): Boolean = (
    supportsSelectedVisual &&
        tab != MediaLibraryTab.MUSIC &&
        !directlyGranted &&
        selectedVisualGranted
    )

fun mediaLibraryAccessNotice(tab: MediaLibraryTab, selectedVisualOnly: Boolean): String? {
    if (!selectedVisualOnly || tab == MediaLibraryTab.MUSIC) return null
    return if (tab == MediaLibraryTab.VIDEO) {
        "현재 선택한 영상만 접근 가능하며 전체 목록은 보이지 않습니다."
    } else {
        "현재 선택한 사진·영상만 접근 가능하며 전체 목록은 보이지 않습니다."
    }
}

fun mediaLibraryEmptyMessage(
    tab: MediaLibraryTab,
    search: String,
    scannedRowCount: Int,
    selectedVisualOnly: Boolean = false,
): String {
    if (search.isNotBlank()) return "검색 결과가 없습니다."
    mediaLibraryAccessNotice(tab, selectedVisualOnly)?.let { return it }
    return if (scannedRowCount == 0) {
        "MediaStore 조회는 완료됐지만 현재 권한에서 확인되는 ${tab.label} 파일이 0건입니다. Android 사진 선택기 또는 직접 찾아보기를 사용해 주세요."
    } else {
        "MediaStore에서 ${scannedRowCount}개 항목을 읽었지만 지원되는 ${tab.label} 형식이 없습니다. Android 사진 선택기 또는 직접 찾아보기를 사용해 주세요."
    }
}

fun formatMediaDuration(durationMillis: Long?): String {
    val totalSeconds = (durationMillis ?: 0L).coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    }
}
