# 현재 구현과 명세 간 기준선

이 문서는 Living Spec을 도입한 시점의 프로토타입 상태를 기록한다. 제품 의도는 `spec.md`가 우선하며 이 파일은 구현 계획 수립 시 유지·수정·삭제 범위를 판단하는 참고 자료다.

## 이미 연결된 세로 슬라이스

- Kotlin + Jetpack Compose 단일 `:app` 프로젝트
- Room 기반 복수 알람과 정확 알람 예약
- 잠금 화면 Activity를 포함한 고우선 알람 알림
- 로컬 재생용 포그라운드 서비스
- 밝기 모드·밝기·미디어 음량 스냅샷과 복원/유지 정책
- 이미지/GIF/영상 및 별도 로컬 음악 문서 선택과 영속 URI 접근
- MediaStore 기반 이미지·GIF·WebP·영상·음악 5개 탭, 최신 썸네일·음악 길이·미리듣기 목록
- 미디어 최근순·이름순 정렬, 파일명 검색, 지원 MIME 필터와 Android 사진 선택기·직접 찾아보기 보조 경로
- MIME 누락·일반 MIME 파일의 확장자 기반 미디어 분류와 미지원 확장자 제외
- MIME 누락·공통/제조사 MIME MediaStore 영상의 확장자 복구 분류, duration 없는 영상 목록 포함과 음악 duration 필터 유지
- Galaxy 영상 조회의 `Video`·외부 `Files` 컬렉션 보조 조회, size/IS_PENDING SQL 필터 제거, 비정상 MIME·날짜·duration 안전 처리와 Android 14 선택 영상 권한 안내
- Android 14 선택 영상 권한만 있는 경우 전체 목록 제한 문구와 전체 영상 접근 재요청 버튼을 표시하고, 사진 선택기·직접 찾아보기는 유지
- 메인 알람 목록은 반응형 카드 대시보드(좁은 화면 세로 카드·하단 플로팅 내비게이션, 넓은 화면 좌측 레일·2열 카드)로 표시하고 다크·라이트 테마 전환을 저장
- 각 알람 카드에 알람 활성화와 분리된 `미리보기` 스위치를 제공하고 `homePreviewEnabled`를 Room에 저장한다. 로컬 미디어는 카드에서 음소거 재생하며 홈 YouTube는 공식 썸네일만 표시한다. YouTube 공식 IFrame은 실제 알람 울림 화면에서만 사용한다.
- 홈 알람 카드는 활성 여부를 우선하지 않고 `localTimeMinutes`, `id` 오름차순으로 정렬한다.
- 스톱워치 옆에 초 단위 입력·시작·일시정지·계속·초기화를 제공하는 타이머 탭을 추가하고 `SystemClock` 기반 남은 시간으로 화면 이동 후에도 진행을 유지한다.
- 앱 시작 필수 접근 설정 화면, 권한 완료 전 알람 편집·테스트 차단, 기존 목록 열람 전용 진입
- 선택한 시각 파일명·실제 미리보기와 음악 파일명·미리듣기 UI
- 알람 편집 화면의 YouTube URL 공식 썸네일·탭 재생·미리보기 닫기와 빈/지원 불가 URL 안내
- 영상 자체 오디오·별도 로컬 음악·기기 기본 대체음의 포그라운드 반복 재생
- 별도 음악 파일 → 영상 오디오 → 기기 기본 알람음 순서의 자동 소리 결정
- 우측 상단 상태 표시 전용 원형 진행률·완료 표시와 완료 뒤 하단 X/위 스와이프 해제 지연
- YouTube URL 검증과 공식 IFrame API 기반 앱 내부 임베디드 재생
- 숫자 직접 입력 시각, 오늘 지난 시각의 내일 자동 보정, 5·10·30·60초 빠른 테스트와 1~3600초 직접 입력
- 정확 알람 Broadcast 전달, 포그라운드 서비스, full-screen intent와 `TYPE_APPLICATION_OVERLAY`를 결합한 즉시 전면 표시
- 영상 회전 메타데이터를 포함한 실제 표시 규격 감지, 가로·세로 화면 전환, 원본 비율을 유지하는 화면 내 최대 크기 전체화면 영상
- 영상에 전체 화면을 먼저 제공하고 이름·스누즈·원형 종료 조작부를 남는 여백 또는 영상 가장자리 위에 겹치는 미디어 우선 레이아웃
- 뒤로가기 무시, 홈·최근 앱·작업 제거 시 서비스 오버레이 복구, 화면 켜짐·잠금 해제 시 활성 알람 화면 재표시
- 종료 지연 중 로컬·YouTube 미디어 전체 입력 차단과 최초 1회 스누즈 예외, 완료 뒤 입력 차단막 제거·X 활성화
- YouTube 일반 가로·Shorts 세로·로컬 실제 비율 기반 초기 방향과 준비 완료 뒤 전체 센서 회전
- 방향·화면 크기·폴드 구성 변경 시 Activity 비재생성, 공통 재생 표면의 자동 재측정
- 잠금 Activity·오버레이의 단일 네이티브 UI 팩토리와 동일한 반투명 스누즈·원형 타이머·X 디자인
- 0.12.0 공통 울림 표면의 상단 `HH:mm`/알람명, 우측 상단 진행 타이머, 하단 잠금 손잡이와 완료 후 좌측 5분 스누즈·우측 큰 X·160dp 위 스와이프 종료
- 타이머 전 전체 입력 차단과 서비스 스누즈 시간 검증, 타이머 완료 후 수직 위 스와이프 정책 단위 테스트
- 세션별 로컬·YouTube 위치와 단일 표면 소유권 인계, 화면 복구 시 중복 재생 중지·이어보기
- 필수 접근 완료 전 추가·편집·테스트 차단, 누락 목록 표시와 복귀 즉시 재검사
- 앱 ID 기반 HTTPS Referer·origin을 제공하는 YouTube WebView와 임베드 금지/비공개/식별 실패 구분 안내
- 재부팅 및 정확 알람 접근 재승인 후 미래 알람 재등록
- Gradle 빌드, Android lint, 알람 시각 계산 단위 테스트
- 0.12.0 검증: JVM 단위 테스트 78건, `lintDebug`, `assembleDebug` 통과. 하단 잠금 해제 카운트다운 회귀 테스트를 포함하며 APK SHA-256은 `12CFDF1F462B3A570892CDD24E0D346C9A6445DAF6F0D15F710E7E20B566AD39`이다.
- 0.12.0 추가 검증: JVM 단위 테스트 83건, `lintDebug`, `assembleDebug` 통과. MediaStore MIME/extension·duration 회귀와 새 시각 파일 선택 시 이전 오디오 제거 helper 테스트를 포함하며 APK SHA-256은 `7DC17AE0243192742D0134FD8D62D81B4389BC0E83DAFC31AD7BFC022B9FFCD1`이다.
- 0.12.0 최신 검증: JVM 단위 테스트 86건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과. YouTube URL 상태·공식 썸네일 URL/HTML helper 회귀 테스트를 포함하며 APK SHA-256은 `8D56BB05F55A2E223200D082361B3AA310C8ECA45015AC4CCFBE8116A566C3C4`이다.
- 0.12.0 최신 검증: JVM 단위 테스트 88건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과. Galaxy 영상 컬렉션 보조 조회·선택 권한·0건 안내 helper 회귀 테스트를 포함하며 APK SHA-256은 `ECDECDF2C6C85C36C6FBB95CE96289CDAABEC3F4DB7B1DFADB433C7BF1A50EAC`이다.
- 0.12.0 최신 검증: JVM 단위 테스트 90건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과. 선택 영상 전용 권한 안내·전체 접근 상태 helper 회귀 테스트를 포함하며 APK SHA-256은 `5FB56AA4F25C187077C1B07B10BEBB29C9A2570F18E075A750A50A7B928DBE95`이다.
- 0.12.0 메인 UI 검증: JVM 단위 테스트 90건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과. 반응형 알람 카드·다크/라이트 테마·시스템 바 전환을 반영했으며 APK SHA-256은 `D0C1299BC58CB172683627245000A0B7BFB1B125C74EF2E01F475270A8270EDD`이다.
- 0.15.1 카드 경계·YouTube 썸네일·시작 안정성 검증: 상단·하단 카드가 모두 음소거 로컬 `VideoView`를 실제 재생하고 부모보다 크게 측정하지 않아 각 190dp 카드 경계 안에 표시된다. 명시적인 중앙 크롭 변환으로 원본 비율을 유지하며 비율 차이로 넘치는 부분만 잘라낸다. 권한 설정 직후 읽기 권한이 끊긴 로컬 URI는 poster/안내 화면으로 대체한다. 홈 YouTube 카드는 공식 썸네일만 표시한다. JVM 단위 테스트 93건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과, APK SHA-256은 `8209BB3A979215446A1BF71898619504AC84EBF1D9835E467F703EC11AE735A6`이다.
- 0.15.2 알람 음량 점진 상승 검증: 로컬 음악·오디오 영상·기본 알람 URI의 `MediaPlayer` 개별 gain을 10%에서 30초 동안 250ms 간격으로 100%까지 올린다. 현재 재생 객체·세션 ID·generation을 확인하는 콜백만 적용하고 해제·스누즈·선점·destroy 전에 제거한다. 기기 `STREAM_MUSIC` 초기값은 다시 설정하지 않으며 `ToneGenerator` 대체음은 고정 음량이다. JVM 단위 테스트 96건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과, APK는 `outputs/routine-alarm-integrated-final-v15.2-debug.apk`, SHA-256은 `E8106DC65CABCD120F93202E4AE3622478E34F188ABC20919ED802FA5A0BBB9E`이다. 1.0.0 전까지 APK 파일명은 `v15.2`처럼 `0.`을 뺀 형식을 사용한다.
- 0.15.3 내비게이션·편집 뒤로가기 보정: 좁은 화면 하단의 알람·스톱워치·타이머를 동일 가중치로 배치하고 시스템 내비게이션 인셋을 적용했다. 넓은 화면 레일과 본문에도 Scaffold 인셋을 적용했으며, 알람 편집 화면의 시스템 뒤로가기를 상단 뒤로 동작과 연결했다. JVM 단위 테스트 96건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과, APK는 `outputs/routine-alarm-integrated-final-v15.3-debug.apk`, SHA-256은 `2061F7D71AECC9B7D6AC9F4D279F0843A472FFA4FD4D7AE355A863CCBA5C9750`이다.
- 0.15.4 앱 표시 이름 변경: Android 런처와 앱 정보 화면의 표시 이름을 `LockAlarm`으로 변경하고 패키지 식별자·알람 채널은 유지한다. JVM 단위 테스트 96건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과, APK는 `outputs/routine-alarm-integrated-final-v15.4-debug.apk`, SHA-256은 `E8A866D7CECBE9B3DCE6FFFAF92806375CF7DEDB44B7568229FE92388025487A`이다.
- 0.15.5 알람 종료 버튼 스타일 통일: 울림 화면의 실제 종료 버튼을 5분 스누즈와 같은 둥근 반투명 버튼으로 바꾸고 `알람끄기` 문구를 표시한다. 잠금화면 Activity와 홈 복구 오버레이의 공통 표면에 적용한다. JVM 단위 테스트 96건(`testDebugUnitTest`), `lintDebug`, `assembleDebug` 통과, APK는 `outputs/routine-alarm-integrated-final-v15.5-debug.apk`, SHA-256은 `49E1DAE7AD1008DF58AE575F198624E8AA8CA174B14B0397DD5B0F29533AABE4`이다.
- 0.15.6 화면 크기·폴드 전환 위치 보존: 표면 재생성 중 보고되는 일시적인 0초를 무시하고 마지막 정상 로컬 영상 위치를 같은 세션에 전달한다. JVM 단위 테스트 96건(`testDebugUnitTest`, 실패/오류 0), `lintDebug` 오류 0건, `assembleDebug` 성공, APK는 `outputs/routine-alarm-integrated-final-v15.6-debug.apk`, SHA-256은 `BBED58A8C30170247F7C5CE95F87E7F1C66F5CB7680C333BC757077C4874A54C`이다.
- 0.15.7 화면 잠금 타이머 켜짐/꺼짐: 타이머를 알람별로 켜거나 끌 수 있고, 켜짐 지연은 0~60초, 꺼짐은 0초로 저장한다. 0초에서는 알람 시작 즉시 해제·스누즈·스와이프가 가능하도록 한다. JVM 단위 테스트 97건(`testDebugUnitTest`, 실패/오류 0), `lintDebug` 오류 0건, `assembleDebug` 성공, APK는 `outputs/routine-alarm-integrated-final-v15.7-debug.apk`, SHA-256은 `609615DCC44308E06BCC93D160CD5D5B2FD45F11EB6F429EB8C550AC175E5A82`이다.
- 최초 울림에서 즉시 사용할 수 있는 고정 5분·발생당 1회 스누즈
- 스누즈 중 같은 세션과 최초 기기 상태 스냅샷 유지, 재울림 시 초기값 미재적용
- 스누즈 대기 알림과 최종 해제, 미래 스누즈의 재부팅 후 재등록
- `ONE_TIME`/선택 요일/포함·제외 날짜의 순수 다음 발생 계산기와 경계 단위 테스트
- 실행 중 예약 취소의 재생·세션·알림·복원 일괄 종료
- 동일 부팅 중 기기 시각 변경에도 경과 5분을 유지하기 위한 elapsed time 재고정
- FIRING 중 서비스 프로세스 종료 후 영속 세션 기반 재시작
- 1회성/요일 반복/포함·제외 날짜를 편집하는 Compose 목록·편집 화면
- 알람별 다음 정규 발생 하나의 예약, 전달 뒤 다음 반복 발생 재예약
- 부팅·앱 교체·시간·시간대·정확 알람 접근 변경 후 활성 알람 재등록
- 실행·스누즈·해제·오류 행동 로그 비저장
- 최근 콘텐츠 30개 중복 제거 저장, 로컬 파일 열람·미리보기, YouTube URL 복사와 최근 목록 전체 삭제
- `scheduleRevision`, `occurrenceId`, `sessionId` 전달과 Room 트랜잭션 기반 중복·오래된 전달 선점
- 현재 울림 중 새 정규 발생의 FIFO 대기와 최종 해제 직후 실행
- 스누즈 대기 중 새 정규 발생 우선, 기존 스누즈 세션 종료·복원
- 정확 알람의 Broadcast PendingIntent에서 세션을 시작하고 서비스 소유 전체화면 오버레이를 즉시 표시하는 경로

## 현재 MVP 결정

- 알람 정의와 최근 콘텐츠는 앱 전용 Room DB에만 저장하고 계정·서버·동기화를 사용하지 않는다.
- 활성 실행 세션은 즉시 복구가 필요한 작은 상태이므로 `SharedPreferences`에 별도로 둔다.
- 자동 밝기를 수동 모드로 전환한 뒤 목표 밝기를 적용한다.
- 영상 자체 오디오 또는 사용자가 선택한 로컬 음악을 미디어 스트림으로 재생하고, 접근·재생 실패 때 기기 기본 알람음으로 대체한다.
- YouTube 모드는 공식 임베디드 플레이어로 재생하며 다운로드·추출·광고 차단을 구현하지 않는다. 해제 알림과 별도 알람 생명주기 서비스는 유지한다.
- 지나간 알람은 재부팅 후 비활성화한다.
- 사용자 편집·활성 변경은 `scheduleRevision`을 증가시키고 자동 반복 진행은 같은 revision을 유지한다.
- 이미 울리는 알람은 선점하지 않으며 새 정규 발생을 대기시킨다. 스누즈 대기 중에는 새 정규 알람이 우선한다.
- 화면 복구·회전·Activity/오버레이 표면 전환은 기존 `sessionId`와 `ringStartedAtMillis`를 유지한다. X와 위 스와이프는 서비스의 세션·시간 검증을 통과해야만 최종 해제된다.
- 0.15.6 화면 크기·폴드 전환 중 `SurfaceView`가 반환하는 일시적인 0초 위치를 저장하지 않고 마지막 정상 로컬 영상 위치를 유지한다. 새 표면은 같은 세션의 안정적인 위치에서 이어지며 음원 서비스·음량 램프·타이머는 재시작하지 않는다.

## 현재 검증 공백

- 실제 Galaxy 기기에서 화면 잠금, Doze, 제조사 절전 정책 검증
- 복수 Android 버전과 full-screen intent 정책 차이 검증
- Room 전달 선점과 겹침 정책은 구현됐으나 실제 기기에서 동시 전달·프로세스 종료 경계 검증은 아직 없다.
- 프로세스 강제 종료 중 활성 세션 복구
- START_STICKY 기반 단일 세션 복구는 연결됐으나 OEM 강제 종료와 재부팅 실제 기기 증거는 없음
- 파일 접근 상실과 손상된 GIF/영상 대체 동작
- YouTube 네트워크 실패·자동 재생 제한·광고가 포함된 실제 임베디드 재생 경험
- TalkBack, 200% 글꼴, 색 대비 검증
- 손상된 영상 오디오·로컬 음악의 대체음 전환 경험
