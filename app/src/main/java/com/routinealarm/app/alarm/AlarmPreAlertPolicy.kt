package com.routinealarm.app.alarm

import com.routinealarm.app.model.AlarmSpec
import com.routinealarm.app.model.RepeatType
import java.time.ZoneId

/** The reminder is global and never changes the regular alarm's delivery time. */
object AlarmPreAlertPolicy {
    const val LEAD_MILLIS = 10 * 60 * 1_000L

    fun reminderAtMillis(triggerAtMillis: Long, nowMillis: Long): Long? {
        val reminderAt = triggerAtMillis - LEAD_MILLIS
        return reminderAt.takeIf { it > nowMillis && triggerAtMillis > nowMillis }
    }

    fun isCurrentUpcomingTicket(
        alarm: AlarmSpec,
        revision: Long,
        triggerAtMillis: Long,
        nowMillis: Long,
    ): Boolean = alarm.enabled && alarm.id > 0 &&
        alarm.scheduleRevision == revision &&
        alarm.triggerAtMillis == triggerAtMillis &&
        nowMillis >= triggerAtMillis - LEAD_MILLIS &&
        nowMillis < triggerAtMillis

    fun nextAfterSkippedOccurrence(
        alarm: AlarmSpec,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? = if (alarm.repeatType == RepeatType.ONE_TIME) {
        null
    } else {
        AlarmScheduleResolver.nextTriggerAtMillis(
            alarm = alarm,
            nowMillis = alarm.triggerAtMillis + 1_000L,
            zoneId = zoneId,
        )
    }
}
