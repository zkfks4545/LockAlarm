package com.routinealarm.app.alarm

object SnoozePolicy {
    const val DURATION_MILLIS = 5 * 60 * 1_000L

    fun dueAt(actionAtMillis: Long): Long = Math.addExact(actionAtMillis, DURATION_MILLIS)

    fun reanchoredWallDueAt(
        nowWallMillis: Long,
        nowElapsedRealtime: Long,
        dueAtElapsedRealtime: Long,
    ): Long = Math.addExact(nowWallMillis, dueAtElapsedRealtime - nowElapsedRealtime)
}
