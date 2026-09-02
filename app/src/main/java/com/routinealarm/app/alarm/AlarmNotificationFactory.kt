package com.routinealarm.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.routinealarm.app.MainActivity
import com.routinealarm.app.R

class AlarmNotificationFactory(private val context: Context) {
    fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    fun buildRinging(
        alarmId: Int,
        sessionId: String,
        snoozeAvailable: Boolean,
        fullScreen: Boolean,
    ): Notification {
        val ringIntent = PendingIntent.getActivity(
            context,
            alarmId,
            Intent(context, AlarmRingActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .putExtra(AlarmScheduler.EXTRA_SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("알람이 울리고 있습니다")
            .setContentText("눌러서 알람 화면 열기")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(ringIntent)
        if (fullScreen) builder.setFullScreenIntent(ringIntent, true)
        if (snoozeAvailable) {
            val snoozeIntent = PendingIntent.getService(
                context,
                alarmId + SNOOZE_REQUEST_OFFSET,
                AlarmPlaybackService.snoozeIntent(context, alarmId, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_alarm, "5분 뒤 다시 알림", snoozeIntent)
        }
        return builder.build()
    }

    fun buildSnoozed(alarmId: Int, sessionId: String, dueAtMillis: Long): Notification {
        return buildSnoozeStatus(
            alarmId = alarmId,
            sessionId = sessionId,
            title = "5분 뒤 다시 울립니다",
            content = android.text.format.DateFormat.format("a h:mm", dueAtMillis),
        )
    }

    fun buildSnoozePermissionBlocked(alarmId: Int): Notification = buildSnoozeStatus(
        alarmId = alarmId,
        sessionId = AlarmSessionStore(context).load()?.sessionId.orEmpty(),
        title = "스누즈 재등록에 접근이 필요합니다",
        content = "앱에서 정확 알람 접근을 허용하거나 이 알람을 해제하세요.",
    )

    private fun buildSnoozeStatus(
        alarmId: Int,
        sessionId: String,
        title: String,
        content: CharSequence,
    ): Notification {
        val appIntent = PendingIntent.getActivity(
            context,
            alarmId,
            Intent(context, MainActivity::class.java)
                .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = PendingIntent.getService(
            context,
            alarmId + DISMISS_REQUEST_OFFSET,
            AlarmPlaybackService.dismissIntent(context, alarmId, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(appIntent)
            .addAction(R.drawable.ic_alarm, "알람 해제", dismissIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "active_alarm_v1"
        fun notificationId(alarmId: Int): Int = 41_000 + alarmId
        private const val DISMISS_REQUEST_OFFSET = 30_000
        private const val SNOOZE_REQUEST_OFFSET = 50_000
    }
}
