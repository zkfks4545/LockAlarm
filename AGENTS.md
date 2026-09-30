# Routine Alarm project agent workflow

This file applies to the whole repository. It is the project-level contract for Codex sessions and spawned subagents working on the Android alarm app.

## Agent routing & Collaboration (Codex ↔ Antigravity)

- **`Codex` (핸즈온 테크 리드 / Tech Lead & Hands-on Developer)**:
  - 아키텍처 설계, 요구사항 분석 및 범위 결정을 주도하며, **자신도 직접 코드 작성·구현·커밋을 수행**합니다.
  - 병렬 처리할 작업, 독립 서브 모듈, 세부 로직 보강, 단위 테스트 보완 등 분업이 필요한 작업을 선별하여 `.agent-tasks/inbox/TASK-xxx.md`에 명확한 작업 지시서로 작성하여 이관합니다.
  - Antigravity가 제출한 `REPORT-xxx.md`와 Git diff를 바탕으로 `reality_checker` 역할을 수행하여 최종 검수 및 머지를 승인합니다.

- **`Antigravity` (시니어 개발자 / Lead Implementer & Problem Solver)**:
  - 고유한 문제 해결사로서, **사용자의 직접 지시(Ad-hoc)를 최우선으로 즉시 개별 코딩·완결**합니다.
  - Codex가 이관한 지시서(`.agent-tasks/inbox/`)가 있을 경우 이를 받아 `.agent-tasks/in_progress/`로 이동 후, 코드 작성·수정, 단위 테스트(`gradlew.bat testDebugUnitTest`), 린트, 빌드를 전 영역 논스톱 자율 실행으로 완결합니다.
  - 완료 후 작업 지시서를 `.agent-tasks/completed/`로 옮기고, `REPORT-xxx.md`를 작성하여 Codex와 사용자에게 보고합니다.

- **동시 작업 및 충돌 방지 원칙 (File Isolation & Sync)**:
  - **영역 분리**: Codex가 본인 코딩과 Antigravity 작업을 병렬로 진행할 때는 서로 다른 파일/모듈(예: UI 레이어 vs 도메인/서비스 레이어)을 건드리도록 지시서에 대상 파일을 격리합니다.
  - **작업 우선순위**: 사용자의 실시간 직접 지시(Ad-hoc) > Codex의 이관 작업(Queue).
  - 작업 전후로 Git 상태를 확인하여 상대방의 변경 사항과 충돌이 없도록 동기화합니다.

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

## 업무데스크 연결 규칙

- `.agent-tasks/`는 공통 업무 교환소이고 `.agent/`는 CLI 실행·권한 대기·원본 출력·Codex 검토 근거 저장소입니다. 같은 업무를 양쪽 큐에 중복 발행하지 않습니다.
- 로컬 업무데스크 또는 `antigravity-handoff`의 `workdesk.py --dispatch`로 실행할 때는 어댑터가 지시서 이동과 보고 저장을 담당합니다. Antigravity는 명시된 제품 파일만 수정하며 교환소를 직접 이동하지 않습니다. 대화형 수동 작업에서는 기존 교환소 절차를 따릅니다.
- 사용자 직접 지시는 사전 지시서 작성 없이 가능합니다. 단, 진행 중인 다른 작업의 파일 소유권부터 확인하며 자동으로 강제 종료하거나 동일 파일을 동시에 수정하지 않습니다.
- 권한 거부는 위임 취소가 아닙니다. 업무를 Antigravity 담당 `AWAITING_PERMISSION`으로 보존하고, 실제 CLI 권한 확인 후 명시적으로 재개합니다. 권한 우회·전역 일괄 승인은 하지 않습니다.
- `completed`는 작업자 보고 제출 상태입니다. Codex의 diff·증거 검토와 Human 확인은 별도로 기록합니다. 미실행 테스트는 `NOT RUN`이며 실제 실행 결과의 건수를 사용합니다.
- CLI의 전체 프로젝트 해시 검사가 실행되는 동안 같은 checkout에서 다른 파일도 동시 수정하지 않습니다. 병렬 구현은 분리 checkout 또는 파일 소유권이 명확한 수동 모드를 사용합니다. 커밋·푸시 등 외부 변경은 해당 업무에서 승인된 경우에만 수행합니다.
