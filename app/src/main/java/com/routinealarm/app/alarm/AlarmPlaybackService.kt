package com.routinealarm.app.alarm

import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.data.local.AlarmOccurrenceStatus
import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.model.AlarmSoundPolicy
import com.routinealarm.app.model.RepeatType
import com.routinealarm.app.model.SoundSource

class AlarmPlaybackService : Service() {
    private lateinit var repository: AlarmRepository
    private lateinit var deviceStateController: DeviceStateController
    private lateinit var audioManager: AudioManager
    private lateinit var sessionStore: AlarmSessionStore
    private lateinit var overlayController: AlarmOverlayController
    private var mediaPlayer: MediaPlayer? = null
    private var fallbackTone: ToneGenerator? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasActiveForegroundSession = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playbackGeneration = 0L
    private var activePlaybackSessionId = ""
    private var volumeRampGeneration = 0L
    private var volumeRampSessionId = ""
    private var volumeRampPlayer: MediaPlayer? = null
    private var volumeRampStartedAtElapsedRealtime = 0L
    private val restoreSurface = Runnable { ensureAlarmSurface() }
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (
                intent?.action == Intent.ACTION_SCREEN_ON ||
                intent?.action == Intent.ACTION_USER_PRESENT
            ) {
                scheduleSurfaceRecovery()
            }
        }
    }
    private val fallbackToneLoop = object : Runnable {
        override fun run() {
            fallbackTone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, FALLBACK_TONE_MILLIS)
            mainHandler.postDelayed(this, FALLBACK_REPEAT_MILLIS)
        }
    }
    private val volumeRamp = object : Runnable {
        override fun run() {
            val player = volumeRampPlayer ?: return
            val sessionId = volumeRampSessionId
            val generation = volumeRampGeneration
            if (
                mediaPlayer !== player ||
                activePlaybackSessionId != sessionId ||
                playbackGeneration != generation ||
                !hasActiveForegroundSession ||
                sessionId.isBlank()
            ) {
                return
            }

            val elapsedMillis = (
                SystemClock.elapsedRealtime() - volumeRampStartedAtElapsedRealtime
                ).coerceAtLeast(0L)
            val gain = PlaybackVolumeRampPolicy.gainAt(elapsedMillis)
            if (runCatching { player.setVolume(gain, gain) }.isFailure) {
                cancelVolumeRamp()
                return
            }
            if (gain < 1f) {
                val delayMillis = PlaybackVolumeRampPolicy.nextTickDelayMillis(elapsedMillis)
                if (delayMillis > 0L) mainHandler.postDelayed(this, delayMillis)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = AlarmRepository(this)
        deviceStateController = DeviceStateController(this)
        audioManager = getSystemService(AudioManager::class.java)
        sessionStore = AlarmSessionStore(this)
        overlayController = AlarmOverlayController(this)
        AlarmNotificationFactory(this).createChannel()
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> dismissAlarm(
                alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                requestedSessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty(),
                startId = startId,
            )
            ACTION_CANCEL -> cancelAlarm(
                alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                requestedSessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty(),
                startId = startId,
            )
            ACTION_SNOOZE -> snoozeAlarm(
                alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                requestedSessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty(),
                startId = startId,
            )
            ACTION_ENSURE_VISIBLE -> ensureActiveAlarmVisible(startId)
            ACTION_START -> startAlarm(
                requestedId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1),
                occurrenceKind = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_KIND)
                    ?.let { stored -> AlarmOccurrenceKind.entries.firstOrNull { it.name == stored } }
                    ?: AlarmOccurrenceKind.REGULAR,
                occurrenceId = intent.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID).orEmpty(),
                scheduleRevision = intent.getLongExtra(
                    AlarmScheduler.EXTRA_SCHEDULE_REVISION,
                    -1L,
                ),
                sessionId = intent.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID).orEmpty(),
                preemptSnooze = intent.getBooleanExtra(EXTRA_PREEMPT_SNOOZE, false),
                startId = startId,
            )
            else -> recoverActiveAlarm(startId)
        }
        return START_STICKY
    }

    private fun ensureActiveAlarmVisible(startId: Int) {
        if (!hasActiveForegroundSession) {
            recoverActiveAlarm(startId)
            return
        }
        scheduleSurfaceRecovery()
    }

    private fun scheduleSurfaceRecovery() {
        mainHandler.removeCallbacks(restoreSurface)
        mainHandler.postDelayed(restoreSurface, SURFACE_RECOVERY_DELAY_MILLIS)
    }

    private fun ensureAlarmSurface() {
        val session = sessionStore.load() ?: return
        if (session.state != AlarmSessionState.FIRING) return
        val alarm = repository.load(session.alarmId)
            ?.takeIf { it.enabled }
            ?: return
        when (
            AlarmSurfacePolicy.target(
                isInteractive = getSystemService(PowerManager::class.java).isInteractive,
                isKeyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
                canDrawOverlays = Settings.canDrawOverlays(this),
            )
        ) {
            AlarmSurfaceTarget.WAIT_FOR_SCREEN -> Unit
            AlarmSurfaceTarget.APPLICATION_OVERLAY -> {
                if (!overlayController.show(alarm, session)) {
                    openRingActivity(alarm.id, session.sessionId)
                }
            }
            AlarmSurfaceTarget.FULL_SCREEN_ACTIVITY -> {
                overlayController.remove()
                openRingActivity(alarm.id, session.sessionId)
            }
        }
    }

    private fun recoverActiveAlarm(startId: Int) {
        val session = sessionStore.load()
        val alarm = session?.let { repository.load(it.alarmId) }
        if (session == null || alarm == null || !alarm.enabled || session.alarmId != alarm.id) {
            stopSelfResult(startId)
            return
        }
        repository.ensureSessionOccurrence(
            occurrenceId = session.occurrenceId,
            alarmId = session.alarmId,
            scheduleRevision = session.scheduleRevision,
            sessionId = session.sessionId,
            scheduledAtMillis = session.startedAtMillis,
            snoozed = session.state == AlarmSessionState.SNOOZED,
        )
        when (session.state) {
            AlarmSessionState.FIRING -> startAlarm(
                requestedId = alarm.id,
                occurrenceKind = if (session.snoozeCount == 0) {
                    AlarmOccurrenceKind.REGULAR
                } else {
                    AlarmOccurrenceKind.SNOOZE
                },
                occurrenceId = session.occurrenceId,
                scheduleRevision = session.scheduleRevision,
                sessionId = session.sessionId,
                preemptSnooze = false,
                startId = startId,
            )
            AlarmSessionState.SNOOZED -> stopSelfResult(startId)
        }
    }

    private fun startAlarm(
        requestedId: Int,
        occurrenceKind: AlarmOccurrenceKind,
        occurrenceId: String,
        scheduleRevision: Long,
        sessionId: String,
        preemptSnooze: Boolean,
        startId: Int,
    ) {
        val alarm = repository.load(requestedId)
        if (
            alarm == null ||
            !alarm.enabled ||
            alarm.id != requestedId ||
            occurrenceId.isBlank() ||
            sessionId.isBlank() ||
            alarm.scheduleRevision != scheduleRevision
        ) {
            rejectStartIfIdle(startId)
            return
        }

        var existingSession = sessionStore.load()
        if (
            occurrenceKind == AlarmOccurrenceKind.REGULAR &&
            existingSession?.state == AlarmSessionState.SNOOZED &&
            existingSession.sessionId != sessionId &&
            preemptSnooze
        ) {
            preemptSnoozedSession(existingSession)
            existingSession = sessionStore.load()
        }
        if (occurrenceKind == AlarmOccurrenceKind.SNOOZE) {
            val isMatchingSession = existingSession?.alarmId == requestedId &&
                existingSession.occurrenceId == occurrenceId &&
                existingSession.scheduleRevision == scheduleRevision &&
                existingSession.sessionId == sessionId
            val isAlreadyResumed = isMatchingSession &&
                existingSession.state == AlarmSessionState.FIRING &&
                existingSession.snoozeCount == 1
            if (
                !isAlreadyResumed &&
                (!isMatchingSession || !sessionStore.resumeSnoozed(requestedId, System.currentTimeMillis()))
            ) {
                rejectStartIfIdle(startId)
                return
            }
        } else {
            val isMatchingSession = existingSession?.alarmId == requestedId &&
                existingSession.occurrenceId == occurrenceId &&
                existingSession.sessionId == sessionId
            if (existingSession != null && !isMatchingSession) {
                repository.deferClaimedOccurrence(occurrenceId, sessionId)
                rejectStartIfIdle(startId)
                return
            }
        }

        val occurrenceStarted = repository.markOccurrenceFiring(occurrenceId, sessionId) ||
            repository.occurrenceIsFiring(occurrenceId, sessionId)
        if (!occurrenceStarted) {
            rejectStartIfIdle(startId)
            return
        }

        repository.rememberRecentContent(alarm)

        deviceStateController.applyInitialValuesOnce(
            alarm = alarm,
            occurrenceId = occurrenceId,
            scheduleRevision = scheduleRevision,
            sessionId = sessionId,
        )
        val canUseUnlockedOverlay = AlarmSurfacePolicy.target(
            isInteractive = getSystemService(PowerManager::class.java).isInteractive,
            isKeyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            canDrawOverlays = Settings.canDrawOverlays(this),
        ) == AlarmSurfaceTarget.APPLICATION_OVERLAY
        val notification = AlarmNotificationFactory(this).buildRinging(
            alarmId = alarm.id,
            sessionId = sessionId,
            snoozeAvailable = (sessionStore.load()?.snoozeCount ?: 0) == 0,
            fullScreen = !canUseUnlockedOverlay,
        )
        val foregroundType = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0
            alarm.contentMode == ContentMode.LOCAL -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else -> 0
        }
        ServiceCompat.startForeground(
            this,
            AlarmNotificationFactory.notificationId(alarm.id),
            notification,
            foregroundType,
        )
        hasActiveForegroundSession = true

        if (alarm.contentMode == ContentMode.LOCAL && mediaPlayer == null && fallbackTone == null) {
            requestAudioFocus()
            playConfiguredLocalSound(alarm, sessionId)
        }
        val activeSession = sessionStore.load()
        if (
            activeSession?.alarmId == alarm.id &&
            activeSession.sessionId == sessionId &&
            activeSession.state == AlarmSessionState.FIRING
        ) {
            val overlayShown = overlayController.show(alarm, activeSession)
            if (!overlayShown) openRingActivity(alarm.id, sessionId)
        }
    }

    private fun rejectStartIfIdle(startId: Int) {
        if (!hasActiveForegroundSession) {
            stopSelfResult(startId)
        }
    }

    private fun playConfiguredLocalSound(
        alarm: com.routinealarm.app.model.AlarmSpec,
        sessionId: String,
    ) {
        val resolvedSource = AlarmSoundPolicy.resolve(
            visualKind = alarm.visualKind,
            visualUri = alarm.visualUri,
            audioUri = alarm.audioUri,
        )
        val configuredUri = when (resolvedSource) {
            SoundSource.LOCAL_AUDIO -> alarm.audioUri?.let(Uri::parse)
            SoundSource.VISUAL_MEDIA -> alarm.visualUri?.let(Uri::parse)
            SoundSource.DEFAULT_ALARM -> null
        }
        val usesVideoAudio = resolvedSource == SoundSource.VISUAL_MEDIA
        val canUseConfigured = configuredUri != null && (!usesVideoAudio || hasAudioTrack(configuredUri))
        if (canUseConfigured && playMediaUri(configuredUri, sessionId)) return

        playDefaultAlarmAsMedia(sessionId)
    }

    private fun playDefaultAlarmAsMedia(sessionId: String) {
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        if (!playMediaUri(alarmUri, sessionId)) startFallbackTone()
    }

    private fun playMediaUri(uri: Uri, sessionId: String): Boolean {
        val player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(this@AlarmPlaybackService, uri)
                isLooping = true
                setWakeMode(this@AlarmPlaybackService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
            }
        }.getOrNull() ?: return false

        return runCatching {
            val initialGain = PlaybackVolumeRampPolicy.gainAt(0L)
            player.setVolume(initialGain, initialGain)
            player.start()
            mediaPlayer = player
            startVolumeRamp(player, sessionId)
        }.onFailure {
            player.runCatching { release() }
        }.isSuccess
    }

    private fun startVolumeRamp(player: MediaPlayer, sessionId: String) {
        cancelVolumeRamp()
        playbackGeneration += 1L
        activePlaybackSessionId = sessionId
        volumeRampGeneration = playbackGeneration
        volumeRampSessionId = sessionId
        volumeRampPlayer = player
        volumeRampStartedAtElapsedRealtime = SystemClock.elapsedRealtime()
        mainHandler.post(volumeRamp)
    }

    private fun cancelVolumeRamp() {
        mainHandler.removeCallbacks(volumeRamp)
        volumeRampPlayer = null
        volumeRampSessionId = ""
        volumeRampStartedAtElapsedRealtime = 0L
        playbackGeneration += 1L
        volumeRampGeneration = playbackGeneration
        activePlaybackSessionId = ""
    }

    private fun hasAudioTrack(uri: Uri): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    private fun startFallbackTone() {
        fallbackTone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        mainHandler.post(fallbackToneLoop)
    }

    private fun requestAudioFocus() {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
            .also(audioManager::requestAudioFocus)
    }

    private fun snoozeAlarm(alarmId: Int, requestedSessionId: String, startId: Int) {
        val alarm = repository.load(alarmId)
        if (alarm == null || !alarm.enabled || alarm.id != alarmId) {
            rejectStartIfIdle(startId)
            return
        }
        val previousSession = sessionStore.load()
        if (previousSession == null || previousSession.sessionId != requestedSessionId) {
            rejectStartIfIdle(startId)
            return
        }
        if (!AlarmSnoozePolicy.canSnooze(alarm, previousSession, System.currentTimeMillis())) {
            return
        }
        val dueAtMillis = SnoozePolicy.dueAt(System.currentTimeMillis())
        val dueAtElapsedRealtime = SnoozePolicy.dueAt(SystemClock.elapsedRealtime())
        if (
            !sessionStore.markSnoozed(
                alarmId = alarmId,
                dueAtMillis = dueAtMillis,
                dueAtElapsedRealtime = dueAtElapsedRealtime,
                bootCount = sessionStore.currentBootCount(),
            )
        ) {
            rejectStartIfIdle(startId)
            return
        }
        if (!repository.markOccurrenceSnoozed(previousSession.occurrenceId, previousSession.sessionId)) {
            sessionStore.begin(previousSession)
            rejectStartIfIdle(startId)
            return
        }

        val snoozedSession = sessionStore.load() ?: previousSession
        runCatching { AlarmScheduler(this).scheduleSnooze(snoozedSession, dueAtMillis) }
            .onFailure {
                repository.restoreOccurrenceFiring(
                    previousSession.occurrenceId,
                    previousSession.sessionId,
                )
                sessionStore.begin(previousSession)
                return
            }

        releasePlayback()
        overlayController.remove()
        sendBroadcast(AlarmRingActivity.finishIntent(this))
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasActiveForegroundSession = false
        getSystemService(NotificationManager::class.java).notify(
            AlarmNotificationFactory.notificationId(alarmId),
            AlarmNotificationFactory(this).buildSnoozed(
                alarmId,
                previousSession.sessionId,
                dueAtMillis,
            ),
        )
        stopSelf()
    }

    private fun dismissAlarm(alarmId: Int, requestedSessionId: String, startId: Int) {
        val alarm = repository.load(alarmId)
        val session = sessionStore.load()
        if (
            alarm == null ||
            session == null ||
            alarm.id != alarmId ||
            session.alarmId != alarmId ||
            session.sessionId != requestedSessionId
        ) {
            rejectStartIfIdle(startId)
            return
        }
        if (
            session.state == AlarmSessionState.FIRING &&
            !AlarmInteractionGate.isUnlocked(
                ringStartedAtMillis = session.ringStartedAtMillis,
                delaySeconds = alarm.dismissDelaySeconds,
                nowMillis = System.currentTimeMillis(),
            )
        ) {
            return
        }
        repository.finishOccurrence(
            session.occurrenceId,
            session.sessionId,
            AlarmOccurrenceStatus.DISMISSED,
        )
        AlarmPlaybackPositionStore.clear(session.sessionId)
        releasePlayback()
        overlayController.remove()
        AlarmScheduler(this).cancelSnooze(alarmId)
        deviceStateController.finishAndMaybeRestore()
        if (alarm.repeatType == RepeatType.ONE_TIME) repository.disable(alarmId)
        sendBroadcast(AlarmRingActivity.finishIntent(this))
        getSystemService(NotificationManager::class.java)
            .cancel(AlarmNotificationFactory.notificationId(alarmId))
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasActiveForegroundSession = false
        startNextWaitingOrStop(startId)
    }

    private fun cancelAlarm(alarmId: Int, requestedSessionId: String, startId: Int) {
        val alarm = repository.load(alarmId)
        if (alarm == null || alarm.id != alarmId) {
            rejectStartIfIdle(startId)
            return
        }
        val session = sessionStore.load()
        if (
            requestedSessionId.isNotBlank() &&
            session?.sessionId != requestedSessionId
        ) {
            rejectStartIfIdle(startId)
            return
        }
        releasePlayback()
        overlayController.remove()
        AlarmScheduler(this).cancel(alarmId)
        if (session?.alarmId == alarmId) {
            repository.finishOccurrence(
                session.occurrenceId,
                session.sessionId,
                AlarmOccurrenceStatus.CANCELLED,
            )
            deviceStateController.finishAndMaybeRestore()
            AlarmPlaybackPositionStore.clear(session.sessionId)
        }
        repository.disable(alarmId)
        sendBroadcast(AlarmRingActivity.finishIntent(this))
        getSystemService(NotificationManager::class.java)
            .cancel(AlarmNotificationFactory.notificationId(alarmId))
        stopForeground(STOP_FOREGROUND_REMOVE)
        hasActiveForegroundSession = false
        startNextWaitingOrStop(startId)
    }

    private fun preemptSnoozedSession(session: AlarmSession) {
        val previousAlarm = repository.load(session.alarmId)
        AlarmScheduler(this).cancelSnooze(session.alarmId)
        releasePlayback()
        overlayController.remove()
        AlarmPlaybackPositionStore.clear(session.sessionId)
        deviceStateController.finishAndMaybeRestore()
        if (previousAlarm?.repeatType == RepeatType.ONE_TIME) {
            repository.disable(session.alarmId)
        }
        sendBroadcast(AlarmRingActivity.finishIntent(this))
        getSystemService(NotificationManager::class.java)
            .cancel(AlarmNotificationFactory.notificationId(session.alarmId))
    }

    private fun startNextWaitingOrStop(startId: Int) {
        val waiting = repository.claimNextWaitingOccurrence()
        if (waiting == null) {
            stopSelfResult(startId)
            return
        }
        startAlarm(
            requestedId = waiting.alarmId,
            occurrenceKind = AlarmOccurrenceKind.REGULAR,
            occurrenceId = waiting.occurrenceId,
            scheduleRevision = waiting.scheduleRevision,
            sessionId = waiting.sessionId,
            preemptSnooze = false,
            startId = startId,
        )
        val alarm = repository.load(waiting.alarmId)
        val activeSession = sessionStore.load()
        if (alarm != null && activeSession?.sessionId == waiting.sessionId) {
            mainHandler.post { openRingActivity(alarm.id, waiting.sessionId) }
        }
    }

    private fun openRingActivity(alarmId: Int, sessionId: String) {
        runCatching {
            startActivity(
                Intent(this, AlarmRingActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
            )
        }
    }

    private fun releasePlayback() {
        cancelVolumeRamp()
        mediaPlayer?.runCatching {
            stop()
            release()
        }
        mediaPlayer = null
        fallbackTone?.release()
        fallbackTone = null
        mainHandler.removeCallbacks(fallbackToneLoop)
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        audioFocusRequest = null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(restoreSurface)
        runCatching { unregisterReceiver(screenStateReceiver) }
        overlayController.remove()
        releasePlayback()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleSurfaceRecovery()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_START = "com.routinealarm.app.action.START_ALARM"
        private const val ACTION_DISMISS = "com.routinealarm.app.action.DISMISS_ALARM"
        private const val ACTION_CANCEL = "com.routinealarm.app.action.CANCEL_ALARM"
        private const val ACTION_SNOOZE = "com.routinealarm.app.action.SNOOZE_ALARM"
        private const val ACTION_ENSURE_VISIBLE = "com.routinealarm.app.action.ENSURE_VISIBLE"
        private const val EXTRA_PREEMPT_SNOOZE = "preempt_snooze"
        private const val FALLBACK_TONE_MILLIS = 10_000
        private const val FALLBACK_REPEAT_MILLIS = 9_500L
        private const val SURFACE_RECOVERY_DELAY_MILLIS = 120L

        fun startIntent(
            context: Context,
            alarmId: Int,
            occurrenceKind: AlarmOccurrenceKind,
            occurrenceId: String,
            scheduleRevision: Long,
            sessionId: String,
            preemptSnooze: Boolean = false,
        ): Intent =
            Intent(context, AlarmPlaybackService::class.java)
                .setAction(ACTION_START)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_KIND, occurrenceKind.name)
                .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_ID, occurrenceId)
                .putExtra(AlarmScheduler.EXTRA_SCHEDULE_REVISION, scheduleRevision)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)
                .putExtra(EXTRA_PREEMPT_SNOOZE, preemptSnooze)

        fun dismissIntent(context: Context, alarmId: Int, sessionId: String): Intent =
            Intent(context, AlarmPlaybackService::class.java)
                .setAction(ACTION_DISMISS)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)

        fun snoozeIntent(context: Context, alarmId: Int, sessionId: String): Intent =
            Intent(context, AlarmPlaybackService::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)

        fun cancelIntent(context: Context, alarmId: Int, sessionId: String = ""): Intent =
            Intent(context, AlarmPlaybackService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)

        fun ensureVisibleIntent(context: Context): Intent =
            Intent(context, AlarmPlaybackService::class.java)
                .setAction(ACTION_ENSURE_VISIBLE)
    }
}
