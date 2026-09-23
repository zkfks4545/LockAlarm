# 루틴 알람: 로컬 우선 MVP 구현 요약

## 고정된 제품 범위

- 서버, 계정, 로그인, 클라우드 동기화, 원격 분석 없이 한 기기에서 동작한다.
- 여러 알람을 만들고 1회성·요일별 반복·매일 반복을 선택하며, 반복 일정에 추가·제외 날짜를 지정할 수 있다.
- 울림 및 각 스누즈 재울림에서 횟수 제한 없이 5분 스누즈를 사용할 수 있다.
- 울림 시작 시 밝기와 미디어 음량을 지정 초기값으로 한 번만 적용한다. 이후 밝기는 사용자의 조작을 유지하고, `STREAM_MUSIC`은 목표보다 낮아진 경우에만 제한된 속도로 목표까지 복원한다.
- 최종 해제 시 원래 기기 값 복원 또는 현재 값 유지 중 알람별 정책을 적용한다.
- 로컬 이미지/GIF/영상과 영상 자체 오디오·별도 로컬 음악·기기 기본 대체음을 지원한다.
- 스누즈 예약 성공 뒤 재울림하는 영상과 음원은 0초부터 다시 시작하며, 홈·편집 로컬 영상 미리보기는 카드별 `TextureView` 경계 안에서만 중앙 크롭한다.
- 0.15.14 검증은 JVM 단위 테스트 111건, `lintDebug`, `assembleDebug` 성공으로 완료했으며 실제 Galaxy 기기 검증은 남아 있다.
- 0.15.15부터 스누즈 예약 인텐트에 회차·예정 시각을 넣고 수신부·재생 서비스가 현재 세션을 이중 검증한다. 같은 부팅에서는 elapsed realtime, 재부팅·미확인 부팅에서는 wall-clock due를 사용하며 조기 전달은 예약을 소모하지 않고 재등록한다.
- 0.15.15 검증은 JVM 단위 테스트 134건, `lintDebug`, `assembleDebug` 성공으로 완료했으며 실제 Galaxy의 AlarmManager·재부팅·Doze 전달 검증은 남아 있다.
- 0.15.16부터 재울림 화면의 스누즈 버튼을 계속 유지하고, 성공 시 기존 대기 알림과 별도의 `5분 뒤에 알람이 울립니다.` 확인 알림을 발행한다. JVM 단위 테스트 135건, `lintDebug`, `assembleDebug` 성공으로 확인했다.
- 0.15.17부터 YouTube 공식 IFrame 플레이어는 컨트롤·키보드·전체화면·영상 표면 입력을 차단한 표시 전용 표면으로 동작한다. JVM 단위 테스트 136건, `lintDebug`, `assembleDebug` 성공으로 확인했다.
- 0.15.20부터 반복 알람은 복구 시 오늘의 유효 시각을 우선하고, 울림 해제 시 오늘 회차를 건너뛰어 매일 알람이 내일부터 재개된다. JVM 단위 테스트 142건, `lintDebug`, `assembleDebug` 성공으로 확인했다.
- YouTube는 다운로드·추출·광고 차단 없이 공식 IFrame API 플레이어를 알람 화면 안에 임베드한다.

## 로컬 저장

- `routine-alarm.db`의 `alarms` 테이블에 알람 정의와 다음 실행 시각을 저장한다.
- `recent_contents` 테이블에 실제로 저장하거나 실행한 로컬 파일과 YouTube URL을 중복 없이 저장한다.
- `alarm_occurrences` 테이블에서 schedule revision, occurrence, session과 실행·대기·스누즈·종료 상태를 관리한다.
- 실행·스누즈·해제·오류 행동 로그는 저장하지 않는다.
- 최근 콘텐츠는 30개만 유지하며 로컬 파일은 열람 전용, YouTube URL은 복사 가능하다.
- 기존 단일 SharedPreferences 알람은 앱 최초 실행 때 Room으로 한 번 가져온다.
- 울림/스누즈 중 즉시 복구가 필요한 활성 세션 하나는 별도 로컬 설정에 저장한다.

## GUI

- 알람 탭: 필수 접근 상태, 복수 알람 목록, 활성화 스위치, 편집, 삭제.
- 편집 화면: 이름, 선택 창 또는 숫자 직접 입력 시각, 숫자 입력 중 날짜 고정, 활성화 시 오늘 우선·지난 시각만 다음날 보정, 오늘·내일 빠른 날짜, 단발/요일별/매일 반복, 추가·제외 날짜, 다음 실행 미리보기.
- 편집 화면: 초기 밝기·미디어 음량, 3~60초 원형 종료 타이머, 해제 후 복원 정책.
- 편집 화면: 로컬 시각 콘텐츠 선택 또는 YouTube URL, 저장·활성화, 5·10·30·60초 빠른 선택과 1~3600초 사용자 입력형 테스트.
- 편집 화면의 유효한 YouTube URL은 공식 썸네일과 재생 버튼을 제공하고, 탭 시 편집 화면 전용 조작 불가 공식 IFrame 미리보기와 닫기 동작으로 전환한다. 빈/지원 불가 URL은 안내하며 다운로드·캐시·MP3 추출은 사용하지 않는다.
- 로컬 파일 선택 뒤 파일명과 이미지·GIF·영상 미리보기를 표시하고 음악 파일은 미리듣기·정지를 제공한다.
- 로컬 파일 선택 화면은 이미지·GIF·WebP·영상·음악 탭, MediaStore 최신 썸네일·음악 길이·미리듣기, 최근순·이름순 정렬과 파일명 검색을 제공한다.
- Android 사진 선택기와 다운로드·클라우드용 직접 찾아보기를 보조 경로로 유지하며 선택 결과는 URI만 저장한다.
- 로컬 소리는 별도 음악 파일 → 영상 자체 오디오 → 기기 기본 알람음 순서로 자동 결정한다.
- 최근 콘텐츠 탭: 파일명·시각 미리보기·음악 미리듣기, YouTube URL 복사와 확인창을 거친 목록 전체 삭제.

## 알람 실행 경로

- 알람별 고유 PendingIntent와 알림 ID를 사용한다.
- 활성 알람마다 다음 정규 발생 하나만 `setAlarmClock()`으로 등록한다.
- 요일별·매일 반복은 전달 직후 다음 유효 발생을 계산·저장·재등록하고, 울림 해제 시에도 오늘 회차를 건너뛰도록 다시 확인한다.
- 부팅, 앱 교체, 시간·시간대 변경, 정확 알람 접근 변경 뒤 활성 예약을 복구한다.
- 스누즈는 같은 세션을 유지해 최초 밝기·음량 스냅샷과 초기값을 다시 적용하지 않는다.
- 정규·스누즈 전달은 Room 트랜잭션에서 원자적으로 선점해 오래된 revision과 중복 occurrence를 거부한다.
- 현재 울림·스누즈 대기·스누즈 재울림 중 도착한 새 정규 알람은 기존 세션을 `PREEMPTED`로 종료·복원한 뒤 즉시 우선 실행한다. 단일 서비스·재생 세션은 유지하고 이전 FIFO `WAITING` 행은 취소한다.
- 새 알람 시작 서비스는 Room에서 새 occurrence가 `CLAIMED`인지 재확인한 경우에만 이전 세션을 정리해 오래된 start intent의 역선점을 방지한다.
- 울림 중 `STREAM_MUSIC`이 알람 목표보다 낮아지면 2초마다 최대 음량의 5%(최소 1단계)씩 올리며, 목표보다 높은 값은 낮추지 않는다. 스누즈 대기·선점·해제·취소·destroy 시 감시를 취소한다.
- 정확 알람은 Activity 실행에 의존하지 않는 Broadcast로 전달하고 포그라운드 서비스를 먼저 시작한다.
- 잠금 해제 화면에서는 허용된 `TYPE_APPLICATION_OVERLAY`가 즉시 전체화면을 차지하고, 잠금·화면 꺼짐 상태에서는 full-screen intent Activity가 화면을 켠다.
- 로컬 영상은 90°/270° 회전 메타데이터를 포함한 실제 표시 규격에 따라 가로/세로로 전환하고, 자르거나 늘이지 않은 채 화면 안의 최대 크기로 표시한다.
- 로컬·YouTube 영상에 전체 화면을 먼저 제공하고 이름·스누즈·원형 종료 조작부는 남는 여백 또는 영상 가장자리 위에 겹쳐 영상 크기를 줄이지 않는다.
- 뒤로가기는 활성 알람을 닫지 않으며 홈·최근 앱·작업 제거 뒤 서비스 오버레이, 화면 켜짐·잠금 해제 뒤 잠금 상태에 맞는 전체화면을 복구한다.
- 종료 지연 중 영상 입력·X·위 스와이프·스누즈는 전체화면 차단막으로 막고, 지연 완료 뒤 차단막 제거와 하단 중앙 인접 동작 행의 스누즈·알람끄기·160dp 위 스와이프를 함께 활성화한다. 재울림에서도 스누즈를 반복할 수 있다.
- YouTube 일반 영상은 가로, Shorts는 세로, 로컬 콘텐츠는 회전 메타데이터가 반영된 실제 비율로 시작하고 준비 뒤 전체 방향 센서를 허용한다.
- 잠금 Activity와 오버레이는 같은 네이티브 재생 표면과 반투명 스누즈·원형 타이머·X를 사용하며 방향·폴드 화면 크기 변경에서 재생기를 재생성하지 않는다.
- 창 전환이 필요한 화면 복구는 세션별 마지막 재생 위치와 단일 표면 소유권을 인계해 이전 화면의 중복 재생을 멈추고 로컬·YouTube 이어보기를 시도한다.
- 필수 접근 완료 전에는 추가·편집·테스트를 차단하고 누락된 접근을 표시하되 알람 목록과 최근 콘텐츠 열람은 유지한다.
- 종료 지연은 우측 상단 원형 진행으로 표시하고, 상단 중앙 `HH:mm`/알람명과 하단 잠금 손잡이를 공통 표면에 겹쳐 표시한다. 완료 뒤에는 하단 큰 X와 위 스와이프 종료를 제공한다.
- YouTube 모드는 앱 ID 기반 HTTPS Referer·origin으로 공식 임베디드 플레이어를 로드하고 임베드 금지·비공개·식별 실패를 구분해 안내한다. IFrame 컨트롤과 WebView 직접 입력은 차단한다.
- 영상 미디어 선택은 `MediaStore.Video`와 외부 `MediaStore.Files`를 함께 조회하고 size/IS_PENDING 필터 없이 MIME·확장자로 지원 형식을 판별한다. 권한 미허용·조회 실패·실제 0건을 구분하며 Android 14 선택 영상 권한과 잘못된 MIME 직접 찾아보기를 지원한다.
- Android 14에서 선택 영상 권한만 있는 경우 전체 목록 제한 안내와 `전체 영상 접근 허용` 재요청 버튼을 표시하고, Android 사진 선택기·직접 찾아보기는 계속 노출한다.
- 메인 알람 목록은 좁은 화면 세로 카드·하단 플로팅 내비게이션과 넓은 화면 좌측 레일·2열 카드로 표시하며, 상단에서 저장 가능한 다크·라이트 테마를 전환한다. 선택한 테마는 알람 추가·편집 화면에도 동일하게 적용한다.
- 각 카드의 알람 활성화 스위치 아래에 독립적인 미리보기 스위치를 표시한다. 로컬 미디어와 YouTube 공식 썸네일/탭 재생을 카드에서 지원하며, 미리보기 상태는 스케줄과 분리된 `homePreviewEnabled`로 저장한다.
- 알람 활성화 시 기존에 자동으로 내일로 밀린 1회성 날짜와 과거 명시 날짜는 오늘 우선으로 재계산하고, 미래에 직접 고른 날짜는 유지한다. 매일 반복은 선택 요일 없이 모든 요일을 대상으로 계산한다.
- 반복 알람 복구 시 저장된 미래 `triggerAtMillis`보다 현재 날짜의 다음 유효 시각을 우선하며, 매일 알람을 해제하면 해제한 날을 건너뛰고 내일부터 다시 예약한다.
- 공통 울림 화면의 중앙 위 스와이프 영역을 하단 동작 행보다 위쪽에 분리하고 먼저 추가해 좁은 화면에서도 버튼 탭이 제스처 영역에 가로채이지 않게 한다. `5분 스누즈`와 `알람끄기`는 중앙에서 바로 옆에 배치하며, 스누즈 후에는 종료만 중앙에 둔다.
- 스톱워치 옆 타이머 탭에서 초 단위 입력·시작·일시정지·계속·초기화를 제공하고 `SystemClock` 기준으로 화면 이동 뒤에도 진행 상태를 보존한다. 알람 카드는 설정 시각 오름차순으로 정렬한다.

## 자동 검증

- JVM 단위 테스트 110건, 실패/오류 0.
- Android lint 오류 0건. 경고 29건은 의존성 업데이트 안내, 구형 Android 썸네일 호환 API, 동기 세션 저장 의도, KAPT/KTX 제안 등이다.
- debug APK 조립 성공.
- 통합 debug APK: `outputs/routine-alarm-integrated-final-v15.13-debug.apk` (`routine-alarm-integrated-final-debug.apk`도 같은 파일로 교체).
- debug APK SHA-256: `1C3C15C88FEE13745529F137EF9086C1FFC26A7F3EA50FDEAB48C831FD31572C`.

## 남은 신뢰성 작업

- 실제 Galaxy에서 잠금 화면, Doze, 재부팅, 제조사 절전, 권한 회수 검증.
- Room 원자적 선점과 겹침 정책의 실제 Galaxy 동시 전달·프로세스 종료 경계 검증.
- 파일 접근 상실, 손상 미디어, YouTube 네트워크·자동 재생 제한·광고 포함 경험.
- TalkBack, 200% 글꼴, 색 대비 실기기 검증.

참조 방식은 Caveman의 간결한 의사결정 기록과 Ponytail의 기존 흐름 우선·최소 의존성·작은 정확한 변경 원칙을 적용했다. 제품 명세와 실제 Galaxy 검증 공백은 숨기지 않고 분리했다.

## 0.12.0 추가 계획/검증 기준

## 0.13.0 미리보기·타이머 검증 기준

- 알람 카드의 활성화와 미리보기 스위치는 독립적으로 동작하며 `homePreviewEnabled` 변경은 `scheduleRevision`을 올리거나 예약을 재생성하지 않는다.
- 로컬 카드 미리보기와 유효한 YouTube 공식 썸네일 탭 재생, 미리보기 끄기, 타이머 초 단위 입력·일시정지·계속·초기화, 화면 이동 중 진행 유지, 활성 여부와 무관한 설정 시각 정렬을 검증한다.
- 0.13.0 검증 결과: JVM 단위 테스트 93건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v13-debug.apk`와 `outputs/routine-alarm-integrated-final-debug.apk`에 복사했으며 SHA-256은 `EF01CEBAD1509FFC4ADCB0BC526D58E245302F37A205F71C01D494947FE5D925`이다.

- 잠금화면 Activity와 홈 복구 오버레이는 동일한 `AlarmOverlayController` 표면을 사용한다.
- 화면 복구·회전·표면 전환은 기존 `sessionId`와 `ringStartedAtMillis`를 유지하며, X와 스와이프는 서비스의 세션·시간 검증을 통과해야 한다.
- 자동화 검증은 `HH:mm` 포맷, 타이머 잠금/완료, 160dp 위 스와이프와 짧거나 수평 드래그 거부, 반복 스누즈와 기존 세션 유지까지 포함한다.
- 0.12.0 검증 결과: JVM 단위 테스트 90건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 성공. 하단 손잡이 deadline 카운트다운, MediaStore MIME/extension·duration, 새 시각 파일 선택 시 이전 오디오 제거, YouTube URL 상태·공식 썸네일/HTML helper, Galaxy 영상 컬렉션·권한·0건 안내, 선택 영상 전용 권한 제한 안내 helper 회귀 테스트를 포함한다. APK는 `outputs/routine-alarm-integrated-final-v12-debug.apk`와 `outputs/routine-alarm-integrated-final-debug.apk`에 복사했으며 SHA-256은 `5FB56AA4F25C187077C1B07B10BEBB29C9A2570F18E075A750A50A7B928DBE95`이다.
- 0.12.0 메인 UI 검증 결과: JVM 단위 테스트 90건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 성공. 반응형 알람 대시보드·다크/라이트 테마 전환과 시스템 바 동기화를 반영했으며 APK SHA-256은 `D0C1299BC58CB172683627245000A0B7BFB1B125C74EF2E01F475270A8270EDD`이다.
- 0.15.1 카드 렌더링 패치 결과: 카드 `VideoView` 표면을 부모 경계 안에 유지하면서 명시적인 중앙 크롭 변환으로 원본 비율과 CENTER_CROP 동작을 적용했다. `testDebugUnitTest` 93건, `lintDebug`, `assembleDebug` 성공. APK SHA-256은 `8209BB3A979215446A1BF71898619504AC84EBF1D9835E467F703EC11AE735A6`이다.
- 0.15.2 음량 점진 상승 패치 결과: 로컬 음악·오디오 영상·기본 알람 URI의 `MediaPlayer` gain을 10%에서 30초 동안 250ms 간격으로 100%까지 올리고, 재생 객체·세션 ID·generation이 일치하는 콜백만 적용하도록 했다. 해제·스누즈·선점·destroy 전에 콜백을 제거하며 기기 `STREAM_MUSIC` 초기값은 다시 설정하지 않는다. `ToneGenerator` 대체음은 고정 음량이다. `testDebugUnitTest` 96건, `lintDebug`, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.2-debug.apk`이며 SHA-256은 `E8106DC65CABCD120F93202E4AE3622478E34F188ABC20919ED802FA5A0BBB9E`이다.
- 0.15.3 내비게이션·편집 뒤로가기 패치 결과: 좁은 화면 하단 세 탭을 동일 가중치로 배치하고 시스템 인셋을 적용했으며, 넓은 화면 레일/본문도 Scaffold 인셋을 소비하도록 했다. 편집 화면 시스템 뒤로가기는 상단 뒤로와 동일하게 동작한다. `testDebugUnitTest` 96건, `lintDebug`, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.3-debug.apk`이며 SHA-256은 `2061F7D71AECC9B7D6AC9F4D279F0843A472FFA4FD4D7AE355A863CCBA5C9750`이다.
- 0.15.4 앱 표시 이름 패치 결과: Android 런처·앱 정보 표시 이름을 `LockAlarm`으로 변경하고 패키지 식별자와 알람 채널은 유지한다. `testDebugUnitTest` 96건, `lintDebug`, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.4-debug.apk`이며 SHA-256은 `E8A866D7CECBE9B3DCE6FFFAF92806375CF7DEDB44B7568229FE92388025487A`이다.
- 0.15.5 알람 종료 버튼 패치 결과: 실제 종료 버튼을 5분 스누즈와 같은 둥근 반투명 스타일과 `알람끄기` 문구로 통일하고 공통 울림 표면에 적용한다. `testDebugUnitTest` 96건, `lintDebug`, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.5-debug.apk`이며 SHA-256은 `49E1DAE7AD1008DF58AE575F198624E8AA8CA174B14B0397DD5B0F29533AABE4`이다.
- 0.15.6 화면 전환 위치 보존 패치 결과: 폴드·넓은/좁은 화면 전환 중 `SurfaceView`가 반환하는 일시적 0초를 저장하지 않고 마지막 정상 영상 위치를 세션에 보존한다. 새 표면은 같은 세션 위치에서 이어지며 서비스 음원은 계속 재생된다. `testDebugUnitTest` 96건, 실패/오류 0건, `lintDebug` 오류 0건, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.6-debug.apk`이며 SHA-256은 `BBED58A8C30170247F7C5CE95F87E7F1C66F5CB7680C333BC757077C4874A54C`이다.
- 0.15.7 화면 잠금 타이머 패치 결과: 알람별 켜짐/꺼짐을 저장하고 켜짐 지연 0~60초, 꺼짐 0초를 적용한다. Room 5→6 마이그레이션과 0초 즉시 해제 정책을 포함한다. `testDebugUnitTest` 97건, 실패/오류 0건, `lintDebug` 오류 0건, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.7-debug.apk`이며 SHA-256은 `609615DCC44308E06BCC93D160CD5D5B2FD45F11EB6F429EB8C550AC175E5A82`이다.
- 0.15.8 새 알람 우선·기기 음량 복원 패치 결과: 활성 `CLAIMED`/`FIRING`/`SNOOZED`/`SNOOZE_CLAIMED` 발생을 Room에서 `PREEMPTED`로 종결하고 새 정규 발생을 즉시 시작한다. 이전 `WAITING` 행은 재생하지 않으며, 새 occurrence의 `CLAIMED` 상태와 sessionId 제한 종료 broadcast로 오래된 전달을 차단한다. `STREAM_MUSIC`은 울림 중 목표보다 낮을 때만 2초 간격·최대 음량 5%(최소 1단계)로 상승시킨다. `testDebugUnitTest` 100건(실패/오류 0), `lintDebug` 오류 0건, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.8-debug.apk`이며 SHA-256은 `956D88A3501738096327A427F793FFB91B3DF48DA36D46166E6103810F387500`이다. Galaxy 실기기 겹침·음량·잠금화면 검증은 남아 있다.
- 0.15.9 시간 입력 날짜 보정 패치 결과: 숫자 시·분 입력 중에는 날짜를 변경하지 않고, 저장 시 오늘의 시각이 지난 경우에만 내일로 자동 보정한다. `testDebugUnitTest` 100건(실패/오류 0), `lintDebug` 오류 0건, `assembleDebug` 성공. APK는 `outputs/routine-alarm-integrated-final-v15.9-debug.apk`이며 SHA-256은 `EB028E3C7A425200413B95D932F84F06A91FB0738D096C447EA6A768A91D18AE`이다.
- 0.15.10 알람 편집 화면 테마 동기화 패치 결과: `AlarmEditorScreen` 루트가 현재 `MaterialTheme.colorScheme.background`를 직접 그려 홈에서 선택한 다크·라이트 테마가 편집 화면에도 이어진다. `testDebugUnitTest` 100건 통과, `lintDebug` 오류 0건, `assembleDebug` 성공, APK SHA-256 `DBEE9419C754FF46A7BF72E59AA333BAD1EF0E077A77436620BBFA67E74F3BD0`.
- 0.15.11 알람 활성화 날짜 우선·매일 반복 패치 결과: 홈 스위치와 편집 화면의 활성화 시 날짜 미지정 ONE_TIME은 오늘의 설정 시각이 남아 있으면 오늘, 지나면 내일로 계산하고 직접 고른 날짜는 유지한다. 별도 DAILY 반복을 추가하고 Room 6→7 마이그레이션을 연결했다. `testDebugUnitTest` 108건 통과, `lintDebug`, `assembleDebug` 성공. APK SHA-256은 `9ADDAEB688857329EDEC8DEC904CC7F6E80CA052F51376979C1E107088CA9BBA`이다.
- 0.15.12 홈 활성화·종료 버튼 보정 패치 결과: 홈 토글 활성화에서 자동 계산으로 남은 날짜와 과거 명시 날짜를 오늘 우선으로 재기준하고, 미래 명시 날짜는 유지한다. 중앙 스와이프 영역을 명시 종료·스누즈 버튼 아래에 배치해 버튼 터치 가로채기를 방지한다. `testDebugUnitTest` 110건 통과, `lintDebug`, `assembleDebug` 성공. APK SHA-256은 `2C9E53EEB9D16086A21C77F9F06D9054C9BAF2DEBD0C927C9FA46513A653F608`이다.
- 0.15.13 울림 화면 동작 행 배치 패치 결과: 위로 밀기 영역을 하단 동작 행 위쪽으로 분리하고, `5분 스누즈`·`알람끄기`를 중앙 인접 가로 행으로 배치한다. 스누즈 후에는 종료 버튼만 중앙에 둔다. `testDebugUnitTest` 110건, `lintDebug`, `assembleDebug` 성공, APK SHA-256은 `1C3C15C88FEE13745529F137EF9086C1FFC26A7F3EA50FDEAB48C831FD31572C`이다.
