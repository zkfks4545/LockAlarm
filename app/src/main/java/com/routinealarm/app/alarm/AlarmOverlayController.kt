package com.routinealarm.app.alarm

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.ContentMode
import com.routinealarm.app.media.AlarmMediaDuration
import com.routinealarm.app.ui.createAlarmVisualView
import com.routinealarm.app.ui.readLocalVisualDisplaySize
import com.routinealarm.app.youtube.YouTubeEmbed
import com.routinealarm.app.youtube.createYouTubeWebView
import com.routinealarm.app.youtube.destroyYouTubeWebView

class AlarmOverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null
    private var overlaySessionId: String? = null

    fun show(alarm: AlarmSpec, session: AlarmSession): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        if (!context.getSystemService(PowerManager::class.java).isInteractive) return false
        if (overlayView != null && overlaySessionId == session.sessionId) return true
        remove()
        lateinit var root: View
        root = createSurfaceView(alarm, session) { enableSensorRotation(root) }
        @Suppress("DEPRECATION")
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = requestedOrientation(alarm)
        }
        return runCatching {
            windowManager.addView(root, params)
            overlayView = root
            overlaySessionId = session.sessionId
            root.postDelayed({ enableSensorRotation(root) }, SENSOR_UNLOCK_FALLBACK_MILLIS)
        }.isSuccess
    }

    fun remove() {
        val current = overlayView ?: return
        overlayView = null
        overlaySessionId = null
        runCatching { windowManager.removeViewImmediate(current) }
        destroyWebViews(current)
    }

    fun createSurfaceView(
        alarm: AlarmSpec,
        session: AlarmSession,
        onPlaybackReady: (() -> Unit)? = null,
    ): View = if (alarm.contentMode == ContentMode.YOUTUBE) {
        createYouTubeLayout(alarm, session, onPlaybackReady)
    } else {
        createLocalLayout(alarm, session, onPlaybackReady)
    }

    fun destroySurfaceView(view: View) {
        destroyWebViews(view)
    }

    private fun createLocalLayout(
        alarm: AlarmSpec,
        session: AlarmSession,
        onPlaybackReady: (() -> Unit)?,
    ): View {
        return createMediaFirstLayout(
            alarm = alarm,
            session = session,
            mediaView = createAlarmVisualView(
                context = context,
                visualUri = alarm.visualUri,
                visualKind = alarm.visualKind,
                cropToFill = false,
                playbackSessionId = session.sessionId,
                onPlaybackReady = onPlaybackReady,
            ),
        )
    }

    private fun createYouTubeLayout(
        alarm: AlarmSpec,
        session: AlarmSession,
        onPlaybackReady: (() -> Unit)?,
    ): View {
        return createMediaFirstLayout(
            alarm = alarm,
            session = session,
            mediaView = createYouTubeWebView(
                context = context,
                value = alarm.youtubeUrl.orEmpty(),
                playbackSessionId = session.sessionId,
                onPlaybackReady = onPlaybackReady,
            ),
        )
    }

    private fun createMediaFirstLayout(
        alarm: AlarmSpec,
        session: AlarmSession,
        mediaView: View,
    ): View {
        val delaySeconds = AlarmMediaDuration.effectiveDismissDelaySeconds(context, alarm)
        val unlockAtMillis = AlarmInteractionGate.unlockAtMillis(
            ringStartedAtMillis = session.ringStartedAtMillis,
            delaySeconds = delaySeconds,
        )
        return FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(mediaView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(
                TimedInputBlockerView(
                    context = context,
                    unlockAtMillis = unlockAtMillis,
                ),
                FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
            )
            addView(
                createAlarmHeader(alarm),
                FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    topMargin = dp(24)
                },
            )
            addView(
                CircularDismissView(
                    context = context,
                    ringStartedAtMillis = session.ringStartedAtMillis,
                    delaySeconds = delaySeconds,
                ),
                FrameLayout.LayoutParams(dp(80), dp(80)).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = dp(20)
                    marginEnd = dp(20)
                },
            )
            // The swipe surface occupies the space above the action row. It
            // is also added before the row so the explicit buttons remain
            // the topmost touch targets if the bounds ever approach each
            // other on a small or resized display.
            addView(
                AlarmDismissGestureView(
                    context = context,
                    unlockAtMillis = unlockAtMillis,
                ) {
                    dismissAlarm(alarm, session)
                },
                FrameLayout.LayoutParams(dp(280), dp(180)).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(ACTION_ROW_HEIGHT_DP + ACTION_ROW_BOTTOM_MARGIN_DP + ACTION_GESTURE_GAP_DP)
                },
            )
            addView(
                createActionRow(alarm, session, unlockAtMillis),
                FrameLayout.LayoutParams(MATCH_PARENT, dp(ACTION_ROW_HEIGHT_DP)).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    bottomMargin = dp(ACTION_ROW_BOTTOM_MARGIN_DP)
                },
            )
        }
    }

    /**
     * Keeps the two explicit actions in one centered row. The outer view is
     * full width with horizontal padding so the row remains inside the
     * display on narrow, wide, and resized/folded layouts; the actual
     * buttons stay compact and balanced instead of stretching across a
     * tablet-sized overlay.
     */
    private fun createActionRow(
        alarm: AlarmSpec,
        session: AlarmSession,
        unlockAtMillis: Long,
    ): View {
        val hasSnooze = session.state == AlarmSessionState.FIRING && session.snoozeCount >= 0
        val buttonCount = if (hasSnooze) 2 else 1
        val gap = if (hasSnooze) dp(ACTION_BUTTON_GAP_DP) else 0
        val horizontalPadding = dp(ACTION_ROW_HORIZONTAL_PADDING_DP)
        val availableWidth = (
            context.resources.displayMetrics.widthPixels - (horizontalPadding * 2) - gap
            ).coerceAtLeast(dp(1))
        val buttonWidth = minOf(
            dp(ACTION_BUTTON_MAX_WIDTH_DP),
            (availableWidth / buttonCount).coerceAtLeast(dp(1)),
        )

        return FrameLayout(context).apply {
            setPadding(horizontalPadding, 0, horizontalPadding, 0)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    if (hasSnooze) {
                        addView(
                            createSnoozeControl(alarm, session, unlockAtMillis),
                            LinearLayout.LayoutParams(buttonWidth, MATCH_PARENT).apply {
                                marginEnd = gap
                            },
                        )
                    }
                    addView(
                        createCloseButton(alarm, session, unlockAtMillis),
                        LinearLayout.LayoutParams(buttonWidth, MATCH_PARENT),
                    )
                },
                FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                },
            )
        }
    }

    private fun createAlarmHeader(alarm: AlarmSpec): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(Color.argb(126, 0, 0, 0))
            }
            addView(
                AlarmClockTextView(context),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT),
            )
            addView(
                TextView(context).apply {
                    text = alarm.label
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    contentDescription = "알람 이름 ${alarm.label}"
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    topMargin = dp(2)
                },
            )
        }

    private fun createSnoozeControl(
        alarm: AlarmSpec,
        session: AlarmSession,
        unlockAtMillis: Long,
    ): View = AlarmTimedActionButton(
        context = context,
        unlockAtMillis = unlockAtMillis,
        lockedDescription = "타이머 완료 뒤 5분 스누즈",
        activeDescription = "5분 스누즈",
        action = {
            context.startService(
                AlarmPlaybackService.snoozeIntent(
                    context = context,
                    alarmId = alarm.id,
                    sessionId = session.sessionId,
                    expectedSnoozeCount = session.snoozeCount,
                ),
            )
        },
    ).apply {
        text = "5분 스누즈"
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(Color.argb(180, 0, 0, 0))
            setStroke(dp(2), Color.WHITE)
        }
    }

    private fun createCloseButton(
        alarm: AlarmSpec,
        session: AlarmSession,
        unlockAtMillis: Long,
    ): View = AlarmTimedActionButton(
        context = context,
        unlockAtMillis = unlockAtMillis,
        lockedDescription = "타이머 완료 뒤 알람 종료",
        activeDescription = "알람 종료",
        action = { dismissAlarm(alarm, session) },
    ).apply {
        text = "알람끄기"
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(28).toFloat()
            setColor(Color.argb(180, 0, 0, 0))
            setStroke(dp(2), Color.WHITE)
        }
    }

    private fun dismissAlarm(alarm: AlarmSpec, session: AlarmSession) {
        context.startService(
            AlarmPlaybackService.dismissIntent(context, alarm.id, session.sessionId),
        )
    }

    private fun enableSensorRotation(root: View) {
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return
        if (params.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) return
        params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun requestedOrientation(alarm: AlarmSpec): Int = when (
        AlarmOrientationPolicy.initial(
            contentMode = alarm.contentMode,
            youtubeIsShorts = YouTubeEmbed.isShortsUrl(alarm.youtubeUrl.orEmpty()),
            localDisplaySize = readLocalVisualDisplaySize(
                context,
                alarm.visualUri,
                alarm.visualKind,
            ),
        )
    ) {
        InitialAlarmOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        InitialAlarmOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        InitialAlarmOrientation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    }

    private fun destroyWebViews(view: View) {
        if (view is WebView) {
            destroyYouTubeWebView(view)
            return
        }
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> destroyWebViews(view.getChildAt(index)) }
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val MATCH_PARENT = WindowManager.LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = WindowManager.LayoutParams.WRAP_CONTENT
        const val ACTION_ROW_HEIGHT_DP = 56
        const val ACTION_ROW_BOTTOM_MARGIN_DP = 24
        const val ACTION_ROW_HORIZONTAL_PADDING_DP = 24
        const val ACTION_GESTURE_GAP_DP = 16
        const val ACTION_BUTTON_GAP_DP = 8
        const val ACTION_BUTTON_MAX_WIDTH_DP = 132
        const val SENSOR_UNLOCK_FALLBACK_MILLIS = 3_000L
    }
}

@SuppressLint("ViewConstructor")
private class AlarmClockTextView(context: Context) : TextView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val nowMillis = System.currentTimeMillis()
            text = AlarmClockFormatter.format(nowMillis)
            contentDescription = "현재 시각 $text"
            val untilNextMinute = (60_000L - (nowMillis % 60_000L)).coerceAtLeast(1_000L)
            handler.postDelayed(this, untilNextMinute)
        }
    }

    init {
        setTextColor(Color.WHITE)
        textSize = 54f
        includeFontPadding = false
        gravity = Gravity.CENTER
        refresh.run()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }
}

@SuppressLint("ViewConstructor")
private class TimedInputBlockerView(
    context: Context,
    private val unlockAtMillis: Long,
) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val unlock = Runnable {
        visibility = GONE
        isClickable = false
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val remainingMillis = (unlockAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        if (remainingMillis == 0L) {
            unlock.run()
        } else {
            handler.postDelayed(unlock, remainingMillis)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(unlock)
        super.onDetachedFromWindow()
    }
}

private class AlarmTimedActionButton(
    context: Context,
    private val unlockAtMillis: Long,
    private val lockedDescription: String,
    private val activeDescription: String,
    private val action: () -> Unit,
) : TextView(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateState()
            if (!isUnlocked()) handler.postDelayed(this, 100L)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        refresh.run()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && isUnlocked()) {
            performClick()
            action()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun isUnlocked(): Boolean = System.currentTimeMillis() >= unlockAtMillis

    private fun updateState() {
        val unlocked = isUnlocked()
        alpha = if (unlocked) 1f else 0.48f
        contentDescription = if (unlocked) activeDescription else lockedDescription
    }
}

private class CircularDismissView(
    context: Context,
    private val ringStartedAtMillis: Long,
    private val delaySeconds: Int,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(trackPaint).apply { color = Color.WHITE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            invalidate()
            if (!isComplete()) handler.postDelayed(this, 50L)
        }
    }

    init {
        isClickable = false
        isFocusable = false
        contentDescription = "알람 종료 대기"
        handler.post(refresh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = minOf(width, height) / 2f
        val strokeInset = 5f * density
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
        canvas.drawCircle(centerX, centerY, radius - strokeInset, trackPaint)
        val timer = AlarmTimerPolicy.state(
            ringStartedAtMillis = ringStartedAtMillis,
            delaySeconds = delaySeconds,
            nowMillis = System.currentTimeMillis(),
        )
        val bounds = RectF(
            centerX - radius + strokeInset,
            centerY - radius + strokeInset,
            centerX + radius - strokeInset,
            centerY + radius - strokeInset,
        )
        canvas.drawArc(bounds, -90f, 360f * timer.progress, false, progressPaint)
        textPaint.textSize = (if (timer.isComplete) 32f else 18f) * density
        val label = if (timer.isComplete) "×" else timer.remainingSeconds.toString()
        val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, centerX, baseline, textPaint)
        contentDescription = if (timer.isComplete) {
            "알람 종료 타이머 완료"
        } else {
            "${timer.remainingSeconds}초 뒤 알람을 닫을 수 있음"
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun isComplete(): Boolean = AlarmTimerPolicy.isComplete(
        ringStartedAtMillis = ringStartedAtMillis,
        delaySeconds = delaySeconds,
        nowMillis = System.currentTimeMillis(),
    )
}

@SuppressLint("ViewConstructor")
private class AlarmDismissGestureView(
    context: Context,
    private val unlockAtMillis: Long,
    private val dismiss: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textSize = 14f * density
    }
    private val refresh = object : Runnable {
        override fun run() {
            updateState()
            if (!isUnlocked()) handler.postDelayed(this, 100L)
        }
    }
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    init {
        isClickable = true
        isFocusable = true
        refresh.run()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unlocked = isUnlocked()
        val centerX = width / 2f
        val handleY = height - 28f * density
        handlePaint.alpha = if (unlocked) 255 else 150
        canvas.drawRoundRect(
            centerX - 30f * density,
            handleY - 3f * density,
            centerX + 30f * density,
            handleY + 3f * density,
            3f * density,
            3f * density,
            handlePaint,
        )
        if (unlocked) {
            arrowPaint.alpha = 220
            canvas.drawLine(centerX, handleY - 10f * density, centerX, handleY - 34f * density, arrowPaint)
            canvas.drawLine(centerX, handleY - 34f * density, centerX - 8f * density, handleY - 25f * density, arrowPaint)
            canvas.drawLine(centerX, handleY - 34f * density, centerX + 8f * density, handleY - 25f * density, arrowPaint)
        }
        val text = if (unlocked) {
            "위로 밀어 알람 종료"
        } else {
            val remaining = AlarmTimerPolicy.remainingSecondsUntil(
                unlockAtMillis = unlockAtMillis,
                nowMillis = System.currentTimeMillis(),
            )
            "잠금 해제까지 ${remaining.coerceAtLeast(1)}초"
        }
        textPaint.alpha = if (unlocked) 255 else 180
        canvas.drawText(text, centerX, handleY - 56f * density, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = isUnlocked()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val shouldDismiss = tracking && AlarmDismissGesturePolicy.shouldDismiss(
                    startX = downX,
                    startY = downY,
                    endX = event.x,
                    endY = event.y,
                    minimumUpwardDistanceDp = AlarmDismissGesturePolicy.MIN_UPWARD_DISTANCE_DP * density,
                )
                tracking = false
                if (shouldDismiss) {
                    performClick()
                    dismiss()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(refresh)
        super.onDetachedFromWindow()
    }

    private fun isUnlocked(): Boolean = System.currentTimeMillis() >= unlockAtMillis

    private fun updateState() {
        alpha = if (isUnlocked()) 1f else 0.72f
        contentDescription = if (isUnlocked()) {
            "위로 밀어 알람 종료"
        } else {
            "타이머 완료 전에는 알람을 종료할 수 없음"
        }
        invalidate()
    }
}
