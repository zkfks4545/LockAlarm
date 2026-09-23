# Routine Alarm project agent workflow

This file applies to the whole repository. It is the project-level contract for Codex sessions and spawned subagents working on the Android alarm app.

## Agent routing

- `sol_director` owns requirement decomposition, scope decisions, acceptance criteria, and implementation sequencing. It is read-only and must return a concise plan with affected files and risks.
- `product_manager` checks product intent, user-facing behavior, MVP scope, and Living Spec alignment. It is read-only.
- `android_architect` checks Kotlin, Jetpack Compose, Android lifecycle, alarm, media, permission, and persistence boundaries. It is read-only.
- `luna_implementer` owns the actual code and documentation changes after the plan is accepted. It uses the smallest focused patch and runs the relevant tests, lint, and APK build.
- `reality_checker` is the evidence gate. It reviews the resulting diff and verification output, calls out unproven device behavior, and must not edit product code.

For a substantial change, use this order: `sol_director` -> (as needed) `product_manager` and `android_architect` -> `luna_implementer` -> `reality_checker`. Keep write work in one agent at a time so agents do not overwrite each other.

## Repository expectations

- Keep this app offline-first and personal-use: no server, account, cloud sync, telemetry, or remote analytics.
- Preserve the alarm-session contract: apply initial device brightness and media volume once at ring start, allow user changes while ringing, and only recover media volume upward toward the alarm target when it is lowered below that target. Never lower a user-selected higher volume, and restore only according to the configured dismiss policy.
- Treat the foreground service and persisted session state as the source of truth across home, back, rotation, lock-screen recovery, and process recreation.
- Keep local media and the official YouTube handoff paths separate; never add downloading, caching, extraction, or unofficial playback.
- Update the relevant Living Spec/current-state/changelog documentation when behavior changes.
- Before handoff, report exact verification commands and distinguish JVM/build evidence from real-device evidence.

## Change discipline

- Read the relevant existing code and specs before editing.
- Prefer small, reversible patches and existing dependencies.
- Do not claim device/OEM/Doze behavior is verified without Galaxy or equivalent real-device evidence.
- The primary agent owns final integration and the user-facing summary; subagents return findings or commits only when explicitly requested.
