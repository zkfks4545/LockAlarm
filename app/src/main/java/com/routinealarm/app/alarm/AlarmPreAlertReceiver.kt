package com.routinealarm.app.alarm

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.routinealarm.app.data.AlarmRepository
import com.routinealarm.app.model.RepeatType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A reminder/one-occurrence skip path; it never enters the ringing service. */
class AlarmPreAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(appContext, intent)
            } catch (error: Exception) {
                Log.w("AlarmPreAlert", "Advance alert could not be processed", error)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val revision = intent.getLongExtra(AlarmScheduler.EXTRA_SCHEDULE_REVISION, -1L)
        val triggerAt = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_AT, -1L)
        if (alarmId <= 0 || revision <= 0L || triggerAt <= 0L) return
        val repository = AlarmRepository(context)
        val alarm = repository.load(alarmId) ?: return
        if (!AlarmPreAlertPolicy.isCurrentUpcomingTicket(
                alarm = alarm,
                revision = revision,
                triggerAtMillis = triggerAt,
                nowMillis = System.currentTimeMillis(),
            )
        ) return

        val occurrenceId = AlarmOccurrenceIds.regular(alarmId, revision, triggerAt).occurrenceId
        if (repository.occurrenceExists(occurrenceId)) return

        val manager = context.getSystemService(NotificationManager::class.java)
        when (intent.action) {
            ACTION_SHOW -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) return
                val factory = AlarmNotificationFactory(context)
                factory.createPreAlertChannel()
                runCatching {
                    manager.notify(
                        AlarmNotificationFactory.preAlertNotificationTag(alarmId),
                        AlarmNotificationFactory.PRE_ALERT_NOTIFICATION_ID,
                        factory.buildPreAlert(alarmId, alarm.label, revision, triggerAt),
                    )
                }.onFailure { error ->
                    Log.w("AlarmPreAlert", "Advance notification could not be shown", error)
                }
                val latest = repository.load(alarmId)
                if (latest == null ||
                    !AlarmPreAlertPolicy.isCurrentUpcomingTicket(
                        latest, revision, triggerAt, System.currentTimeMillis(),
                    ) || repository.occurrenceExists(occurrenceId)
                ) {
                    manager.cancel(
                        AlarmNotificationFactory.preAlertNotificationTag(alarmId),
                        AlarmNotificationFactory.PRE_ALERT_NOTIFICATION_ID,
                    )
                }
            }

            ACTION_KEEP -> manager.cancel(
                AlarmNotificationFactory.preAlertNotificationTag(alarmId),
                AlarmNotificationFactory.PRE_ALERT_NOTIFICATION_ID,
            )

            ACTION_SKIP -> {
                if (AlarmSessionStore(context).load()?.occurrenceId == occurrenceId) return
                val next = AlarmPreAlertPolicy.nextAfterSkippedOccurrence(alarm)
                if (alarm.repeatType != RepeatType.ONE_TIME && next == null) return
                val updated = repository.skipUpcomingOccurrence(alarm, occurrenceId, next) ?: return
                val scheduler = AlarmScheduler(context)
                scheduler.cancelRegular(alarmId)
                if (updated.enabled && scheduler.canScheduleExactAlarms()) {
                    scheduler.schedule(updated)
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW = "com.routinealarm.app.action.SHOW_PRE_ALERT"
        const val ACTION_SKIP = "com.routinealarm.app.action.SKIP_UPCOMING_ALARM"
        const val ACTION_KEEP = "com.routinealarm.app.action.KEEP_UPCOMING_ALARM"
    }
}
