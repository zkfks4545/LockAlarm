package com.routinealarm.app

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.routinealarm.app.alarm.AlarmPlaybackService
import com.routinealarm.app.alarm.AlarmScheduleResolver
import com.routinealarm.app.alarm.AlarmScheduler
import com.routinealarm.app.alarm.AlarmSessionStore
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.media.DeviceMediaItem
import com.routinealarm.app.media.MediaLibraryTab
import com.routinealarm.app.media.requestedMediaPermissions
import com.routinealarm.app.media.tabForMedia
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.AlarmSoundPolicy
import com.routinealarm.app.model.AlarmVisualSelectionPolicy
import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.model.RepeatType
import com.routinealarm.app.model.RestorePolicy
import com.routinealarm.app.model.SoundSource
import com.routinealarm.app.model.VisualKind
import com.routinealarm.app.ui.RoutineAlarmTheme
import com.routinealarm.app.ui.AlarmVisual
import com.routinealarm.app.ui.DeviceMediaLibraryScreen
import com.routinealarm.app.youtube.YouTubeEmbed
import com.routinealarm.app.youtube.YouTubePreview
import com.routinealarm.app.youtube.YouTubeThumbnailOnly
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

class MainActivity : ComponentActivity() {
    private var permissionState by mutableStateOf(PermissionState())
    private var darkTheme by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme = getPreferences(MODE_PRIVATE).getBoolean(KEY_DARK_THEME, true)
        applySystemThemeBars(darkTheme)
        refreshPermissionState()
        setContent {
            RoutineAlarmTheme(darkTheme = darkTheme) {
                RoutineAlarmApp(
                    permissions = permissionState,
                    darkTheme = darkTheme,
                    onThemeChange = { enabled ->
                        darkTheme = enabled
                        applySystemThemeBars(enabled)
                        getPreferences(MODE_PRIVATE).edit()
                            .putBoolean(KEY_DARK_THEME, enabled)
                            .apply()
                    },
                    onPermissionsChanged = ::refreshPermissionState,
                    onOpenExactAlarmAccess = ::openExactAlarmAccess,
                    onOpenWriteSettings = ::openWriteSettings,
                    onOpenFullScreenAccess = ::openFullScreenAccess,
                    onOpenOverlayAccess = ::openOverlayAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    private fun refreshPermissionState() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val notificationManager = getSystemService(NotificationManager::class.java)
        permissionState = PermissionState(
            exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
            writeSettings = Settings.System.canWrite(this),
            fullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                notificationManager.canUseFullScreenIntent(),
            notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            drawOverlays = Settings.canDrawOverlays(this),
        )
    }

    private fun openExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openWriteSettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:$packageName")),
        )
    }

    private fun openFullScreenAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    private fun openOverlayAccess() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:$packageName")),
        )
    }

    private fun applySystemThemeBars(dark: Boolean) {
        window.statusBarColor = android.graphics.Color.parseColor(if (dark) "#07090B" else "#F4F6FA")
        window.navigationBarColor = android.graphics.Color.parseColor(if (dark) "#07090B" else "#F4F6FA")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    private companion object {
        const val KEY_DARK_THEME = "main_dark_theme"
    }
}

data class PermissionState(
    val exactAlarm: Boolean = false,
    val writeSettings: Boolean = false,
    val fullScreenIntent: Boolean = false,
    val notifications: Boolean = false,
    val drawOverlays: Boolean = false,
) {
    val allGranted: Boolean
        get() = exactAlarm && writeSettings && fullScreenIntent && notifications && drawOverlays

    val missingAccessNames: List<String>
        get() = buildList {
            if (!exactAlarm) add("정확한 알람")
            if (!writeSettings) add("시스템 밝기 변경")
            if (!fullScreenIntent) add("전체화면 알람")
            if (!drawOverlays) add("다른 앱 위 전체화면 표시")
            if (!notifications) add("알림 표시")
        }

    fun blockedEditingMessage(): String =
        "필수 접근을 먼저 완료해 주세요: ${missingAccessNames.joinToString()}"
}

private enum class AppDestination { ALARMS, STOPWATCH, TIMER }

private data class StopwatchLap(
    val number: Int,
    val elapsedMillis: Long,
    val splitMillis: Long,
)

@Composable
private fun RoutineAlarmApp(
    permissions: PermissionState,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onPermissionsChanged: () -> Unit,
    onOpenExactAlarmAccess: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenFullScreenAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    val scheduler = remember { AlarmScheduler(context) }
    val alarms by produceState<List<AlarmSpec>>(emptyList(), repository) {
        repository.observeAll().collect { value = it }
    }
    var destination by remember { mutableStateOf(AppDestination.ALARMS) }
    var editing by remember { mutableStateOf<AlarmSpec?>(null) }
    var mediaLibraryTab by remember { mutableStateOf<MediaLibraryTab?>(null) }
    var mediaPermissionRevision by remember { mutableStateOf(0) }
    var showStartupPermissionSetup by remember { mutableStateOf(!permissions.allGranted) }
    var message by remember { mutableStateOf<String?>(null) }
    var stopwatchRunning by rememberSaveable { mutableStateOf(false) }
    var stopwatchElapsedMillis by rememberSaveable { mutableStateOf(0L) }
    var stopwatchStartedAtMillis by rememberSaveable { mutableStateOf(0L) }
    var stopwatchLaps by remember { mutableStateOf(emptyList<StopwatchLap>()) }
    var timerDurationMillis by rememberSaveable {
        mutableStateOf(CountdownTimerPolicy.DEFAULT_DURATION_MILLIS)
    }
    var timerRemainingMillis by rememberSaveable {
        mutableStateOf(CountdownTimerPolicy.DEFAULT_DURATION_MILLIS)
    }
    var timerStartedAtMillis by rememberSaveable { mutableStateOf(0L) }
    var timerRunning by rememberSaveable { mutableStateOf(false) }
    var timerInputSeconds by rememberSaveable { mutableStateOf("300") }

    fun applyVisualUri(uri: Uri, mimeTypeHint: String? = null): Boolean {
        val mimeType = mimeTypeHint ?: context.contentResolver.getType(uri)
        val selectedTab = tabForMedia(mimeType, selectedFileName(context, uri.toString()))
        val kind = when (selectedTab) {
            MediaLibraryTab.IMAGE -> VisualKind.IMAGE
            MediaLibraryTab.GIF,
            MediaLibraryTab.WEBP,
            -> VisualKind.ANIMATED_IMAGE
            MediaLibraryTab.VIDEO -> VisualKind.VIDEO
            else -> null
        }
        if (kind == null) {
            message = "지원하지 않는 이미지·영상 형식입니다."
            return false
        }
        editing = editing?.let { current ->
            AlarmVisualSelectionPolicy.apply(
                current = current,
                visualUri = uri.toString(),
                visualKind = kind,
            )
        }
        return true
    }

    fun applyAudioUri(uri: Uri, mimeTypeHint: String? = null): Boolean {
        val mimeType = mimeTypeHint ?: context.contentResolver.getType(uri)
        val selectedTab = tabForMedia(mimeType, selectedFileName(context, uri.toString()))
        if (selectedTab != MediaLibraryTab.MUSIC) {
            message = "지원하지 않는 음악 형식입니다."
            return false
        }
        editing = editing?.copy(
            audioUri = uri.toString(),
            soundSource = SoundSource.LOCAL_AUDIO,
        )
        return true
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        message = if (granted) "알림 권한을 허용했습니다." else "알림 권한이 필요합니다."
        onPermissionsChanged()
    }
    val mediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionsResult ->
        mediaPermissionRevision += 1
        if (permissionsResult.values.none { it }) {
            message = "선택한 탭의 기기 미디어 접근이 허용되지 않았습니다."
        }
    }
    val directVisualPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (applyVisualUri(uri)) mediaLibraryTab = null
        }
    }
    val directAudioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (applyAudioUri(uri)) mediaLibraryTab = null
        }
    }
    val androidPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (applyVisualUri(uri)) mediaLibraryTab = null
        }
    }

    LaunchedEffect(permissions.allGranted) {
        if (permissions.allGranted) showStartupPermissionSetup = false
        if (!permissions.allGranted && editing != null) {
            mediaLibraryTab = null
            editing = null
            message = permissions.blockedEditingMessage()
        }
    }

    if (showStartupPermissionSetup && !permissions.allGranted) {
        PermissionSetupScreen(
            permissions = permissions,
            onOpenExactAlarmAccess = onOpenExactAlarmAccess,
            onOpenWriteSettings = onOpenWriteSettings,
            onOpenFullScreenAccess = onOpenFullScreenAccess,
            onOpenOverlayAccess = onOpenOverlayAccess,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onContinueReadOnly = { showStartupPermissionSetup = false },
        )
        return
    }

    if (editing != null && permissions.allGranted && mediaLibraryTab != null) {
        DeviceMediaLibraryScreen(
            initialTab = mediaLibraryTab!!,
            permissionRevision = mediaPermissionRevision,
            onRequestAccess = { tab -> mediaPermission.launch(requestedMediaPermissions(tab)) },
            onSelect = { item: DeviceMediaItem ->
                val applied = if (item.tab == MediaLibraryTab.MUSIC) {
                    applyAudioUri(Uri.parse(item.uri), item.mimeType)
                } else {
                    applyVisualUri(Uri.parse(item.uri), item.mimeType)
                }
                if (applied) mediaLibraryTab = null
            },
            onOpenAndroidPhotoPicker = { tab ->
                val mediaType = when (tab) {
                    MediaLibraryTab.GIF -> ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
                    MediaLibraryTab.WEBP -> ActivityResultContracts.PickVisualMedia.SingleMimeType("image/webp")
                    MediaLibraryTab.VIDEO -> ActivityResultContracts.PickVisualMedia.VideoOnly
                    else -> ActivityResultContracts.PickVisualMedia.ImageOnly
                }
                androidPhotoPicker.launch(PickVisualMediaRequest(mediaType))
            },
            onDirectBrowse = { tab ->
                when (tab) {
                    MediaLibraryTab.MUSIC -> directAudioPicker.launch(arrayOf("audio/*"))
                    MediaLibraryTab.GIF -> directVisualPicker.launch(arrayOf("image/gif"))
                    MediaLibraryTab.WEBP -> directVisualPicker.launch(arrayOf("image/webp"))
                    MediaLibraryTab.VIDEO -> directVisualPicker.launch(arrayOf("video/*", "*/*"))
                    MediaLibraryTab.IMAGE -> directVisualPicker.launch(arrayOf("image/*"))
                }
            },
            onBack = { mediaLibraryTab = null },
        )
        return
    }

    if (editing != null && permissions.allGranted) {
        AlarmEditorScreen(
            alarm = editing!!,
            permissions = permissions,
            message = message,
            onAlarmChange = { editing = it },
            onPickVisual = { mediaLibraryTab = MediaLibraryTab.IMAGE },
            onPickAudio = { mediaLibraryTab = MediaLibraryTab.MUSIC },
            onBack = {
                editing = null
                message = null
            },
            onSave = { enable, testDelayMillis ->
                val result = saveAndMaybeSchedule(
                    alarm = editing!!,
                    enable = enable,
                    testDelayMillis = testDelayMillis,
                    permissions = permissions,
                    repository = repository,
                    scheduler = scheduler,
                )
                message = result.message
                if (result.saved != null) editing = null
            },
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wideLayout = maxWidth >= 700.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!wideLayout) {
                    DashboardBottomNavigation(
                        destination = destination,
                        onDestinationChange = { destination = it },
                    )
                }
            },
        ) { contentPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                if (wideLayout) {
                    DashboardRail(
                        destination = destination,
                        onDestinationChange = { destination = it },
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 14.dp, top = 18.dp, bottom = 18.dp),
                    )
                }
                val contentModifier = if (wideLayout) {
                    Modifier.weight(1f)
                } else {
                    Modifier.fillMaxSize()
                }
                when (destination) {
                    AppDestination.ALARMS -> AlarmListScreen(
                        modifier = contentModifier,
                        alarms = alarms,
                        permissions = permissions,
                        message = message,
                        darkTheme = darkTheme,
                        onThemeChange = onThemeChange,
                        onOpenExactAlarmAccess = onOpenExactAlarmAccess,
                        onOpenWriteSettings = onOpenWriteSettings,
                        onOpenFullScreenAccess = onOpenFullScreenAccess,
                        onOpenOverlayAccess = onOpenOverlayAccess,
                        onRequestNotifications = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onAdd = {
                            if (permissions.allGranted) {
                                editing = AlarmScheduleResolver.newAlarm()
                                message = null
                            } else {
                                message = permissions.blockedEditingMessage()
                            }
                        },
                        onEdit = {
                            if (permissions.allGranted) {
                                editing = it
                                message = null
                            } else {
                                message = permissions.blockedEditingMessage()
                            }
                        },
                        onToggle = { alarm, enabled ->
                            if (enabled) {
                                message = saveAndMaybeSchedule(
                                    alarm = alarm,
                                    enable = true,
                                    testDelayMillis = null,
                                    permissions = permissions,
                                    repository = repository,
                                    scheduler = scheduler,
                                ).message
                            } else {
                                scheduler.cancel(alarm.id)
                                if (AlarmSessionStore(context).load()?.alarmId == alarm.id) {
                                    context.startService(AlarmPlaybackService.cancelIntent(context, alarm.id))
                                } else {
                                    repository.disable(alarm.id)
                                }
                                message = "알람을 껐습니다."
                            }
                        },
                        previewEnabled = { alarm -> alarm.homePreviewEnabled },
                        onPreviewToggle = { alarm, enabled ->
                            repository.setHomePreviewEnabled(alarm.id, enabled)
                        },
                        onDelete = { alarm ->
                            if (AlarmSessionStore(context).load()?.alarmId == alarm.id) {
                                message = "울리는 중이거나 스누즈 중인 알람은 먼저 해제해 주세요."
                            } else {
                                scheduler.cancel(alarm.id)
                                repository.delete(alarm.id)
                                message = "알람을 삭제했습니다."
                            }
                        },
                    )
                    AppDestination.STOPWATCH -> StopwatchScreen(
                        modifier = contentModifier,
                        isRunning = stopwatchRunning,
                        elapsedMillis = stopwatchElapsedMillis,
                        startedAtMillis = stopwatchStartedAtMillis,
                        laps = stopwatchLaps,
                        onStart = {
                            stopwatchStartedAtMillis = SystemClock.elapsedRealtime()
                            stopwatchRunning = true
                        },
                        onPause = { currentElapsedMillis ->
                            stopwatchElapsedMillis = currentElapsedMillis
                            stopwatchStartedAtMillis = 0L
                            stopwatchRunning = false
                        },
                        onLap = { currentElapsedMillis ->
                            val previousElapsedMillis = stopwatchLaps.firstOrNull()?.elapsedMillis ?: 0L
                            stopwatchLaps = listOf(
                                StopwatchLap(
                                    number = stopwatchLaps.size + 1,
                                    elapsedMillis = currentElapsedMillis,
                                    splitMillis = currentElapsedMillis - previousElapsedMillis,
                                ),
                            ) + stopwatchLaps
                        },
                        onReset = {
                            stopwatchRunning = false
                            stopwatchElapsedMillis = 0L
                            stopwatchStartedAtMillis = 0L
                            stopwatchLaps = emptyList()
                        },
                    )
                    AppDestination.TIMER -> CountdownTimerScreen(
                        modifier = contentModifier,
                        durationMillis = timerDurationMillis,
                        remainingMillis = timerRemainingMillis,
                        startedAtElapsedMillis = timerStartedAtMillis,
                        isRunning = timerRunning,
                        inputSeconds = timerInputSeconds,
                        onInputSecondsChange = { value ->
                            timerInputSeconds = value.filter(Char::isDigit).take(5)
                            val seconds = CountdownTimerPolicy.parseDurationSeconds(timerInputSeconds)
                            if (!timerRunning && seconds != null) {
                                timerDurationMillis = seconds * 1_000L
                                timerRemainingMillis = timerDurationMillis
                            }
                        },
                        onStart = {
                            val now = SystemClock.elapsedRealtime()
                            val current = CountdownTimerPolicy.remainingMillis(
                                baseRemainingMillis = timerRemainingMillis,
                                startedAtElapsedMillis = timerStartedAtMillis,
                                nowElapsedMillis = now,
                                running = timerRunning,
                            )
                            if (current > 0L) {
                                timerRemainingMillis = current
                                timerStartedAtMillis = now
                                timerRunning = true
                            }
                        },
                        onPause = {
                            val now = SystemClock.elapsedRealtime()
                            timerRemainingMillis = CountdownTimerPolicy.remainingMillis(
                                baseRemainingMillis = timerRemainingMillis,
                                startedAtElapsedMillis = timerStartedAtMillis,
                                nowElapsedMillis = now,
                                running = timerRunning,
                            )
                            timerStartedAtMillis = 0L
                            timerRunning = false
                        },
                        onReset = {
                            val seconds = CountdownTimerPolicy.parseDurationSeconds(timerInputSeconds)
                                ?: (timerDurationMillis / 1_000L)
                            timerDurationMillis = seconds * 1_000L
                            timerRemainingMillis = timerDurationMillis
                            timerStartedAtMillis = 0L
                            timerRunning = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StopwatchScreen(
    modifier: Modifier,
    isRunning: Boolean,
    elapsedMillis: Long,
    startedAtMillis: Long,
    laps: List<StopwatchLap>,
    onStart: () -> Unit,
    onPause: (Long) -> Unit,
    onLap: (Long) -> Unit,
    onReset: () -> Unit,
) {
    var tickMillis by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (true) {
                tickMillis = SystemClock.elapsedRealtime()
                delay(50)
            }
        } else {
            tickMillis = SystemClock.elapsedRealtime()
        }
    }

    val currentElapsedMillis = if (isRunning) {
        elapsedMillis + (tickMillis - startedAtMillis).coerceAtLeast(0L)
    } else {
        elapsedMillis
    }
    val canReset = isRunning || currentElapsedMillis > 0L || laps.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("스톱워치", style = MaterialTheme.typography.headlineLarge)
            Text(
                "시간을 측정하고 랩 타임을 기록하세요.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        formatStopwatchTime(currentElapsedMillis),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        if (isRunning) "측정 중" else if (currentElapsedMillis > 0L) "일시정지됨" else "시작할 준비가 됐어요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isRunning) onPause(currentElapsedMillis) else onStart()
                    },
                ) {
                    Text(if (isRunning) "일시정지" else if (currentElapsedMillis > 0L) "계속" else "시작")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = canReset,
                    onClick = onReset,
                ) {
                    Text("초기화")
                }
            }
        }
        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = isRunning,
                onClick = { onLap(currentElapsedMillis) },
            ) {
                Text("랩 기록")
            }
        }
        item {
            Text("랩 기록", style = MaterialTheme.typography.titleLarge)
        }
        if (laps.isEmpty()) {
            item {
                Card {
                    Text(
                        "스톱워치를 시작하면 랩 기록이 여기에 표시됩니다.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(laps, key = { it.number }) { lap ->
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("랩 ${lap.number}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "구간 ${formatStopwatchTime(lap.splitMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatStopwatchTime(lap.elapsedMillis),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun CountdownTimerScreen(
    modifier: Modifier,
    durationMillis: Long,
    remainingMillis: Long,
    startedAtElapsedMillis: Long,
    isRunning: Boolean,
    inputSeconds: String,
    onInputSecondsChange: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
) {
    var tickElapsedMillis by remember { mutableStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(isRunning, startedAtElapsedMillis) {
        if (isRunning) {
            while (true) {
                tickElapsedMillis = SystemClock.elapsedRealtime()
                val current = CountdownTimerPolicy.remainingMillis(
                    baseRemainingMillis = remainingMillis,
                    startedAtElapsedMillis = startedAtElapsedMillis,
                    nowElapsedMillis = tickElapsedMillis,
                    running = true,
                )
                if (current <= 0L) {
                    onPause()
                    break
                }
                delay(250L)
            }
        } else {
            tickElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    val currentRemainingMillis = CountdownTimerPolicy.remainingMillis(
        baseRemainingMillis = remainingMillis,
        startedAtElapsedMillis = startedAtElapsedMillis,
        nowElapsedMillis = tickElapsedMillis,
        running = isRunning,
    )
    val parsedSeconds = CountdownTimerPolicy.parseDurationSeconds(inputSeconds)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("타이머", style = MaterialTheme.typography.headlineLarge)
            Text(
                "입력한 시간만큼 카운트다운합니다. 화면을 이동해도 진행 상태를 유지합니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        CountdownTimerPolicy.format(currentRemainingMillis),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        when {
                            isRunning -> "진행 중"
                            currentRemainingMillis == 0L -> "완료"
                            currentRemainingMillis < durationMillis -> "일시정지됨"
                            else -> "시작할 준비가 됐어요"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = inputSeconds,
                onValueChange = onInputSecondsChange,
                enabled = !isRunning,
                label = { Text("시간(초)") },
                supportingText = { Text("${CountdownTimerPolicy.MIN_DURATION_SECONDS}초~${CountdownTimerPolicy.MAX_DURATION_SECONDS / 3_600}시간") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = inputSeconds.isNotBlank() && parsedSeconds == null,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = isRunning || (parsedSeconds != null && currentRemainingMillis > 0L),
                    onClick = { if (isRunning) onPause() else onStart() },
                ) {
                    Text(if (isRunning) "일시정지" else if (currentRemainingMillis < durationMillis) "계속" else "시작")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onReset,
                ) {
                    Text("초기화")
                }
            }
        }
    }
}

@Composable
private fun AlarmListScreen(
    modifier: Modifier,
    alarms: List<AlarmSpec>,
    permissions: PermissionState,
    message: String?,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onOpenExactAlarmAccess: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenFullScreenAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (AlarmSpec) -> Unit,
    onToggle: (AlarmSpec, Boolean) -> Unit,
    previewEnabled: (AlarmSpec) -> Boolean,
    onPreviewToggle: (AlarmSpec, Boolean) -> Unit,
    onDelete: (AlarmSpec) -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<AlarmSpec?>(null) }
    var activeYoutubePreviewAlarmId by remember { mutableStateOf<Int?>(null) }
    val orderedAlarms = remember(alarms) {
        AlarmListPolicy.sortForDisplay(alarms)
    }
    val featured = orderedAlarms.firstOrNull()
    val remaining = orderedAlarms.filterNot { it.id == featured?.id }

    LaunchedEffect(orderedAlarms) {
        if (activeYoutubePreviewAlarmId != null &&
            orderedAlarms.none { it.id == activeYoutubePreviewAlarmId }
        ) {
            activeYoutubePreviewAlarmId = null
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wideLayout = maxWidth >= 700.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = if (wideLayout) 28.dp else 18.dp,
                top = 22.dp,
                end = if (wideLayout) 28.dp else 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "알람",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            "활성 ${alarms.count { it.enabled }}개 · 밝기와 음량은 시작할 때 한 번만 적용",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onAdd) {
                        Text("+", style = MaterialTheme.typography.headlineLarge)
                    }
                    IconButton(onClick = { onThemeChange(!darkTheme) }) {
                        Text(if (darkTheme) "☼" else "☾", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            if (!permissions.allGranted) {
                item {
                    PermissionCard(
                        permissions = permissions,
                        onOpenExactAlarmAccess = onOpenExactAlarmAccess,
                        onOpenWriteSettings = onOpenWriteSettings,
                        onOpenFullScreenAccess = onOpenFullScreenAccess,
                        onOpenOverlayAccess = onOpenOverlayAccess,
                        onRequestNotifications = onRequestNotifications,
                    )
                }
            }
            message?.let { notice ->
                item {
                    Text(
                        notice,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (featured != null) {
                item {
                    DashboardFeaturedAlarmCard(
                        alarm = featured,
                        editingEnabled = permissions.allGranted,
                        previewEnabled = previewEnabled(featured),
                        youtubePreviewPlaying = activeYoutubePreviewAlarmId == featured.id,
                        onEdit = { onEdit(featured) },
                        onToggle = { onToggle(featured, it) },
                        onPreviewToggle = {
                            if (!it && activeYoutubePreviewAlarmId == featured.id) {
                                activeYoutubePreviewAlarmId = null
                            }
                            onPreviewToggle(featured, it)
                        },
                        onYoutubePreviewPlay = { activeYoutubePreviewAlarmId = featured.id },
                        onYoutubePreviewStop = {
                            if (activeYoutubePreviewAlarmId == featured.id) {
                                activeYoutubePreviewAlarmId = null
                            }
                        },
                        onDelete = { deleteCandidate = featured },
                    )
                }
            }
            if (remaining.isEmpty() && featured == null) {
                item {
                    EmptyAlarmCard(onAdd = onAdd, enabled = permissions.allGranted)
                }
            } else if (wideLayout) {
                remaining.chunked(2).forEachIndexed { index, pair ->
                    item(key = "alarm-row-$index") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            pair.forEach { alarm ->
                                DashboardAlarmCard(
                                    modifier = Modifier.weight(1f),
                                    alarm = alarm,
                                    editingEnabled = permissions.allGranted,
                                    previewEnabled = previewEnabled(alarm),
                                    youtubePreviewPlaying = activeYoutubePreviewAlarmId == alarm.id,
                                    onEdit = { onEdit(alarm) },
                                    onToggle = { onToggle(alarm, it) },
                                    onPreviewToggle = {
                                        if (!it && activeYoutubePreviewAlarmId == alarm.id) {
                                            activeYoutubePreviewAlarmId = null
                                        }
                                        onPreviewToggle(alarm, it)
                                    },
                                    onYoutubePreviewPlay = { activeYoutubePreviewAlarmId = alarm.id },
                                    onYoutubePreviewStop = {
                                        if (activeYoutubePreviewAlarmId == alarm.id) {
                                            activeYoutubePreviewAlarmId = null
                                        }
                                    },
                                    onDelete = { deleteCandidate = alarm },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(remaining, key = { it.id }) { alarm ->
                    DashboardAlarmCard(
                        modifier = Modifier.fillMaxWidth(),
                        alarm = alarm,
                        editingEnabled = permissions.allGranted,
                        previewEnabled = previewEnabled(alarm),
                        youtubePreviewPlaying = activeYoutubePreviewAlarmId == alarm.id,
                        onEdit = { onEdit(alarm) },
                        onToggle = { onToggle(alarm, it) },
                        onPreviewToggle = {
                            if (!it && activeYoutubePreviewAlarmId == alarm.id) {
                                activeYoutubePreviewAlarmId = null
                            }
                            onPreviewToggle(alarm, it)
                        },
                        onYoutubePreviewPlay = { activeYoutubePreviewAlarmId = alarm.id },
                        onYoutubePreviewStop = {
                            if (activeYoutubePreviewAlarmId == alarm.id) {
                                activeYoutubePreviewAlarmId = null
                            }
                        },
                        onDelete = { deleteCandidate = alarm },
                    )
                }
            }
        }
    }
    deleteCandidate?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("알람을 삭제할까요?") },
            text = { Text("‘${alarm.label}’ 설정은 복구할 수 없지만 원본 파일은 유지됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(alarm)
                        deleteCandidate = null
                    },
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun DashboardFeaturedAlarmCard(
    alarm: AlarmSpec,
    editingEnabled: Boolean,
    previewEnabled: Boolean,
    youtubePreviewPlaying: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPreviewToggle: (Boolean) -> Unit,
    onYoutubePreviewPlay: () -> Unit,
    onYoutubePreviewStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
            if (previewEnabled && alarm.hasHomePreviewContent()) {
                AlarmHomePreview(
                    alarm = alarm,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(30.dp)),
                    youtubePreviewPlaying = youtubePreviewPlaying,
                    onYoutubePreviewPlay = onYoutubePreviewPlay,
                    onYoutubePreviewStop = onYoutubePreviewStop,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(
                        if (previewEnabled) "미리볼 콘텐츠 없음" else "미리보기 꺼짐",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alarm.label.ifBlank { "알람" },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    TextButton(onClick = onEdit, enabled = editingEnabled) {
                        Text("편집", color = Color.White.copy(alpha = if (editingEnabled) 1f else 0.5f))
                    }
                    TextButton(onClick = onDelete) { Text("삭제", color = Color.White) }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatAlarmTime(alarm.localTimeMinutes),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            scheduleLabel(alarm),
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("알람", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = alarm.enabled,
                                onCheckedChange = onToggle,
                                enabled = editingEnabled || alarm.enabled,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("미리보기", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = previewEnabled,
                                onCheckedChange = onPreviewToggle,
                                enabled = editingEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardAlarmCard(
    modifier: Modifier,
    alarm: AlarmSpec,
    editingEnabled: Boolean,
    previewEnabled: Boolean,
    youtubePreviewPlaying: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPreviewToggle: (Boolean) -> Unit,
    onYoutubePreviewPlay: () -> Unit,
    onYoutubePreviewStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            if (previewEnabled && alarm.hasHomePreviewContent()) {
                AlarmHomePreview(
                    alarm = alarm,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp)),
                    playLocalVideo = true,
                    youtubePreviewPlaying = youtubePreviewPlaying,
                    onYoutubePreviewPlay = onYoutubePreviewPlay,
                    onYoutubePreviewStop = onYoutubePreviewStop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Text(
                        if (previewEnabled) "미리볼 콘텐츠 없음" else "미리보기 꺼짐",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alarm.label.ifBlank { "알람" },
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    TextButton(onClick = onEdit, enabled = editingEnabled) {
                        Text("편집", color = Color.White.copy(alpha = if (editingEnabled) 1f else 0.5f))
                    }
                    TextButton(onClick = onDelete) { Text("삭제", color = Color.White) }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            formatAlarmTime(alarm.localTimeMinutes),
                            color = Color.White,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                        Text(
                            scheduleLabel(alarm),
                            color = Color.White.copy(alpha = 0.82f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("알람", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = alarm.enabled,
                                onCheckedChange = onToggle,
                                enabled = editingEnabled || alarm.enabled,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("미리보기", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = previewEnabled,
                                onCheckedChange = onPreviewToggle,
                                enabled = editingEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun AlarmSpec.hasHomePreviewContent(): Boolean = when (contentMode) {
    ContentMode.LOCAL -> visualUri != null && visualKind != VisualKind.NONE
    ContentMode.YOUTUBE -> YouTubeEmbed.isSupportedUrl(youtubeUrl.orEmpty())
}

@Composable
private fun AlarmHomePreview(
    alarm: AlarmSpec,
    modifier: Modifier,
    playLocalVideo: Boolean = true,
    youtubePreviewPlaying: Boolean,
    onYoutubePreviewPlay: () -> Unit,
    onYoutubePreviewStop: () -> Unit,
) {
    when (alarm.contentMode) {
        ContentMode.LOCAL -> AlarmVisual(
            visualUri = alarm.visualUri,
            visualKind = alarm.visualKind,
            modifier = modifier,
            cropToFill = true,
            playVideo = playLocalVideo,
        )

        ContentMode.YOUTUBE -> YouTubeThumbnailOnly(
            value = alarm.youtubeUrl.orEmpty(),
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyAlarmCard(onAdd: () -> Unit, enabled: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("아직 저장한 알람이 없습니다.", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onAdd, enabled = enabled) { Text("첫 알람 추가") }
        }
    }
}

@Composable
private fun DashboardRail(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(84.dp).fillMaxHeight(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("☰", style = MaterialTheme.typography.headlineSmall)
            DashboardRailItem("⏰", "알람", destination == AppDestination.ALARMS) {
                onDestinationChange(AppDestination.ALARMS)
            }
            DashboardRailItem("⏱", "스톱워치", destination == AppDestination.STOPWATCH) {
                onDestinationChange(AppDestination.STOPWATCH)
            }
            DashboardRailItem("⌛", "타이머", destination == AppDestination.TIMER) {
                onDestinationChange(AppDestination.TIMER)
            }
        }
    }
}

@Composable
private fun DashboardRailItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Text(label.take(3), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DashboardBottomNavigation(
    destination: AppDestination,
    onDestinationChange: (AppDestination) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DashboardNavItem(
                modifier = Modifier.weight(1f),
                icon = "⏰",
                label = "알람",
                selected = destination == AppDestination.ALARMS,
            ) {
                onDestinationChange(AppDestination.ALARMS)
            }
            DashboardNavItem(
                modifier = Modifier.weight(1f),
                icon = "⏱",
                label = "스톱워치",
                selected = destination == AppDestination.STOPWATCH,
            ) {
                onDestinationChange(AppDestination.STOPWATCH)
            }
            DashboardNavItem(
                modifier = Modifier.weight(1f),
                icon = "⌛",
                label = "타이머",
                selected = destination == AppDestination.TIMER,
            ) {
                onDestinationChange(AppDestination.TIMER)
            }
        }
    }
}

@Composable
private fun DashboardNavItem(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun PermissionSetupScreen(
    permissions: PermissionState,
    onOpenExactAlarmAccess: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenFullScreenAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
    onContinueReadOnly: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("처음 실행 설정", style = MaterialTheme.typography.headlineLarge)
        Text(
            "정확한 시각에 잠금화면 위로 알람을 띄우려면 아래 접근을 한 번씩 허용해 주세요.",
            style = MaterialTheme.typography.bodyLarge,
        )
        PermissionCard(
            permissions = permissions,
            onOpenExactAlarmAccess = onOpenExactAlarmAccess,
            onOpenWriteSettings = onOpenWriteSettings,
            onOpenFullScreenAccess = onOpenFullScreenAccess,
            onOpenOverlayAccess = onOpenOverlayAccess,
            onRequestNotifications = onRequestNotifications,
        )
        Text(
            "모든 접근을 완료하기 전에는 알람 추가·편집·테스트를 사용할 수 없습니다.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onContinueReadOnly,
        ) {
            Text("권한 설정 없이 기존 목록만 보기")
        }
    }
}

@Composable
private fun AlarmCard(
    alarm: AlarmSpec,
    editingEnabled: Boolean,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(alarm.label, style = MaterialTheme.typography.titleLarge)
                    Text(formatTime(alarm.localTimeMinutes), style = MaterialTheme.typography.headlineMedium)
                    Text(scheduleSummary(alarm), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = editingEnabled,
                    onClick = onEdit,
                ) { Text(if (editingEnabled) "편집" else "권한 필요") }
                TextButton(modifier = Modifier.weight(1f), onClick = onDelete) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun AlarmEditorScreen(
    alarm: AlarmSpec,
    permissions: PermissionState,
    message: String?,
    onAlarmChange: (AlarmSpec) -> Unit,
    onPickVisual: () -> Unit,
    onPickAudio: () -> Unit,
    onBack: () -> Unit,
    onSave: (enable: Boolean, testDelayMillis: Long?) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var hourText by remember(alarm.id) {
        mutableStateOf((alarm.localTimeMinutes / 60).toString().padStart(2, '0'))
    }
    var minuteText by remember(alarm.id) {
        mutableStateOf((alarm.localTimeMinutes % 60).toString().padStart(2, '0'))
    }
    var testDelayText by remember(alarm.id) { mutableStateOf("10") }
    var scheduleNotice by remember(alarm.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(alarm.localTimeMinutes) {
        val typedMinutes = hourText.toIntOrNull()?.let { hour ->
            minuteText.toIntOrNull()?.let { minute -> hour * 60 + minute }
        }
        if (typedMinutes != alarm.localTimeMinutes) {
            hourText = (alarm.localTimeMinutes / 60).toString().padStart(2, '0')
            minuteText = (alarm.localTimeMinutes % 60).toString().padStart(2, '0')
        }
    }
    val typedHour = hourText.toIntOrNull()
    val typedMinute = minuteText.toIntOrNull()
    val manualTimeValid = typedHour != null && typedHour in 0..23 &&
        typedMinute != null && typedMinute in 0..59
    val testDelaySeconds = testDelayText.toLongOrNull()?.takeIf { it in 1L..3_600L }
    fun normalizeOneTime(candidate: AlarmSpec): AlarmSpec {
        val normalized = AlarmScheduleResolver.rollPastOneTimeToTomorrow(candidate)
        if (
            candidate.repeatType == RepeatType.ONE_TIME &&
            candidate.oneTimeDateEpochDay != normalized.oneTimeDateEpochDay
        ) {
            scheduleNotice = "오늘 이미 지난 시각이라 내일로 자동 변경했습니다."
        }
        return normalized
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text(
                if (alarm.id == 0) "새 알람" else "알람 편집",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        HorizontalDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = alarm.label,
                onValueChange = { onAlarmChange(alarm.copy(label = it.take(40))) },
                label = { Text("알람 이름") },
                singleLine = true,
            )
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeading(
                        title = "언제 울릴까요?",
                        description = "숫자로 바로 입력하거나 시간 선택 화면을 사용할 수 있습니다.",
                    )
                    Text(formatTime(alarm.localTimeMinutes), style = MaterialTheme.typography.headlineLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = hourText,
                            onValueChange = { value ->
                                val next = value.filter(Char::isDigit).take(2)
                                hourText = next
                                val hour = next.toIntOrNull()
                                val minute = minuteText.toIntOrNull()
                                if (hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                                    onAlarmChange(
                                        normalizeOneTime(alarm.copy(localTimeMinutes = hour * 60 + minute)),
                                    )
                                }
                            },
                            label = { Text("시") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = minuteText,
                            onValueChange = { value ->
                                val next = value.filter(Char::isDigit).take(2)
                                minuteText = next
                                val hour = hourText.toIntOrNull()
                                val minute = next.toIntOrNull()
                                if (hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                                    onAlarmChange(
                                        normalizeOneTime(alarm.copy(localTimeMinutes = hour * 60 + minute)),
                                    )
                                }
                            },
                            label = { Text("분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                    }
                    if (!manualTimeValid) {
                        Text("시각은 00:00~23:59 범위로 입력해 주세요.", color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    hourText = hour.toString().padStart(2, '0')
                                    minuteText = minute.toString().padStart(2, '0')
                                    onAlarmChange(
                                        normalizeOneTime(alarm.copy(localTimeMinutes = hour * 60 + minute)),
                                    )
                                },
                                alarm.localTimeMinutes / 60,
                                alarm.localTimeMinutes % 60,
                                true,
                            ).show()
                        },
                    ) { Text("시계 화면에서 선택") }
                    HorizontalDivider()
                    Text("반복 방식", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = alarm.repeatType == RepeatType.ONE_TIME,
                            onClick = {
                                val candidate = alarm.copy(
                                    repeatType = RepeatType.ONE_TIME,
                                    oneTimeDateEpochDay = alarm.oneTimeDateEpochDay
                                        ?: LocalDate.now().toEpochDay(),
                                )
                                onAlarmChange(
                                    normalizeOneTime(candidate),
                                )
                            },
                            label = { Text("한 번") },
                        )
                        FilterChip(
                            selected = alarm.repeatType == RepeatType.WEEKLY,
                            onClick = {
                                onAlarmChange(
                                    alarm.copy(
                                        repeatType = RepeatType.WEEKLY,
                                        weekdays = alarm.weekdays.ifEmpty {
                                            setOf(LocalDate.now().dayOfWeek.value)
                                        },
                                    ),
                                )
                            },
                            label = { Text("매주") },
                        )
                    }
                    if (alarm.repeatType == RepeatType.ONE_TIME) {
                        Text("울릴 날짜", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showDatePicker(context, alarm.oneTimeDateEpochDay) { selected ->
                                    onAlarmChange(
                                        normalizeOneTime(alarm.copy(oneTimeDateEpochDay = selected)),
                                    )
                                }
                            },
                        ) {
                            Text(
                                alarm.oneTimeDateEpochDay?.let(::formatDate) ?: "날짜 선택",
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onAlarmChange(
                                        normalizeOneTime(
                                            alarm.copy(oneTimeDateEpochDay = LocalDate.now().toEpochDay()),
                                        ),
                                    )
                                },
                            ) { Text("오늘") }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scheduleNotice = null
                                    onAlarmChange(
                                        alarm.copy(
                                            oneTimeDateEpochDay = LocalDate.now().plusDays(1).toEpochDay(),
                                        ),
                                    )
                                },
                            ) { Text("내일") }
                        }
                        scheduleNotice?.let {
                            Text(it, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        WeekdayPicker(alarm.weekdays) { onAlarmChange(alarm.copy(weekdays = it)) }
                        ExceptionDates(
                            title = "추가 날짜",
                            dates = alarm.includeDatesEpochDay,
                            onAdd = {
                                showDatePicker(context, null) { selected ->
                                    onAlarmChange(
                                        alarm.copy(
                                            includeDatesEpochDay = alarm.includeDatesEpochDay + selected,
                                            excludeDatesEpochDay = alarm.excludeDatesEpochDay - selected,
                                        ),
                                    )
                                }
                            },
                            onRemove = {
                                onAlarmChange(alarm.copy(includeDatesEpochDay = alarm.includeDatesEpochDay - it))
                            },
                        )
                        ExceptionDates(
                            title = "제외 날짜",
                            dates = alarm.excludeDatesEpochDay,
                            onAdd = {
                                showDatePicker(context, null) { selected ->
                                    onAlarmChange(
                                        alarm.copy(
                                            excludeDatesEpochDay = alarm.excludeDatesEpochDay + selected,
                                            includeDatesEpochDay = alarm.includeDatesEpochDay - selected,
                                        ),
                                    )
                                }
                            },
                            onRemove = {
                                onAlarmChange(alarm.copy(excludeDatesEpochDay = alarm.excludeDatesEpochDay - it))
                            },
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            text = AlarmScheduleResolver.nextTriggerAtMillis(alarm)
                                ?.let { "다음 실행  ${formatTrigger(it)}" }
                                ?: "다음 실행 시각을 계산할 수 없습니다.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeading(
                        title = "알람이 시작될 때",
                        description = "시작 순간에만 적용되며 울리는 중에도 직접 바꿀 수 있습니다.",
                    )
                    ValueSlider("화면 밝기", alarm.brightnessPercent, 0f..100f, "%") {
                        onAlarmChange(alarm.copy(brightnessPercent = it))
                    }
                    ValueSlider("미디어 음량", alarm.mediaVolumePercent, 0f..100f, "%") {
                        onAlarmChange(alarm.copy(mediaVolumePercent = it))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("화면 잠금 타이머")
                            Text(
                                if (alarm.dismissTimerEnabled) {
                                    "켜짐 · 0~60초 뒤 해제 버튼 활성화"
                                } else {
                                    "꺼짐 · 알람 시작 즉시 해제 가능"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = alarm.dismissTimerEnabled,
                            onCheckedChange = { enabled ->
                                onAlarmChange(
                                    alarm.copy(
                                        dismissTimerEnabled = enabled,
                                        dismissDelaySeconds = if (enabled) {
                                            alarm.dismissDelaySeconds.coerceIn(0, 60)
                                        } else {
                                            0
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    if (alarm.dismissTimerEnabled) {
                        ValueSlider("닫기 버튼 대기 시간", alarm.dismissDelaySeconds.coerceIn(0, 60), 0f..60f, "초") {
                            onAlarmChange(alarm.copy(dismissDelaySeconds = it.coerceIn(0, 60)))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("해제 시 원래 값 복원")
                            Text("끄면 사용자가 바꾼 현재 값을 유지합니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = alarm.restorePolicy == RestorePolicy.RESTORE_PREVIOUS,
                            onCheckedChange = { restore ->
                                onAlarmChange(
                                    alarm.copy(
                                        restorePolicy = if (restore) {
                                            RestorePolicy.RESTORE_PREVIOUS
                                        } else {
                                            RestorePolicy.KEEP_CURRENT
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionHeading(
                        title = "무엇을 재생할까요?",
                        description = "선택한 파일을 바로 확인할 수 있고, 소리는 파일 구성에 맞춰 자동으로 정해집니다.",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = alarm.contentMode == ContentMode.LOCAL,
                            onClick = { onAlarmChange(alarm.copy(contentMode = ContentMode.LOCAL)) },
                            label = { Text("내 기기 파일") },
                        )
                        FilterChip(
                            selected = alarm.contentMode == ContentMode.YOUTUBE,
                            onClick = { onAlarmChange(alarm.copy(contentMode = ContentMode.YOUTUBE)) },
                            label = { Text("YouTube") },
                        )
                    }
                    if (alarm.contentMode == ContentMode.LOCAL) {
                        if (alarm.visualUri == null) {
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPickVisual) {
                                Text("이미지·GIF·영상 선택")
                            }
                        } else {
                            SelectedVisualFile(
                                uri = alarm.visualUri,
                                kind = alarm.visualKind,
                                onChange = onPickVisual,
                                onRemove = {
                                    onAlarmChange(
                                        alarm.copy(
                                            visualUri = null,
                                            visualKind = VisualKind.NONE,
                                            soundSource = automaticSoundSource(VisualKind.NONE, null, alarm.audioUri),
                                        ),
                                    )
                                },
                            )
                        }
                        Text("소리", style = MaterialTheme.typography.titleMedium)
                        if (alarm.audioUri == null) {
                            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onPickAudio) {
                                Text("별도 로컬 음악 추가")
                            }
                        } else {
                            SelectedAudioFile(
                                uri = alarm.audioUri,
                                onChange = onPickAudio,
                                onRemove = {
                                    onAlarmChange(
                                        alarm.copy(
                                            audioUri = null,
                                            soundSource = automaticSoundSource(alarm.visualKind, alarm.visualUri, null),
                                        ),
                                    )
                                },
                            )
                        }
                        Text(
                            automaticSoundDescription(alarm),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = alarm.youtubeUrl.orEmpty(),
                            onValueChange = { onAlarmChange(alarm.copy(youtubeUrl = it)) },
                            label = { Text("YouTube URL") },
                            singleLine = true,
                        )
                        YouTubePreview(
                            value = alarm.youtubeUrl.orEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("공식 YouTube 임베디드 플레이어로 알람 화면 안에서 재생합니다.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeading(
                        title = "먼저 시험해 보기",
                        description = "원하는 대기 시간을 고르거나 초 단위로 직접 입력하세요.",
                    )
                    listOf(listOf(5L, 10L), listOf(30L, 60L)).forEach { choices ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            choices.forEach { seconds ->
                                FilterChip(
                                    modifier = Modifier.weight(1f),
                                    selected = testDelaySeconds == seconds,
                                    onClick = { testDelayText = seconds.toString() },
                                    label = { Text("${seconds}초") },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = testDelayText,
                        onValueChange = { testDelayText = it.filter(Char::isDigit).take(4) },
                        label = { Text("직접 입력 (초)") },
                        supportingText = { Text("1초~3600초") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = permissions.allGranted && manualTimeValid && testDelaySeconds != null,
                        onClick = { onSave(true, checkNotNull(testDelaySeconds) * 1_000L) },
                    ) {
                        Text(testDelaySeconds?.let { "${it}초 뒤 전체화면 테스트" } ?: "시간을 확인해 주세요")
                    }
                    if (!permissions.allGranted) {
                        Text("테스트하려면 홈 화면의 필수 접근을 모두 허용해 주세요.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SectionHeading(
                        title = "설정 마치기",
                        description = "저장한 뒤 알람을 바로 켜거나, 꺼진 상태로 보관할 수 있습니다.",
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualTimeValid,
                        onClick = { onSave(true, null) },
                    ) {
                        Text(if (alarm.enabled) "변경 내용 저장" else "저장하고 알람 켜기")
                    }
                    if (!alarm.enabled) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualTimeValid,
                            onClick = { onSave(false, null) },
                        ) {
                            Text("저장만 하기")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SelectedVisualFile(
    uri: String,
    kind: VisualKind,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val name = remember(uri) { selectedFileName(context, uri) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("선택한 화면 파일", style = MaterialTheme.typography.labelLarge)
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
            AlarmVisual(
                visualUri = uri,
                visualKind = kind,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                cropToFill = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onChange) { Text("파일 변경") }
                TextButton(modifier = Modifier.weight(1f), onClick = onRemove) { Text("제거") }
            }
        }
    }
}

@Composable
private fun SelectedAudioFile(
    uri: String,
    onChange: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val name = remember(uri) { selectedFileName(context, uri) }
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var previewError by remember(uri) { mutableStateOf(false) }

    fun stopPreview() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        isPlaying = false
    }

    DisposableEffect(uri) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("선택한 음악 파일", style = MaterialTheme.typography.labelLarge)
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isPlaying) {
                            stopPreview()
                        } else {
                            previewError = false
                            val created = runCatching {
                                MediaPlayer.create(context, Uri.parse(uri))
                            }.getOrNull()
                            if (created == null) {
                                previewError = true
                            } else {
                                player = created
                                created.setOnCompletionListener {
                                    it.release()
                                    player = null
                                    isPlaying = false
                                }
                                created.start()
                                isPlaying = true
                            }
                        }
                    },
                ) { Text(if (isPlaying) "미리듣기 정지" else "음악 미리듣기") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        stopPreview()
                        onChange()
                    },
                ) { Text("파일 변경") }
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    stopPreview()
                    onRemove()
                },
            ) { Text("음악 파일 제거") }
            if (previewError) {
                Text("이 파일은 미리 재생할 수 없습니다.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun automaticSoundSource(
    visualKind: VisualKind,
    visualUri: String?,
    audioUri: String?,
): SoundSource =
    AlarmSoundPolicy.resolve(
        visualKind = visualKind,
        visualUri = visualUri,
        audioUri = audioUri,
    )

private fun automaticSoundDescription(alarm: AlarmSpec): String = when (
    AlarmSoundPolicy.resolve(alarm.visualKind, alarm.visualUri, alarm.audioUri)
) {
    SoundSource.LOCAL_AUDIO -> "알람이 울리면 선택한 음악 파일을 재생합니다."
    SoundSource.VISUAL_MEDIA ->
        "별도 음악이 없으므로 영상 소리를 재생합니다. 영상에 소리가 없거나 재생할 수 없으면 기기 기본 알람음을 사용합니다."
    SoundSource.DEFAULT_ALARM -> "별도 음악이나 영상 소리가 없으므로 기기 기본 알람음을 사용합니다."
}

private fun selectedFileName(context: Context, value: String): String {
    val uri = Uri.parse(value)
    val displayName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()
    return displayName?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "선택한 파일"
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("반복 요일")
        listOf((1..4).toList(), (5..7).toList()).forEach { rowDays ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowDays.forEach { day ->
                    FilterChip(
                        selected = day in selected,
                        onClick = {
                            onChange(if (day in selected) selected - day else selected + day)
                        },
                        label = { Text(shortDay(day)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExceptionDates(
    title: String,
    dates: Set<Long>,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("추가") }
        }
        dates.sorted().forEach { date ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDate(date), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onRemove(date) }) { Text("제거") }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissions: PermissionState,
    onOpenExactAlarmAccess: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenFullScreenAccess: () -> Unit,
    onOpenOverlayAccess: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (permissions.allGranted) "필수 접근 준비됨" else "필수 접근 확인", style = MaterialTheme.typography.titleLarge)
            if (!permissions.allGranted) {
                Text(
                    "아래 접근을 모두 허용해야 알람 추가·편집·테스트를 사용할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            PermissionRow("정확한 알람", permissions.exactAlarm, onOpenExactAlarmAccess)
            PermissionRow("시스템 밝기 변경", permissions.writeSettings, onOpenWriteSettings)
            PermissionRow("전체화면 알람", permissions.fullScreenIntent, onOpenFullScreenAccess)
            PermissionRow("다른 앱 위 전체화면 표시", permissions.drawOverlays, onOpenOverlayAccess)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow("알림 표시", permissions.notifications, onRequestNotifications)
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("$label · ${if (granted) "허용됨" else "확인 필요"}")
        if (!granted) OutlinedButton(onClick = onClick) { Text("설정") }
    }
}

@Composable
private fun SectionHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ValueSlider(
    label: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    suffix: String = "",
    onValueChange: (Int) -> Unit,
) {
    Column {
        Text("$label · $value$suffix")
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range,
            steps = (range.endInclusive - range.start - 1).roundToInt().coerceAtLeast(0),
        )
    }
}

private data class SaveResult(val saved: AlarmSpec?, val message: String)

private fun saveAndMaybeSchedule(
    alarm: AlarmSpec,
    enable: Boolean,
    testDelayMillis: Long?,
    permissions: PermissionState,
    repository: AlarmRepository,
    scheduler: AlarmScheduler,
): SaveResult {
    val shouldEnable = enable || alarm.enabled
    if (shouldEnable && !permissions.exactAlarm) return SaveResult(null, "정확한 알람 접근을 허용해 주세요.")
    if (shouldEnable && !permissions.writeSettings) return SaveResult(null, "시스템 밝기 변경을 허용해 주세요.")
    if (shouldEnable && !permissions.fullScreenIntent) return SaveResult(null, "전체화면 알람 접근을 허용해 주세요.")
    if (shouldEnable && !permissions.notifications) return SaveResult(null, "알림 표시를 허용해 주세요.")
    if (shouldEnable && !permissions.drawOverlays) return SaveResult(null, "다른 앱 위 전체화면 표시를 허용해 주세요.")
    if (alarm.repeatType == RepeatType.WEEKLY && alarm.weekdays.isEmpty()) {
        return SaveResult(null, "반복할 요일을 하나 이상 선택해 주세요.")
    }
    if (alarm.contentMode == ContentMode.YOUTUBE && !YouTubeEmbed.isSupportedUrl(alarm.youtubeUrl.orEmpty())) {
        return SaveResult(null, "올바른 YouTube URL을 입력해 주세요.")
    }
    val automaticAlarm = alarm.copy(
        soundSource = automaticSoundSource(alarm.visualKind, alarm.visualUri, alarm.audioUri),
    )
    val schedulingAlarm = if (testDelayMillis == null) {
        AlarmScheduleResolver.rollPastOneTimeToTomorrow(automaticAlarm)
    } else {
        automaticAlarm
    }
    val rolledToTomorrow = testDelayMillis == null &&
        alarm.repeatType == RepeatType.ONE_TIME &&
        alarm.oneTimeDateEpochDay != schedulingAlarm.oneTimeDateEpochDay
    val triggerAt = testDelayMillis?.let { System.currentTimeMillis() + it }
        ?: AlarmScheduleResolver.nextTriggerAtMillis(schedulingAlarm)
        ?: return SaveResult(null, "미래의 알람 날짜와 시간을 선택해 주세요.")
    val normalized = schedulingAlarm.copy(
        label = schedulingAlarm.label.trim().ifBlank { "알람" },
        triggerAtMillis = triggerAt,
        enabled = shouldEnable,
        includeDatesEpochDay = if (schedulingAlarm.repeatType == RepeatType.WEEKLY) schedulingAlarm.includeDatesEpochDay else emptySet(),
        excludeDatesEpochDay = if (schedulingAlarm.repeatType == RepeatType.WEEKLY) schedulingAlarm.excludeDatesEpochDay else emptySet(),
    )
    var persisted: AlarmSpec? = null
    return runCatching {
        if (alarm.id > 0) scheduler.cancel(alarm.id)
        val saved = repository.save(normalized)
        persisted = saved
        if (saved.enabled) {
            scheduler.schedule(saved)
        }
        saved
    }.fold(
        onSuccess = {
            val successMessage = when {
                rolledToTomorrow -> "오늘 시각이 이미 지나 내일 알람으로 예약했습니다."
                it.enabled -> "알람을 예약했습니다."
                else -> "알람을 저장했습니다."
            }
            SaveResult(it, successMessage)
        },
        onFailure = { error ->
            persisted?.let { repository.disable(it.id) }
            SaveResult(null, "저장 실패: ${error.message ?: "알 수 없는 오류"}")
        },
    )
}

private fun showDatePicker(context: Context, initialEpochDay: Long?, onSelected: (Long) -> Unit) {
    val initial = initialEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day).toEpochDay()) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun formatAlarmTime(minutes: Int): String {
    val safeMinutes = minutes.coerceIn(0, 1439)
    val hour24 = safeMinutes / 60
    val minute = safeMinutes % 60
    val period = if (hour24 < 12) "오전" else "오후"
    val hour12 = when (val value = hour24 % 12) {
        0 -> 12
        else -> value
    }
    return "$period $hour12:%02d".format(Locale.KOREAN, minute)
}

private fun scheduleLabel(alarm: AlarmSpec): String = when (alarm.repeatType) {
    RepeatType.ONE_TIME -> alarm.oneTimeDateEpochDay?.let { formatDate(it) } ?: "한 번"
    RepeatType.WEEKLY -> {
        val days = alarm.weekdays.sorted()
        when {
            days.size == 7 -> "매일"
            days.isEmpty() -> "요일 미정"
            else -> days.joinToString(" ") { shortDay(it) }
        }
    }
}

private fun formatStopwatchTime(millis: Long): String {
    val safeMillis = millis.coerceAtLeast(0L)
    val hours = safeMillis / 3_600_000L
    val minutes = (safeMillis / 60_000L) % 60L
    val seconds = (safeMillis / 1_000L) % 60L
    val centiseconds = (safeMillis % 1_000L) / 10L
    return if (hours > 0L) {
        String.format(Locale.US, "%02d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
    } else {
        String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centiseconds)
    }
}

private fun formatDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN))

private fun shortDay(day: Int): String = DayOfWeek.of(day)
    .getDisplayName(TextStyle.SHORT, Locale.KOREAN)

private fun scheduleSummary(alarm: AlarmSpec): String = when (alarm.repeatType) {
    RepeatType.ONE_TIME -> alarm.oneTimeDateEpochDay?.let(::formatDate) ?: "날짜 미정"
    RepeatType.WEEKLY -> alarm.weekdays.sorted().joinToString(" ") { shortDay(it) }
}

private fun formatTrigger(millis: Long): String = Instant.ofEpochMilli(millis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN))
