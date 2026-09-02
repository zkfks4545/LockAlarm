# 루틴 알람 버전·빌드 내역

작성 기준: 2026-08-14 (Asia/Seoul)

이 문서는 `CHANGELOG.md`, 프로젝트 명세 문서, `outputs` 폴더의 APK 산출물을 기준으로 정리한 버전별 개선 및 빌드 기록이다.

현재 앱 버전은 **0.15.7**이며, Gradle 설정은 다음과 같다.

- `versionCode`: 22
- `versionName`: `0.15.7`
- 최신 APK: `outputs/routine-alarm-integrated-final-v15.7-debug.apk`
- 빌드 검증 명령: `gradlew.bat test lintDebug assembleDebug`

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
| 0.15.1 | 카드 로컬 영상 원본 비율·중앙 크롭과 카드 경계 보정, YouTube 홈 썸네일 전용 표시 | JVM 단위 테스트 93건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.1-debug.apk` |
| 0.15.2 | 로컬 알람 음원의 30초 개별 gain 점진 상승 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.2-debug.apk` |
| 0.15.3 | 반응형 내비게이션 인셋과 편집 화면 시스템 뒤로가기 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.3-debug.apk` |
| 0.15.4 | 앱 표시 이름을 LockAlarm으로 변경 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.4-debug.apk` |
| 0.15.5 | 알람 종료 버튼을 `알람끄기` 둥근 버튼으로 통일 | JVM 단위 테스트 96건, `lintDebug` 성공, `assembleDebug` 성공 | `routine-alarm-integrated-final-v15.5-debug.apk` |
| 0.15.6 | 폴드·창 크기 변경 중 일시적 0초 위치를 무시하고 로컬 영상의 마지막 정상 위치를 보존 | JVM 단위 테스트 96건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `BBED58A8C30170247F7C5CE95F87E7F1C66F5CB7680C333BC757077C4874A54C` | `routine-alarm-integrated-final-v15.6-debug.apk` |
| 0.15.7 | 화면 잠금 타이머 켜짐/꺼짐, 켜짐 0~60초, 꺼짐 0초, Room 5→6 마이그레이션 | JVM 단위 테스트 97건, `lintDebug` 오류 0건, `assembleDebug` 성공; SHA-256 `609615DCC44308E06BCC93D160CD5D5B2FD45F11EB6F429EB8C550AC175E5A82` | `routine-alarm-integrated-final-v15.7-debug.apk` |

## 기능 발전 흐름

### 알람 핵심 기능

- 단일 1회성 알람에서 Room 기반 복수 알람으로 확장했다.
- 단발·요일 반복·포함 날짜·제외 날짜를 지원한다.
- `scheduleRevision`, `occurrenceId`, `sessionId`와 Room 트랜잭션 선점으로 중복·오래된 알람 전달을 차단한다.
- 현재 울림 중인 새 정규 알람은 FIFO로 대기시키고, 스누즈 대기 중인 새 정규 알람은 우선 실행한다.
- 부팅·앱 교체·시간 변경·시간대 변경·정확 알람 권한 변경 후 예약을 복구한다.

### 스누즈·울림 제어

- 최초 울림에서 5분 스누즈를 1회 사용할 수 있다.
- 스누즈는 기존 세션과 밝기·음량 스냅샷을 유지하며 초기값을 다시 적용하지 않는다.
- 타이머 진행 중에는 종료·스누즈·미디어 조작·스와이프를 차단한다.
- 타이머 완료 후 하단 좌측 스누즈, 우측 X, 160dp 이상 수직 위 스와이프 종료를 제공한다.
- 해제 요청은 서비스가 현재 세션 ID와 해제 가능 시각을 검증한 뒤 처리한다.

### 미디어·재생

- 이미지·GIF·WebP·로컬 영상·음악·YouTube를 지원한다.
- 소리는 `별도 로컬 음악 → 영상 자체 소리 → 기기 기본 알람음` 순서로 결정한다.
- 로컬 영상은 회전 정보를 반영하고 원본 비율을 유지한다.
- YouTube 일반 영상은 가로, Shorts는 세로로 시작하며 재생 준비 후 센서 회전을 허용한다.
- 잠금화면 Activity와 다른 앱 위 오버레이가 공통 재생 표면을 사용한다.
- 화면 복구·회전·폴드 전환 시 세션별 마지막 재생 위치에서 이어보기를 시도한다.

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

`routine-alarm-integrated-final-debug.apk`는 v15.7 APK와 크기 및 SHA-256이 동일한 최신본 복사본이다.

## 0.12.0 내부 검증 진행

0.12.0은 기능 추가에 따라 단위 테스트와 APK를 여러 차례 재빌드했다.

1. 78건: 하단 잠금 해제 카운트다운 회귀 테스트
2. 83건: MediaStore MIME·확장자·duration 및 새 시각 파일 선택 시 오디오 제거 테스트
3. 86건: YouTube URL 상태·공식 썸네일·HTML helper 테스트
4. 88건: Galaxy 영상 컬렉션·권한·0건 안내 테스트
5. 90건: Android 14 선택 영상 전용 권한 안내 테스트 포함 최종본

최종 검증 결과는 `testDebugUnitTest` 90건 통과, `lintDebug` 성공, `assembleDebug` 성공이다.

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
