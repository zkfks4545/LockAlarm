package com.routinealarm.app.alarm

enum class AlarmSessionAction {
    SNOOZE,
    RESUME_SNOOZE,
}

object AlarmSessionStateMachine {
    fun nextState(
        currentState: AlarmSessionState,
        snoozeCount: Int,
        action: AlarmSessionAction,
    ): AlarmSessionState? = when (action) {
        AlarmSessionAction.SNOOZE ->
            AlarmSessionState.SNOOZED.takeIf {
                currentState == AlarmSessionState.FIRING && snoozeCount >= 0
            }
        AlarmSessionAction.RESUME_SNOOZE ->
            AlarmSessionState.FIRING.takeIf {
                currentState == AlarmSessionState.SNOOZED && snoozeCount > 0
            }
    }
}
