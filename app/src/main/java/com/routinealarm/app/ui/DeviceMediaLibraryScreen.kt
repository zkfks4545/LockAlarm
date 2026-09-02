package com.routinealarm.app.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routinealarm.app.media.DeviceMediaItem
import com.routinealarm.app.media.DeviceMediaLibrary
import com.routinealarm.app.media.DeviceMediaQueryResult
import com.routinealarm.app.media.MediaLibrarySort
import com.routinealarm.app.media.MediaLibraryTab
import com.routinealarm.app.media.filterAndSortMedia
import com.routinealarm.app.media.formatMediaDuration
import com.routinealarm.app.media.hasMediaLibraryAccess
import com.routinealarm.app.media.hasSelectedVisualOnlyAccess
import com.routinealarm.app.media.mediaLibraryAccessNotice
import com.routinealarm.app.media.mediaLibraryEmptyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DeviceMediaLibraryScreen(
    initialTab: MediaLibraryTab,
    permissionRevision: Int,
    onRequestAccess: (MediaLibraryTab) -> Unit,
    onSelect: (DeviceMediaItem) -> Unit,
    onOpenAndroidPhotoPicker: (MediaLibraryTab) -> Unit,
    onDirectBrowse: (MediaLibraryTab) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var sort by remember { mutableStateOf(MediaLibrarySort.NEWEST) }
    var search by remember { mutableStateOf("") }
    var queryResult by remember { mutableStateOf<DeviceMediaQueryResult?>(null) }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewMessage by remember { mutableStateOf<String?>(null) }
    val hasAccess = hasMediaLibraryAccess(context, tab)
    val selectedVisualOnlyAccess = hasSelectedVisualOnlyAccess(context, tab)

    fun stopPreview() {
        previewPlayer?.runCatching { stop() }
        previewPlayer?.release()
        previewPlayer = null
        previewUri = null
    }

    fun togglePreview(item: DeviceMediaItem) {
        if (previewUri == item.uri) {
            stopPreview()
            return
        }
        stopPreview()
        previewMessage = null
        runCatching {
            MediaPlayer().also { player ->
                player.setDataSource(context, Uri.parse(item.uri))
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener {
                    it.release()
                    if (previewPlayer === it) previewPlayer = null
                    if (previewUri == item.uri) previewUri = null
                }
                player.setOnErrorListener { failedPlayer, _, _ ->
                    failedPlayer.release()
                    if (previewPlayer === failedPlayer) previewPlayer = null
                    if (previewUri == item.uri) previewUri = null
                    previewMessage = "이 음악을 미리 재생할 수 없습니다."
                    true
                }
                player.prepareAsync()
                previewPlayer = player
                previewUri = item.uri
            }
        }.onFailure {
            stopPreview()
            previewMessage = "이 음악을 미리 재생할 수 없습니다."
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewPlayer?.release()
            previewPlayer = null
        }
    }
    LaunchedEffect(tab, permissionRevision, hasAccess, selectedVisualOnlyAccess) {
        if (tab != MediaLibraryTab.MUSIC) stopPreview()
        queryResult = null
        if (hasAccess) {
            queryResult = withContext(Dispatchers.IO) {
                DeviceMediaLibrary.query(context, tab)
            }
        }
    }

    val displayedItems = remember(queryResult, search, sort) {
        filterAndSortMedia(queryResult?.items.orEmpty(), search, sort)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text("기기 미디어", style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaLibraryTab.entries.forEach { candidate ->
                FilterChip(
                    selected = tab == candidate,
                    onClick = { tab = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            value = search,
            onValueChange = { search = it.take(80) },
            label = { Text("파일명 검색") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaLibrarySort.entries.forEach { candidate ->
                FilterChip(
                    selected = sort == candidate,
                    onClick = { sort = candidate },
                    label = { Text(candidate.label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tab != MediaLibraryTab.MUSIC) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAndroidPhotoPicker(tab) },
                ) { Text("Android 사진 선택기") }
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { onDirectBrowse(tab) },
            ) { Text("직접 찾아보기") }
        }
        mediaLibraryAccessNotice(tab, selectedVisualOnlyAccess)?.let { notice ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(notice, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { onRequestAccess(tab) }) {
                        Text(if (tab == MediaLibraryTab.VIDEO) "전체 영상 접근 허용" else "전체 사진·영상 접근 허용")
                    }
                }
            }
        }
        previewMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        when {
            !hasAccess -> MediaAccessRequired(
                tab = tab,
                onRequestAccess = { onRequestAccess(tab) },
                modifier = Modifier.weight(1f),
            )

            queryResult == null -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            queryResult?.errorMessage != null -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(queryResult?.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            displayedItems.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    mediaLibraryEmptyMessage(
                        tab = tab,
                        search = search,
                        scannedRowCount = queryResult?.scannedRowCount ?: 0,
                        selectedVisualOnly = selectedVisualOnlyAccess,
                    ),
                )
            }

            tab == MediaLibraryTab.MUSIC -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayedItems, key = { it.uri }) { item ->
                    MusicMediaRow(
                        item = item,
                        isPreviewing = previewUri == item.uri,
                        onTogglePreview = { togglePreview(item) },
                        onSelect = { onSelect(item) },
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(118.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayedItems, key = { it.uri }) { item ->
                    VisualMediaTile(item = item, onSelect = { onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun MediaAccessRequired(
    tab: MediaLibraryTab,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (tab == MediaLibraryTab.MUSIC) {
                    "기기의 음악 목록을 표시하려면 음악 접근을 허용해 주세요."
                } else {
                    "기기의 ${tab.label} 목록을 표시하려면 사진·영상 접근을 허용해 주세요. Android 14 이상에서 선택한 사진·영상만 허용하면 선택한 항목만 표시됩니다."
                },
            )
            Button(onClick = onRequestAccess) { Text("기기 미디어 접근 허용") }
            Text(
                "권한 없이도 위의 Android 사진 선택기 또는 직접 찾아보기를 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VisualMediaTile(item: DeviceMediaItem, onSelect: () -> Unit) {
    Card(onClick = onSelect) {
        Column {
            MediaThumbnail(
                item = item,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (item.durationMillis != null) {
                    Text(formatMediaDuration(item.durationMillis), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MusicMediaRow(
    item: DeviceMediaItem,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onSelect: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(modifier = Modifier.size(52.dp), onClick = onTogglePreview) {
                Text(if (isPreviewing) "■" else "▶")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatMediaDuration(item.durationMillis), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onSelect) { Text("선택") }
        }
    }
}

@Composable
private fun MediaThumbnail(item: DeviceMediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(360, 360), null)
                } else if (item.tab == MediaLibraryTab.VIDEO) {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null,
                    )
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        context.contentResolver,
                        item.id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null,
                    )
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Text(item.tab.label, color = Color.White)
    }
}
