# 데이터 모델: 복수 일정과 스누즈

## 현재 MVP 물리 저장 구조

- Room `alarms`: 알람 정의, 현지 시각, 반복 종류, 요일/추가/제외 날짜, 밝기·음량·복원, 콘텐츠, 캐시된 다음 실행 시각을 한 행에 저장한다. 유한 날짜 집합은 MVP에서 정렬된 CSV 값으로 직렬화한다.
- Room `recent_contents`: 실제로 저장하거나 실행한 고유 로컬 파일과 YouTube URL을 최신순 30개까지 유지한다. 행동 이벤트 로그는 저장하지 않는다.
- Room `alarm_occurrences`: 정규 발생의 revision/occurrence/session 식별자와 `CLAIMED`, 호환용 `WAITING`, `FIRING`, `SNOOZED`, 종료 상태를 저장한다. 전달 수락과 겹침 조정은 이 테이블을 사용하는 단일 Room 트랜잭션에서 선점한다.
- `SharedPreferences` `active_alarm_session`: 울림/스누즈 중 프로세스 복구에 필요한 활성 세션 하나만 동기적으로 저장한다.
- Room 스키마 7은 `alarms.oneTimeDateUserSelected`로 1회성 날짜가 편집 화면에서 직접 선택됐는지 구분한다. 이전 스키마의 값은 `false`로 이관해 활성화 시 오늘 우선 규칙으로 다시 계산한다.
- 계정, 서버 API, 클라우드 동기화, 원격 분석 저장소는 없다.

`scheduleRevision`은 사용자 편집·활성 변경 때 증가하고 자동 반복 진행 때는 유지된다. 정규 예약은 `alarmId + scheduleRevision + triggerAt`에서 안정적인 `occurrenceId`와 `sessionId`를 만들며, 오래된 revision과 이미 선점된 occurrence는 Room 트랜잭션에서 거부한다.

## AlarmDefinition

| 필드 | 의미 |
|---|---|
| `alarmId` | 변경되지 않는 알람 식별자 |
| `enabled` | 미래 정규 발생 예약 여부 |
| `localTime` | 사용자가 의도한 현지 시각 |
| `ruleType` | `ONE_TIME`, 선택 요일 `WEEKLY` 또는 모든 요일 `DAILY` |
| `oneTimeDate` | 1회성 날짜, 반복에서는 없음 |
| `oneTimeDateUserSelected` | 1회성 날짜를 사용자가 `오늘`·`내일`·날짜 선택으로 직접 지정했는지 여부. 활성화 시 명시 날짜는 유지하고 미지정 날짜는 오늘 우선으로 계산한다. |
| `weekdays` | WEEKLY에서 1개 이상 선택하는 요일. DAILY에서는 비워 둔다. |
| `includeDates` | 요일과 무관하게 추가 실행할 유한 날짜 집합 |
| `excludeDates` | 다른 규칙보다 우선해 건너뛸 유한 날짜 집합 |
| `scheduleRevision` | 편집할 때 증가해 오래된 전달을 거부하는 값 |
| `nextRegularAt` | 캐시된 다음 정규 실행 instant |
| `dismissTimerEnabled` | 화면 잠금 타이머 사용 여부. 꺼짐이면 `dismissDelaySeconds`를 0으로 저장한다. |
| `dismissDelaySeconds` | 우측 상단 원형 진행이 종료 조작으로 전환될 때까지의 지연. 켜짐 상태에서 0초부터 선택한 로컬 미디어 중 가장 긴 길이까지 설정하며, 길이 확인 불가 시 60초를 최대값으로 사용한다. |
| `devicePreset` | 밝기·미디어 음량 초기값과 복원 정책 |
| `contentProfile` | 로컬 시각 URI와 선택 음악 URI를 저장하고 `LOCAL_AUDIO` → `VISUAL_MEDIA` → `DEFAULT_ALARM` 우선순위를 자동 해석하거나 YouTube 임베디드 설정을 표현 |

검증 규칙: ONE_TIME은 미래 또는 정책상 허용된 날짜 하나를 가져야 한다. WEEKLY는 요일이 하나 이상이어야 하며 DAILY는 요일 선택 없이 모든 요일을 사용한다. 같은 날짜가 포함·제외 양쪽에 있으면 저장 전에 충돌을 표시하며 실행 계산에서는 제외가 우선한다.

## AlarmOccurrence

| 필드 | 의미 |
|---|---|
| `occurrenceId` | `alarmId + intendedLocalDateTime + revision`에서 안정적으로 생성 |
| `alarmId` | 원본 알람 |
| `intendedLocalDateTime` | 반복 규칙이 의도한 현지 날짜·시각 |
| `scheduledInstant` | AlarmManager에 전달할 실제 instant |
| `revision` | 생성 당시 알람 revision |
| `status` | `CLAIMED`, `WAITING`, `FIRING`, `SNOOZED`, `SNOOZE_CLAIMED`, `DISMISSED`, `CANCELLED`, `PREEMPTED` |

## AlarmSession

| 필드 | 의미 |
|---|---|
| `sessionId` | 한 정규 발생에서 최종 종료까지의 실행 식별자 |
| `occurrenceId` | 원본 정규 발생 |
| `startedAt` | 최초 울림 시작 instant |
| `ringStartedAt` | 현재 울림 회차 시작 instant; 재울림 때 갱신 |
| `dismissAvailableAt` | 현재 회차의 일반 해제 가능 instant |
| `snoozeCount` | 현재 회차까지 사용한 스누즈 수. 최초 울림은 `0`이며 스누즈할 때마다 1 증가하고 별도 상한은 없다. |
| `snoozeDueAt` | SNOOZED일 때 재울림 목표 instant |
| `snoozeDueAtElapsedRealtime` | 같은 부팅에서 벽시계 변경과 무관하게 검증할 elapsed realtime 목표 |
| `snoozeBootCount` | elapsed 목표가 현재 부팅에 속하는지 확인하는 부팅 카운터. 미확인 시 벽시계로 보조한다. |
| `previousDeviceState` | 첫 울림 직전의 밝기 모드·밝기·음량 |
| `presetApplied` | 세션당 초기값 적용 멱등성 표식 |
| `restorePolicy` | `RESTORE_PREVIOUS` 또는 `KEEP_CURRENT` |
| `state` | `FIRING_LOCKED`, `FIRING_DISMISSIBLE`, `SNOOZED`, 종료 상태 |
| `endReason` | `DISMISSED`, `CANCELLED_BEFORE_FIRE`, `CANCELLED_BY_DISABLE`, `EXPIRED` |

스누즈는 새 세션을 만들지 않는다. 재울림에서는 `ringStartedAt`과 `dismissAvailableAt`만 갱신하고 `previousDeviceState`, `presetApplied`, `snoozeCount`를 유지한다. 예약 인텐트는 `alarmId`, `occurrenceId`, `scheduleRevision`, `sessionId`, `snoozeCount`, `dueAtMillis`를 함께 운반하며 수신부와 서비스가 현재 `SNOOZED` 세션과 비교한다. 같은 부팅에서는 elapsed 기한을 우선하고, 부팅이 바뀌었거나 카운터를 확인할 수 없으면 벽시계 기한을 사용한다.

겹침 시 `CLAIMED`, `FIRING`, `SNOOZED`, `SNOOZE_CLAIMED` 중 하나가 있으면 새 정규 발생은 Room 트랜잭션에서 기존 발생을 `PREEMPTED`로 전이하고 `CLAIMED`로 즉시 선점한다. 새 빌드는 `WAITING`을 만들지 않으며, 이전 버전에서 남은 `WAITING` 행도 시작하지 않고 취소한다. 서비스는 새 occurrence가 아직 `CLAIMED`인지 확인한 뒤 이전 세션을 정리하므로 오래된 start intent가 새 세션을 역선점하지 않는다.

알람이 울리는 동안의 `STREAM_MUSIC` 목표는 `AlarmDefinition.devicePreset`에서 읽는다. 시작 순간 목표를 한 번 적용한 뒤, 현재 값이 목표보다 낮아진 경우에만 서비스가 2초마다 최대 음량의 5%(최소 1단계)씩 올린다. 목표 이상으로 사용자가 올린 값은 유지하며, 스누즈 대기·선점·최종 해제·취소에서는 감시를 중지한다.

## 관계

```text
AlarmDefinition 1 ─── N AlarmOccurrence
AlarmOccurrence 1 ─── 0..1 AlarmSession
AlarmDefinition 1 ─── 1 DevicePreset
AlarmDefinition 1 ─── 1 ContentProfile
AlarmDefinition ─── 최근 사용 시점에 RecentContent를 갱신 (콘텐츠 값으로 중복 제거)
```

## RecentContent

| 필드 | 의미 |
|---|---|
| `key` | 콘텐츠 종류와 참조값을 결합한 중복 제거 키 |
| `type` | `VISUAL_FILE`, `AUDIO_FILE`, `YOUTUBE` |
| `reference` | 영속 파일 URI 또는 복사 가능한 YouTube URL |
| `displayName` | 로컬 파일 선택 시 확인한 표시 이름 |
| `visualKind` | 시각 파일의 `IMAGE`, `ANIMATED_IMAGE`, `VIDEO` 종류 |
| `usedAt` | 마지막 저장 또는 실제 실행 시각 |

최근 30개 고유 콘텐츠만 유지한다. 로컬 파일은 앱 안에서 열람·미리보기만 제공하고 URI 복사는 제공하지 않는다. YouTube 항목만 URL 복사를 제공한다. 최근 목록 삭제는 AlarmDefinition과 원본 파일을 변경하지 않는다.

## 마이그레이션

- 기존 `AlarmRepository`의 단일 `AlarmSpec`이 있으면 안정적 새 `alarmId` 하나로 가져온다.
- 기존 `triggerAtMillis`는 ONE_TIME의 현지 날짜·시각으로 변환한다.
- Room 6→7에서 `oneTimeDateUserSelected`를 추가하고 기존 행은 `false`로 초기화한다. 과거 버전은 날짜를 자동 보정했는지 직접 선택했는지 저장하지 않았으므로, 이후 활성화 시 오늘 우선 규칙을 적용한다.
- 새 저장과 다음 발생 계산·예약이 성공한 뒤에만 마이그레이션 완료 표식을 기록한다.
- 변환 실패 시 기존 데이터를 보존하고 사용자에게 재저장을 요청한다.
