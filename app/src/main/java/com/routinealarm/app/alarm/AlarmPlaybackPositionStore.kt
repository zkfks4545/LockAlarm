package com.routinealarm.app.alarm

import java.util.UUID

class AlarmPlaybackLease internal constructor(
    private val sessionId: String,
    private val token: String,
) {
    fun positionMillis(): Long = AlarmPlaybackPositionStore.positionMillis(sessionId)

    fun updatePosition(positionMillis: Long) {
        AlarmPlaybackPositionStore.updatePosition(sessionId, token, positionMillis)
    }

    fun release() {
        AlarmPlaybackPositionStore.release(sessionId, token)
    }
}

object AlarmPlaybackPositionStore {
    private data class PlaybackState(
        var positionMillis: Long = 0L,
        var ownerToken: String? = null,
        var onSuperseded: (() -> Unit)? = null,
    )

    private val states = mutableMapOf<String, PlaybackState>()

    fun attach(sessionId: String, onSuperseded: () -> Unit): AlarmPlaybackLease {
        val token = UUID.randomUUID().toString()
        synchronized(this) {
            val state = states.getOrPut(sessionId) { PlaybackState() }
            state.onSuperseded?.invoke()
            state.ownerToken = token
            state.onSuperseded = onSuperseded
        }
        return AlarmPlaybackLease(sessionId, token)
    }

    @Synchronized
    fun positionMillis(sessionId: String): Long = states[sessionId]?.positionMillis ?: 0L

    @Synchronized
    internal fun updatePosition(sessionId: String, token: String, positionMillis: Long) {
        val state = states[sessionId] ?: return
        if (state.ownerToken != token) return
        state.positionMillis = positionMillis.coerceAtLeast(0L)
    }

    @Synchronized
    internal fun release(sessionId: String, token: String) {
        val state = states[sessionId] ?: return
        if (state.ownerToken != token) return
        state.ownerToken = null
        state.onSuperseded = null
    }

    @Synchronized
    fun clear(sessionId: String) {
        states.remove(sessionId)
    }

    @Synchronized
    fun clearAll() {
        states.clear()
    }
}
