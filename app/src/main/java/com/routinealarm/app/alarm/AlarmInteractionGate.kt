package com.routinealarm.app.alarm

object AlarmInteractionGate {
    fun unlockAtMillis(ringStartedAtMillis: Long, delaySeconds: Int): Long =
        ringStartedAtMillis + delaySeconds.coerceAtLeast(0).toLong() * 1_000L

    fun isUnlocked(
        ringStartedAtMillis: Long,
        delaySeconds: Int,
        nowMillis: Long,
    ): Boolean = nowMillis >= unlockAtMillis(ringStartedAtMillis, delaySeconds)
}
