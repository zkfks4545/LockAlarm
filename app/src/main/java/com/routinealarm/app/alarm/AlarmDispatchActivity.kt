package com.routinealarm.app.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity

/** Handles operation PendingIntents created by versions before broadcast delivery was adopted. */
class AlarmDispatchActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAlarmWindow()
        dispatch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatch(intent)
    }

    private fun dispatch(deliveryIntent: Intent) {
        val outcome = AlarmDeliveryCoordinator.handle(this, deliveryIntent)
        if (outcome.state != AlarmDeliveryState.STARTED || outcome.alarm == null) {
            finish()
            return
        }
        routeWhenSessionReady(outcome, attempt = 0)
    }

    private fun routeWhenSessionReady(outcome: AlarmDeliveryOutcome, attempt: Int) {
        val alarm = outcome.alarm ?: return
        val session = AlarmSessionStore(this).load()
        val ready = session?.alarmId == alarm.id &&
            session.sessionId == outcome.sessionId &&
            session.state == AlarmSessionState.FIRING &&
            session.presetApplied
        if (!ready && attempt < MAX_READY_ATTEMPTS) {
            handler.postDelayed(
                { routeWhenSessionReady(outcome, attempt + 1) },
                READY_RETRY_MILLIS,
            )
            return
        }
        if (ready) {
            startActivity(
                Intent(this, AlarmRingActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
                    .putExtra(AlarmScheduler.EXTRA_SESSION_ID, outcome.sessionId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
        finish()
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
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val MAX_READY_ATTEMPTS = 100
        const val READY_RETRY_MILLIS = 50L
    }
}
