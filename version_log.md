# 루틴 알람 버전·빌드 내역

작성 기준: 2026-09-23 (Asia/Seoul)

이 문서는 `CHANGELOG.md`, 프로젝트 명세 문서, `outputs` 폴더의 APK 산출물을 기준으로 정리한 버전별 개선 및 빌드 기록이다.

현재 앱 버전은 **0.15.20**이며, Gradle 설정은 다음과 같다.

- `versionCode`: 35
- `versionName`: `0.15.20`
- 최신 APK: `outputs/routine-alarm-integrated-final-v15.20-debug.apk`
- 빌드 검증 명령: `gradlew.bat testDebugUnitTest lintDebug assembleDebug`

## 버전별 개선 내역

| 버전 | 주요 개선 | 검증 결과 | APK |
|---|---|---|---|
| 초기 MVP | 1회성 정확 알람, `AlarmManager.setAlarmClock()`, 포그라운드 서비스, 잠금화면 전체화면 알람, 밝기·음량 저장/복원, 로컬 미디어 재생 | 최초 MVP 산출물 | `routine-alarm-mvp-debug.apk` |
| D-Snooze | 동일 세션을 유지하는 5분 스누즈와 재알람 예약 흐름 | 스누즈 기능 검증 산출물 | `routine-alarm-d-snooze-debug.apk` |
| 초기 통합 v2~v4 | Room 기반 복수 알람, 기존 알람 마이그레이션, 단발·요일 반복·포함/제외 날짜, 중복 전달 방지, 부팅·시간 변경 후 재예약, 밝기·음량 세션 정책, YouTube 공식 IFrame 재생 | 버전별 상세 변경사항은 별도 기록되지 않음 | `routine-alarm-integrated-final-v2/v3/v4-debug.apk` |
| 0.5.0 | 시·분 직접 입력, 테스트 간격 입력, 이미지·GIF·영상·음악 선택 및 미리보기, 자동 소리 선택, 최근 콘텐츠 30개 저장 | JVM 단위 테스트 41건, Android lint 오류 0건 | `routine-alarm-integrated-final-v5-debug.apk` |
| 0.6.0 | 영상 회전 메타데이터 반영, 원본 비율 유지, 화면 안에서 가능한 최대 크기로 표시 | JVM 단위 테스트 45건 | `routine-alarm-integrated-final-v6-debug.apk` |
| 0.7.0 | 영상 우선 전체화면 배치, 영상 위에 알람명·스누즈·원형 X를 겹쳐 표시 | JVM 단위 테스트 45건, Android lint 오류 0건 | `routine-alarm-integrated-final-v7-debug.apk` |
| 0.8.0 | 뒤로가기·홈·최근 앱 이탈 대응, 오버레이 복구, 종료 지연 중 미디어·X·스와이프 입력 차단 | JVM 단위 테스트 52건, Android lint 오류 0건 | `routine-alarm-integrated-final-v8-debug.apk` |
| 0.9.0 | 회전·폴드 대응, 잠금화면/오버레이 UI 통합, 로컬·YouTube 재생 위치 이어보기, 필수 권한 게이트 | JVM 단위 테스트 64건, Android lint 오류 0건 | `routine-alarm-integrated-final-v9-debug.apk` |
| 0.10.0 | 이미지·GIF·WebP·영상·음악 5개 탭, 검색·정렬, MediaStore 기반 기기 미디어 보관함 | JVM 단위 테스트 71건, Android lint 오류 0건 | `routine-alarm-integrated-final-v10-debug.apk` |
| 0.11.0 | 파일 확장자 기반 미디어 분류, 앱 시작 시 필수 권한 설정, 권한 완료 전 편집·테스트 차단 | JVM 단위 테스트 72건, Android lint 오류 0건 | `routine-alarm-integrated-final-v11-debug.apk` |
| 0.12.0 | 잠금화면형 울림 UI, 타이머 전 입력 차단, 완료 후 X·위 스와이프·1회 스누즈, YouTube 썸네일 미리보기, Galaxy 영상 조회 개선, Android 14 선택 영상 권한 안내 | JVM 단위 테스트 90건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v12-debug.apk` |
| 0.13.0 | 알람별 미리보기 스위치, 로컬·YouTube 카드 미리보기, 단일 YouTube IFrame 미리보기, 초 단위 타이머 탭, 설정 시각 오름차순 정렬, Room 4→5 마이그레이션 | JVM 단위 테스트 93건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v13-debug.apk` |
| 0.13.1 | 대표 알람과 하위 알람 카드 UI 통일, 190dp 카드 고정 높이·클리핑, 대표 카드만 로컬 영상 무음 반복 재생하고 나머지는 포스터 표시 | JVM 단위 테스트 93건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v13.1-debug.apk` |
| 0.15.1 | 카드 로컬 영상 원본 비율·중앙 크롭과 카드 경계 보정, YouTube 홈 썸네일 전용 표시 | JVM 단위 테스트 93건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.1-debug.apk` |
| 0.15.2 | 로컬 알람 음원의 30초 개별 gain 점진 상승 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.2-debug.apk` |
| 0.15.3 | 반응형 내비게이션 인셋과 편집 화면 시스템 뒤로가기 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.3-debug.apk` |
| 0.15.4 | 앱 표시 이름을 LockAlarm으로 변경 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.4-debug.apk` |
| 0.15.5 | 알람 종료 버튼을 `알람끄기` 둥근 버튼으로 통일 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.5-debug.apk` |
| 0.15.6 | 폴드·창 크기 변경 중 일시적 0초 위치를 무시하고 로컬 영상의 마지막 정상 위치를 보존 | JVM 단위 테스트 96건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `BBED58A8C30170247F7C5CE95F87E7F1C66F5CB7680C333BC757077C4874A54C` | `routine-alarm-integrated-final-v15.6-debug.apk` |
| 0.15.7 | 화면 잠금 타이머 켜짐/꺼짐, 켜짐 0~60초, 꺼짐 0초, Room 5→6 마이그레이션 | JVM 단위 테스트 97건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `609615DCC44308E06BCC93D160CD5D5B2FD45F11EB6F429EB8C550AC175E5A82` | `routine-alarm-integrated-final-v15.7-debug.apk` |
| 0.15.8 | 겹친 새 정규 알람 우선 선점, 이전 FIFO 대기 발생 취소, 낮아진 `STREAM_MUSIC`의 목표까지 점진 복원, 세션 제한 종료 broadcast | JVM 단위 테스트 100건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `956D88A3501738096327A427F793FFB91B3DF48DA36D46166E6103810F387500` | `routine-alarm-integrated-final-v15.8-debug.apk` |
| 0.15.9 | 숫자 시간 입력 중 날짜 오변경 방지, 저장 시 오늘 과거 시각만 내일로 자동 보정 | JVM 단위 테스트 100건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `EB028E3C7A425200413B95D932F84F06A91FB0738D096C447EA6A768A91D18AE` | `routine-alarm-integrated-final-v15.9-debug.apk` |
| 0.15.10 | 홈에서 저장한 다크·라이트 테마를 알람 편집 화면에도 동기화하고 편집 화면 배경을 현재 Material 테마로 적용 | JVM 단위 테스트 100건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `DBEE9419C754FF46A7BF72E59AA333BAD1EF0E077A77436620BBFA67E74F3BD0` | `routine-alarm-integrated-final-v15.10-debug.apk` |
| 0.15.11 | 활성화 시 오늘 우선 예약, 이미 지난 시각만 다음날/다음 유효일 보정, 사용자 날짜 선택 보존, 별도 매일 반복 메뉴 및 일정 계산 | JVM 단위 테스트 108건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `9ADDAEB688857329EDEC8DEC904CC7F6E80CA052F51376979C1E107088CA9BBA` | `routine-alarm-integrated-final-v15.11-debug.apk` |
| 0.15.12 | 홈 토글 활성화의 오래된 날짜 재기준, 레거시 날짜 미선택 처리, `알람끄기` 버튼 터치 우선순위 보정 | JVM 단위 테스트 110건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `2C9E53EEB9D16086A21C77F9F06D9054C9BAF2DEBD0C927C9FA46513A653F608` | `routine-alarm-integrated-final-v15.12-debug.apk` |
| 0.15.13 | 위로 밀기 영역을 하단 버튼 행 위로 분리, `5분 스누즈`·`알람끄기` 인접 중앙 행 및 스누즈 후 단일 버튼 중앙 정렬 | JVM 단위 테스트 110건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `1C3C15C88FEE13745529F137EF9086C1FFC26A7F3EA50FDEAB48C831FD31572C` | `routine-alarm-integrated-final-v15.13-debug.apk` |
| 0.15.14 | 스누즈 재울림 미디어 위치 0초 초기화, 홈·편집 로컬 영상 미리보기의 TextureView 카드 경계 고정 | JVM 단위 테스트 111건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `2542F1BDEFD43BC1A8762829DA0FFA8254BD420F2140EFBE6F88567B2630B0F5` | `routine-alarm-integrated-final-v15.14-debug.apk` |
| 0.15.15 | 스누즈 예약 인텐트에 회차·예정 시각을 포함하고 수신부·서비스에서 현재 세션을 이중 검증, 조기 전달은 남은 시간으로 재예약 | JVM 단위 테스트 134건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `5F3E8C2A94821F50B11D8E474202798D9F9BA8D0550332ADE25C04F579BE0851` | `routine-alarm-integrated-final-v15.15-debug.apk` |
| 0.15.16 | 스누즈 횟수 제한 제거, 재울림 화면의 스누즈 버튼 유지, `5분 뒤에 알람이 울립니다.` 별도 확인 알림 추가 | JVM 단위 테스트 135건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `51D3733999A970E0CB02F848C97F82BD99BF0409743E97EFA5D29072D327010E` | `routine-alarm-integrated-final-v15.16-debug.apk` |
| 0.15.17 | YouTube IFrame 컨트롤·키보드·전체화면·영상 표면 입력을 차단하고 알람 native 동작만 유지 | JVM 단위 테스트 136건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `1C46F69650AEBD53C6A4A1D0DC1E184DD09B7E21E90E13AC2001C7E4166A22A9` | `routine-alarm-integrated-final-v15.17-debug.apk` |
| 0.15.18 | 알람 해제 시 이전 밝기를 먼저 적용한 뒤 자동/수동 모드를 복원하고, 종료 경합 시 종료 세션 복원을 보강 | JVM 단위 테스트 135건, `lintDebug` 성공, `assembleDebug` 성공; SHA-256 `55E51666F3808C2975530F5D2BBC254309BAB5E00367C928674F3AAFCDD70D63` | `routine-alarm-integrated-final-v15.18-debug.apk` |
| 0.15.19 | 화면 잠금 타이머의 최대값을 선택한 로컬 미디어 길이로 확장하고, 길이를 확인할 수 없으면 60초 fallback을 사용 | JVM 단위 테스트 140건, 실패/오류 0건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `E53FB08A2B312DA1CAFAC22AA69808D3C20DA457A13E4957DE731D10FF3169F2` | `routine-alarm-integrated-final-v15.19-debug.apk` |
| 0.15.20 | 반복 알람의 현재 날짜 우선 재계산, 울림 해제 후 같은 날 재발 방지 및 내일부터 재예약 | JVM 단위 테스트 142건, 실패/오류 0건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `F01674354E47B1301261E2B7385D88BDD066A67B047157D1787FD5F778887F0C` | `routine-alarm-integrated-final-v15.20-debug.apk` |

참고: 현재 변경 기록에는 `0.14.x`와 `0.15.0`의 별도 기능·빌드 항목이 없으며, `0.13.1` 다음 기록은 `0.15.1`이다.

## 기능 발전 흐름

### 알람 핵심 기능

- 단일 1회성 알람에서 Room 기반 복수 알람으로 확장했다.
- 단발·요일별 반복·매일 반복·포함 날짜·제외 날짜를 지원한다.
- `scheduleRevision`, `occurrenceId`, `sessionId`와 Room 트랜잭션 선점으로 중복·오래된 알람 전달을 차단한다.
- 현재 울림·스누즈 대기·스누즈 재울림 중인 기존 세션은 새 정규 알람이 선점하며, 단일 서비스·재생 세션에서 새 알람을 즉시 시작한다. 이전 FIFO `WAITING` 발생은 다시 시작하지 않는다.
- 홈에서 선택한 다크·라이트 테마는 공통 `RoutineAlarmTheme`을 통해 알람 목록과 알람 추가·편집 화면에 동일하게 적용하고, 편집 루트 배경도 `MaterialTheme.colorScheme.background`를 사용한다.
- 울림 중 `STREAM_MUSIC`이 알람 목표보다 낮아지면 2초마다 최대 음량의 5%(최소 1단계)씩 목표까지 올리고, 목표보다 높은 사용자의 음량은 낮추지 않는다. 스누즈 대기·선점·해제·취소·서비스 종료에서는 감시를 중지한다.
- 알람 해제 시 저장된 이전 밝기를 먼저 적용한 뒤 자동·수동 밝기 모드를 복원하고, 종료 경합에서도 종료 세션의 복원 절차가 누락되지 않게 한다.
- 새 알람의 날짜는 오늘로 시작하며 숫자 시·분 입력 중 날짜를 자동 변경하지 않는다. 저장 시 오늘의 선택 시각이 지난 경우에만 내일로 보정해 한 자리 입력 과정의 날짜 오변경을 방지한다.
- 홈 토글을 다시 켤 때 오래된 1회성 날짜는 현재 기준으로 재설정하고, 레거시 알람의 날짜 미선택 상태도 안전하게 보정한다.
- 알람 활성화와 독립된 `homePreviewEnabled` 상태로 카드 미리보기를 켜고 끌 수 있으며, 미리보기 변경은 스케줄 revision이나 예약을 바꾸지 않는다.
- 목록은 활성 여부와 무관하게 설정 시각 오름차순으로 정렬하고, 타이머 탭은 `SystemClock` 기준으로 화면 이동 뒤에도 남은 시간을 유지한다.
- 울림 화면의 위로 밀기 영역은 하단 동작 행과 분리해 위쪽에 두며, `5분 스누즈`와 `알람끄기`는 같은 중앙 가로 행에서 인접하게 제공한다. 스누즈 후에는 `알람끄기`만 중앙에 둔다.
- 부팅·앱 교체·시간 변경·시간대 변경·정확 알람 권한 변경 후 예약을 복구한다.

### 스누즈·울림 제어

- 울림 및 각 스누즈 재울림에서 횟수 제한 없이 5분 스누즈를 다시 사용할 수 있다.
- 스누즈는 기존 세션과 밝기·음량 스냅샷을 유지하며 초기값을 다시 적용하지 않는다.
- 스누즈 재울림은 미디어 위치를 0초부터 시작하고, 예약 인텐트의 회차·예정 시각을 수신부와 서비스에서 이중 검증한다. 조기 전달이면 남은 시간으로 재예약한다.
- 스누즈 횟수 제한을 제거하고 재울림 화면에서도 스누즈를 유지하며, `5분 뒤에 알람이 울립니다.` 확인 알림을 별도로 표시한다.
- 타이머 진행 중에는 종료·스누즈·미디어 조작·스와이프를 차단한다.
- 타이머 완료 후 하단 좌측 스누즈, 우측 X, 160dp 이상 수직 위 스와이프 종료를 제공한다.
- 알람별 화면 잠금 타이머를 끄거나 0초부터 선택한 로컬 미디어의 최대 길이까지 설정할 수 있으며, 길이를 확인할 수 없는 콘텐츠는 60초를 최대값으로 사용한다. 0초에서는 울림 직후 종료·스누즈·위 스와이프를 허용한다.
- 울림·스누즈 중 새 정규 알람이 도착하면 기존 세션을 `PREEMPTED`로 종결하고 새 알람을 즉시 시작하며, 이전 FIFO `WAITING` 발생은 취소한다.
- 해제 요청은 서비스가 현재 세션 ID와 해제 가능 시각을 검증한 뒤 처리한다.

### 미디어·재생

- 이미지·GIF·WebP·로컬 영상·음악·YouTube를 지원한다.
- 소리는 `별도 로컬 음악 → 영상 자체 소리 → 기기 기본 알람음` 순서로 결정한다.
- 홈 카드의 로컬 영상 미리보기는 음소거하고, 대표 카드 외의 로컬 영상은 디코더 충돌을 피하기 위해 포스터로 표시할 수 있다. YouTube 홈 카드는 공식 썸네일만 표시한다.
- 로컬 알람 음원의 `MediaPlayer` 개별 gain은 10%에서 시작해 30초 동안 250ms 간격으로 100%까지 상승하며, 기기 `STREAM_MUSIC` 초기값은 다시 덮어쓰지 않는다.
- 로컬 영상은 회전 정보를 반영하고 원본 비율을 유지한다.
- YouTube 일반 영상은 가로, Shorts는 세로로 시작하며 재생 준비 후 센서 회전을 허용한다.
- YouTube IFrame 컨트롤·키보드·전체화면·영상 표면 입력을 차단해 공식 재생 경로는 유지하되 알람 native 동작만 조작할 수 있게 한다.
- 잠금화면 Activity와 다른 앱 위 오버레이가 공통 재생 표면을 사용한다.
- 화면 복구·회전·폴드 전환 시 세션별 마지막 재생 위치에서 이어보기를 시도한다.
- 화면 잠금 타이머의 최대값은 선택한 로컬 미디어 길이까지 확장하고, 길이를 확인할 수 없는 콘텐츠는 60초 fallback을 사용한다.

### 미디어 선택·권한

- MediaStore 기반 이미지·GIF·WebP·영상·음악 5개 탭을 제공한다.
- 최근순·이름순 정렬과 파일명 검색을 지원한다.
- MIME 정보가 비어 있거나 일반 MIME인 파일은 확장자로 지원 형식을 판별한다.
- Galaxy 영상 조회에서 `MediaStore.Video`와 외부 `MediaStore.Files`를 함께 확인한다.
- Android 14에서 선택한 영상 권한만 있는 경우 전체 목록 제한 안내와 전체 영상 접근 요청을 표시한다.
- Android 사진 선택기와 직접 찾아보기는 MediaStore 권한이 없어도 사용할 수 있다.

## APK 빌드 산출물

파일 수정 시각, 크기, SHA-256 기준이다.

| 산출물 | 파일 시각 | 크기(bytes) | SHA-256 |
|---|---:|---:|---|
| `routine-alarm-d-snooze-debug.apk` | 2026-08-01 16:06:11 | 15,436,488 | `4AB3E3496D1660BFD818DD846C02497B8C1931EA2A5D1728B8AA7AE8AB628A9D` |
| `routine-alarm-mvp-debug.apk` | 2026-08-01 17:22:25 | 11,934,459 | `EB23FB6839A9FBC5363426F6A98765CEF9A2F43B10728B6C30DD35159780E564` |
| `routine-alarm-integrated-final-v2-debug.apk` | 2026-08-01 18:20:45 | 11,983,871 | `C03CA5F51FD5D0351EF9BA22CF3B92165BE87D2D05C1ED476BFC578F1F067C09` |
| `routine-alarm-integrated-final-v3-debug.apk` | 2026-08-01 18:38:49 | 12,000,259 | `77FA4FB1FCABC214CE2362D2D9A75E4C7326AB983006E782EEFFFF5E1809BDA2` |
| `routine-alarm-integrated-final-v4-debug.apk` | 2026-08-01 18:50:15 | 12,000,263 | `A30416F18C6973415A847C3A10038B8ECF9B4486BBFA9FB2117D29371CD04A69` |
| `routine-alarm-integrated-final-v5-debug.apk` | 2026-08-01 19:05:27 | 12,052,846 | `8DDEB5C312E915BB69B3B4465DFD9441C256D2343A951751372B3AA6A22B66C5` |
| `routine-alarm-integrated-final-v6-debug.apk` | 2026-08-02 11:31:26 | 12,016,643 | `0FC17B60ADA16E05D9F8A7CD55B42213969D8D1BBA42219A7849EE497B89CFB1` |
| `routine-alarm-integrated-final-v7-debug.apk` | 2026-08-02 11:41:12 | 12,016,643 | `634E35925F2913A822F90C606D776839B06EF5BEB21633A6D03D2378B3833F9D` |
| `routine-alarm-integrated-final-v8-debug.apk` | 2026-08-02 11:59:16 | 12,084,281 | `97A4ED6194F48010BEAF3EF90A65A9725A250B32C0CC966293D1CAB96B13B638` |
| `routine-alarm-integrated-final-v9-debug.apk` | 2026-08-02 12:26:43 | 12,016,663 | `205CC3FC10307C9C807D3645E11F0D04E418A97A3AA5451D5482360948E70342` |
| `routine-alarm-integrated-final-v10-debug.apk` | 2026-08-02 13:16:21 | 12,095,128 | `ADCFBB90DBCB52A757F4FD56A577F1991DCB2E806BFF29D34EBD61B30B795C7C` |
| `routine-alarm-integrated-final-v11-debug.apk` | 2026-08-03 21:29:28 | 12,082,406 | `2D3FCF7507B1D7600C768536507B35C0558807ADE1CEABB499ED6DC44E9581CD` |
| `routine-alarm-integrated-final-v12-debug.apk` | 2026-08-13 22:24:10 | 12,353,540 | `5FB56AA4F25C187077C1B07B10BEBB29C9A2570F18E075A750A50A7B928DBE95` |
| `routine-alarm-integrated-final-v15.6-debug.apk` | 2026-08-17 11:34:16 | 12,180,710 | `BBED58A8C30170247F7C5CE95F87E7F1C66F5CB7680C333BC757077C4874A54C` |
| `routine-alarm-integrated-final-v15.7-debug.apk` | 2026-08-24 23:44:11 | 12,197,094 | `609615DCC44308E06BCC93D160CD5D5B2FD45F11EB6F429EB8C550AC175E5A82` |
| `routine-alarm-integrated-final-v15.8-debug.apk` | 2026-09-03 12:07:20 | 12,197,094 | `956D88A3501738096327A427F793FFB91B3DF48DA36D46166E6103810F387500` |
| `routine-alarm-integrated-final-v15.9-debug.apk` | 2026-09-03 12:23:07 | 12,197,094 | `EB028E3C7A425200413B95D932F84F06A91FB0738D096C447EA6A768A91D18AE` |
| `routine-alarm-integrated-final-v15.10-debug.apk` | 2026-09-03 12:34:15 | 12,197,090 | `DBEE9419C754FF46A7BF72E59AA333BAD1EF0E077A77436620BBFA67E74F3BD0` |
| `routine-alarm-integrated-final-v15.11-debug.apk` | 2026-09-04 10:43:03 | 12,197,090 | `9ADDAEB688857329EDEC8DEC904CC7F6E80CA052F51376979C1E107088CA9BBA` |
| `routine-alarm-integrated-final-v15.12-debug.apk` | 2026-09-04 11:10:22 | 12,197,090 | `2C9E53EEB9D16086A21C77F9F06D9054C9BAF2DEBD0C927C9FA46513A653F608` |
| `routine-alarm-integrated-final-v15.13-debug.apk` | 2026-09-04 11:30:52 | 12,197,090 | `1C3C15C88FEE13745529F137EF9086C1FFC26A7F3EA50FDEAB48C831FD31572C` |
| `routine-alarm-integrated-final-v15.14-debug.apk` | 2026-09-11 19:44:07 | 12,197,094 | `2542F1BDEFD43BC1A8762829DA0FFA8254BD420F2140EFBE6F88567B2630B0F5` |
| `routine-alarm-integrated-final-v15.15-debug.apk` | 2026-09-11 20:49:48 | 12,273,091 | `5F3E8C2A94821F50B11D8E474202798D9F9BA8D0550332ADE25C04F579BE0851` |
| `routine-alarm-integrated-final-v15.16-debug.apk` | 2026-09-11 21:10:52 | 12,197,090 | `51D3733999A970E0CB02F848C97F82BD99BF0409743E97EFA5D29072D327010E` |
| `routine-alarm-integrated-final-v15.17-debug.apk` | 2026-09-11 22:02:56 | 12,445,104 | `1C46F69650AEBD53C6A4A1D0DC1E184DD09B7E21E90E13AC2001C7E4166A22A9` |
| `routine-alarm-integrated-final-v15.18-debug.apk` | 2026-09-19 17:51:15 | 12,197,090 | `55E51666F3808C2975530F5D2BBC254309BAB5E00367C928674F3AAFCDD70D63` |
| `routine-alarm-integrated-final-v15.19-debug.apk` | 2026-09-19 20:14:16 | 12,213,474 | `E53FB08A2B312DA1CAFAC22AA69808D3C20DA457A13E4957DE731D10FF3169F2` |
| `routine-alarm-integrated-final-debug.apk` | 2026-09-04 11:30:52 | 12,197,090 | `1C3C15C88FEE13745529F137EF9086C1FFC26A7F3EA50FDEAB48C831FD31572C` |

`routine-alarm-integrated-final-debug.apk`는 현재 v15.17 APK와 크기 및 SHA-256이 동일하다. 최신 버전 v15.20 APK와는 별도 파일이며, v15.20의 SHA-256은 `F01674354E47B1301261E2B7385D88BDD066A67B047157D1787FD5F778887F0C`이다.

현재 `outputs` 폴더에는 v15.7~v15.20과 `final-debug.apk`가 보관되어 있다. 아래 빌드는 과거 산출물이 현재 폴더에서 정리된 뒤에도 변경 문서에 해시가 남아 있는 기록이다.

### 현재 폴더에 없는 과거 빌드 기록

| 버전 | 기록된 APK 이름 | SHA-256 |
|---|---|---|
| 0.12.0 후속 메인 UI 통합 검증 | `routine-alarm-integrated-final-v12-debug.apk` 파일명 재사용 | `D0C1299BC58CB172683627245000A0B7BFB1B125C74EF2E01F475270A8270EDD` |
| 0.13.0 | `routine-alarm-integrated-final-v13-debug.apk` | `1EC0EB3EA507B9C8C8602B8AE3DA22B302CD983F1276F7A59E62FDA0B1118B45` |
| 0.13.1 | `routine-alarm-integrated-final-v13.1-debug.apk` | `567680C04830DE2F67631C452FC8AD382A005EC2A518DDE4FBB299F01DD0519A` |
| 0.15.1 | `routine-alarm-integrated-final-v15.1-debug.apk` | `8209BB3A979215446A1BF71898619504AC84EBF1D9835E467F703EC11AE735A6` |
| 0.15.2 | `routine-alarm-integrated-final-v15.2-debug.apk` | `E8106DC65CABCD120F93202E4AE3622478E34F188ABC20919ED802FA5A0BBB9E` |
| 0.15.3 | `routine-alarm-integrated-final-v15.3-debug.apk` | `2061F7D71AECC9B7D6AC9F4D279F0843A472FFA4FD4D7AE355A863CCBA5C9750` |
| 0.15.4 | `routine-alarm-integrated-final-v15.4-debug.apk` | `E8A866D7CECBE9B3DCE6FFFAF92806375CF7DEDB44B7568229FE92388025487A` |
| 0.15.5 | `routine-alarm-integrated-final-v15.5-debug.apk` | `49E1DAE7AD1008DF58AE575F198624E8AA8CA174B14B0397DD5B0F29533AABE4` |

## 0.12.0 내부 검증 진행

0.12.0은 기능 추가에 따라 단위 테스트와 APK를 여러 차례 재빌드했다.

1. 78건: 하단 잠금 해제 카운트다운 회귀 테스트
2. 83건: MediaStore MIME·확장자·duration 및 새 시각 파일 선택 시 오디오 제거 테스트
3. 86건: YouTube URL 상태·공식 썸네일·HTML helper 테스트
4. 88건: Galaxy 영상 컬렉션·권한·0건 안내 테스트
5. 90건: Android 14 선택 영상 전용 권한 안내 테스트 포함 최종본

최종 검증 결과는 `testDebugUnitTest` 90건 통과, `lintDebug` 성공, `assembleDebug` 성공이다.

이후 버전에서는 0.13.0·0.13.1에서 93건, 0.15.1에서 93건, 0.15.2~0.15.6에서 96건, 0.15.7에서 97건, 0.15.8~0.15.10에서 100건, 0.15.11에서 108건, 0.15.12~0.15.13에서 110건, 0.15.14에서 111건, 0.15.15에서 134건, 0.15.16에서 135건, 0.15.17에서 136건, 0.15.18에서 135건, 0.15.19에서 140건, 0.15.20에서 142건으로 검증 범위를 확장했다. 각 버전의 상세 결과와 해시는 버전별 개선 내역 및 빌드 기록 표에 반영했다.

## 남은 실기기 검증

- Galaxy 잠금화면·Doze·재부팅·OEM 절전 정책
- 권한 회수 및 프로세스 강제 종료 후 세션 복구
- 홈·최근 앱·화면 꺼짐/켜짐 후 전체화면 복구
- Galaxy Fold 접기·펼치기 및 화면 크기 변경
- 로컬·YouTube 화면 인계 시 재생 위치와 음성 연속성
- 손상되거나 접근 권한이 사라진 미디어 처리
- YouTube 네트워크·자동재생 제한 상황
- TalkBack, 200% 글꼴, 색 대비

물리 전원 버튼과 Android 설정의 강제 종료는 일반 앱에서 차단할 수 없다.
