package com.routinealarm.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.ui.readLocalVisualDisplaySize
import com.routinealarm.app.youtube.YouTubeEmbed

class AlarmRingActivity : ComponentActivity() {
    private lateinit var surfaceController: AlarmOverlayController
    private var surfaceView: View? = null
    private var displayedSessionId: String? = null
    private val sensorUnlock = Runnable { enableSensorRotation() }
    private val finishReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val requestedSessionId = intent?.getStringExtra(AlarmScheduler.EXTRA_SESSION_ID)
            if (requestedSessionId != null && requestedSessionId == displayedSessionId) {
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        ContextCompat.registerReceiver(
            this,
            finishReceiver,
            IntentFilter(ACTION_FINISH_RING),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // The persisted service session is authoritative. A stale full-screen
        // intent can arrive after a newer alarm has already preempted it; in
        // that case show the current session instead of closing the new alarm.
        val activeSession = AlarmSessionStore(this).load()
            ?.takeIf { it.state == AlarmSessionState.FIRING }
        val alarm = activeSession?.let { AlarmRepository(this).load(it.alarmId) }
        if (alarm == null || !alarm.enabled) {
            finish()
            return
        }

        surfaceController = AlarmOverlayController(this)
        displayedSessionId = activeSession.sessionId
        showAlarmSurface(alarm, activeSession)
    }

    private fun configureAlarmWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun applyInitialPlaybackOrientation(alarm: AlarmSpec) {
        requestedOrientation = when (
            AlarmOrientationPolicy.initial(
                contentMode = alarm.contentMode,
                youtubeIsShorts = YouTubeEmbed.isShortsUrl(alarm.youtubeUrl.orEmpty()),
                localDisplaySize = readLocalVisualDisplaySize(
                    this,
                    alarm.visualUri,
                    alarm.visualKind,
                ),
            )
        ) {
            InitialAlarmOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            InitialAlarmOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            InitialAlarmOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    private fun enableSensorRotation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    private fun showAlarmSurface(alarm: AlarmSpec, session: AlarmSession) {
        window.decorView.removeCallbacks(sensorUnlock)
        displayedSessionId = session.sessionId
        applyInitialPlaybackOrientation(alarm)
        val previous = surfaceView
        val next = surfaceController.createSurfaceView(
            alarm = alarm,
            session = session,
            onPlaybackReady = ::enableSensorRotation,
        )
        surfaceView = next
        setContentView(next)
        previous?.let(surfaceController::destroySurfaceView)
        window.decorView.postDelayed(sensorUnlock, SENSOR_UNLOCK_FALLBACK_MILLIS)
    }

    override fun onUserLeaveHint() {
        requestActiveSurfaceRecovery()
        super.onUserLeaveHint()
    }

    override fun onStop() {
        if (!isFinishing && !isChangingConfigurations) requestActiveSurfaceRecovery()
        super.onStop()
    }

    override fun onDestroy() {
        window.decorView.removeCallbacks(sensorUnlock)
        runCatching { unregisterReceiver(finishReceiver) }
        surfaceView?.let { view -> surfaceController.destroySurfaceView(view) }
        surfaceView = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configureAlarmWindow()
    }

    private fun requestActiveSurfaceRecovery() {
        runCatching { startService(AlarmPlaybackService.ensureVisibleIntent(this)) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Ignore the incoming identity when it differs from the current
        // persisted session. It may be an old start intent delivered after a
        // newer alarm took ownership of the single alarm surface.
        val activeSession = AlarmSessionStore(this).load()
            ?.takeIf { it.state == AlarmSessionState.FIRING }
        val alarm = activeSession?.let { session -> AlarmRepository(this).load(session.alarmId) }
        if (activeSession != null && alarm != null && alarm.enabled) {
            showAlarmSurface(alarm, activeSession)
            return
        }
        finish()
    }

    companion object {
        private const val ACTION_FINISH_RING = "com.routinealarm.app.action.FINISH_RING"
        private const val SENSOR_UNLOCK_FALLBACK_MILLIS = 3_000L

        fun finishIntent(context: Context, sessionId: String): Intent =
            Intent(ACTION_FINISH_RING)
                .setPackage(context.packageName)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)
    }
}
