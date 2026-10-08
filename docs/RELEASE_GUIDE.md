# LockAlarm GitHub 배포 및 서명키 운영

현재 버전은 **0.15.25 / 코드 40**입니다. v0.15.24의 YouTube 길이 확인 개선에 더해 반복 알람의 `내일 다시 켜기`, 10분 전 예고 알림과 이번 회차만 건너뛰기가 포함됩니다. 2026-10-08 동일 버전 교체본은 카드 높이를 190dp로 되돌리고 다음 울림 문구를 날짜·반복 표시 옆에 배치하며 `예약 취소`를 제거했습니다. 정식 APK의 실기기 알람·예고 동작은 아직 확인하지 않았으므로 GitHub에서는 Pre-release로 배포합니다.

> 정식 키는 생성됐고, 프로젝트의 `key/` 백업 폴더는 Git에서 제외됩니다. 키·백업·비밀번호는 GitHub나 채팅에 올리지 마세요. 비밀번호는 로컬 숨김 입력에만 입력합니다. 개발용 설치본과 정식 APK는 서명이 다르므로 덮어쓰기 설치를 가정하지 마세요. 앱 삭제는 로컬 알람 데이터를 지울 수 있습니다. 서명 자체가 Play Protect 경고 제거를 보장하지 않으며 보안 경고를 일괄 무시하지 마세요.

## 1. 정식 키 만들기 — 현재 키 생성 완료

현재 릴리스 키가 이미 만들어졌습니다. 기존 키를 보존해야 하므로 이 릴리스 작업에서는 `CreateKey`를 다시 실행하지 않습니다. 새 PC에서 최초 키를 만들 때만 아래 절차를 사용합니다. Windows PowerShell 7.2 이상과 JDK 17이 필요합니다.

~~~powershell
pwsh -NoProfile -File .\tools\release.ps1 -Action CreateKey
~~~

- 기본 저장 위치: %LOCALAPPDATA%\LockAlarm\signing\lockalarm-release.jks (프로젝트 밖).
- 별칭: lockalarm-release. RSA 3072, JKS 저장소, 유효기간 10000일.
- keytool이 비밀번호를 터미널에서 직접 묻습니다. 키 비밀번호 질문에서 Enter를 누르면 저장소 비밀번호를 재사용합니다.
- 기존 키가 있으면 덮어쓰지 않습니다. 실패한 경우 남은 파일도 보존하므로 재시도 전에 상태를 확인합니다.
- 생성 후 키 파일을 별도 오프라인 장소에도 백업하고 비밀번호는 비밀번호 관리자에 보관합니다. 프로젝트 안의 사본만으로는 별도 오프라인 백업이 되지 않습니다. 키나 비밀번호를 GitHub 또는 채팅에 올리지 않습니다.
- 다른 경로는 -JavaHome, -KeystorePath, -KeyAlias로 지정합니다. 키 경로는 절대 경로여야 합니다.

## 2. 검증하고 서명 APK 만들기

~~~powershell
pwsh -NoProfile -File .\tools\release.ps1 -Action Build
~~~

도구는 다음을 수행합니다.

1. 키·JDK·Android build-tools 36.0.0의 존재를 확인합니다.
2. 비밀번호를 숨김 입력으로 받아 현재 빌드 프로세스의 환경 변수에만 전달합니다. 비밀번호 파일을 만들지 않습니다.
3. verifyReleaseSigning, testReleaseUnitTest, lintRelease, assembleRelease를 실행합니다. 장기 Gradle daemon, configuration cache, build cache는 사용하지 않습니다.
4. APK 서명 검증, 개발용 인증서·debuggable 여부 검사, 패키지·버전 및 SHA256 출력을 수행합니다.
5. 추가한 환경 변수를 복원합니다. 업로드·태그·커밋은 수행하지 않습니다.

출력 원본: `app/build/outputs/apk/release/app-release.apk`. v0.15.25 동일 버전 교체 APK 파일 이름은 [`LockAlarm-v0.15.25.apk`](https://github.com/zkfks4545/LockAlarm/releases/download/v0.15.25/LockAlarm-v0.15.25.apk), SHA-256은 `387072566AE70778EBF23E347B160F5CB8408267C3A2D9347D190DA4C115FAF9`입니다. 개발용 `outputs/*-debug.apk`와 구분하고, 빌드·서명·패키지 버전 검증을 모두 마친 파일만 게시합니다.

Gradle은 LOCKALARM_STORE_FILE, LOCKALARM_STORE_PASSWORD, LOCKALARM_KEY_ALIAS, LOCKALARM_KEY_PASSWORD 환경 변수를 읽습니다. 값 자체는 소스에 넣지 않습니다. 키 정보가 없으면 릴리스 패키징을 실패 처리합니다. 단위 테스트는 키 없이 별도로 실행할 수 있습니다.

비밀번호는 빌드 프로세스 메모리에 잠시 존재합니다. 신뢰하는 로컬 PC에서만 실행하고 환경 변수·Gradle 상세 디버그 출력·프로세스 덤프를 공유하지 않습니다.

## 3. 실기기 확인

JVM·lint 성공은 아래 항목의 실제 성공을 뜻하지 않습니다.

이미 개발용 APK가 설치된 기기에서는 정식 APK로 덮어쓰기 업데이트할 수 없습니다. 특히 v0.15.24의 초기 잘못된 개발용 자산을 설치했다면 새 정식 APK로 제자리 업데이트할 수 없으며, 앱을 삭제하면 알람 데이터가 지워질 수 있습니다. Play Protect 경고와 서명 불일치 설치 실패는 구분해야 합니다. [설치 문제 확인](INSTALLATION.md)을 참고하세요.

- 정식 APK 설치와 필수 권한 안내
- 단발·반복 알람, 스누즈·해제, 재부팅 후 예약
- 잠금 화면·Doze·Galaxy 절전 상태에서 울림
- 로컬 미디어 및 YouTube 네트워크 실패 시 동작
- 밝기·음량의 시작 적용과 종료 복원
- 개발용 설치본에서 정식 설치본으로 전환 시 데이터 영향

## 4. GitHub Releases 게시 기록 및 다음 배포 절차

대상: [zkfks4545/LockAlarm](https://github.com/zkfks4545/LockAlarm).

2026-09-29부터 GitHub Pre-release를 공개하고 있습니다. 다음 릴리스에는 아래 절차를 반복합니다.

1. 릴리스에 필요한 코드·문서만 검토하고 커밋합니다. `.agent/`, `.agent-tasks/`, 키, 비밀번호, 로컬 설정 및 빌드 APK는 Git 커밋에서 제외합니다.
2. `tools/release.ps1 -Action Build`를 실행해 사용자가 비밀번호를 직접 입력합니다. 결과 APK가 `com.routinealarm.app`, 의도한 versionName/versionCode이며 `application-debuggable`이 없는지 확인합니다.
3. `apksigner verify --print-certs`로 **이전 정식 배포본과 동일한** 인증서 SHA-256 `442090DDA070A1F970CEE370A0469DE0E1A560CA0668F71FA984EF0B9DB491D8`인지 대조합니다. `CN=LockAlarm` 이름이나 “Debug가 아님”만으로는 충분하지 않습니다.
4. 검증된 `app/build/outputs/apk/release/app-release.apk`만 버전별 `LockAlarm-v<version>.apk` 자산으로 게시합니다. `outputs/*-debug.apk`는 GitHub Release에 첨부하지 않습니다. 버전 태그를 임의로 이동하거나 개인 서명키를 첨부하지 않습니다.
5. 공개 후 자산을 GitHub에서 다시 내려받아 파일 SHA-256·서명 지문·패키지 버전을 검증하고, 이를 릴리스 노트에 기록합니다. 시험 배포라면 **Pre-release** 표시와 실기기 미검증 범위를 유지합니다.

이번 공개 대상: [Pre-release `v0.15.25`](https://github.com/zkfks4545/LockAlarm/releases/tag/v0.15.25), 코드 40, 동일 버전 교체 APK SHA-256 `387072566AE70778EBF23E347B160F5CB8408267C3A2D9347D190DA4C115FAF9`. 최초 공개 APK SHA-256 `77DAC6B5F2E0CDA79781DF399FA7C9A5DD6BA9303E7F80A7871EA4420484537B`는 보존 기록입니다. 코드 40은 그대로이므로 이미 v0.15.25를 설치한 사용자는 자동 업데이트에 의존하지 말고 교체 파일을 직접 다시 내려받아 설치해야 합니다. 이전 [Pre-release `v0.15.24`](https://github.com/zkfks4545/LockAlarm/releases/tag/v0.15.24)는 2026-10-06 정식 서명 자산으로 교체했고 태그도 그 소스 커밋으로 옮겼습니다. v0.15.24 공개 APK SHA-256은 `6C25ED0D65DFD09F1612E8375C0F24042DA3D96DF2371CCA23E67BE3C8E097CD`이며, 그 이전 정식 APK의 해시 `4A923D46D49BD5CCF2D44C532A111D24D52190382FCF521B9F8CE20974ABDA3E`도 보존합니다.

Pre-release는 지금 내려받을 수 있는 공개 시험판입니다. 실기기 검증을 끝내기 전에는 안정판으로 표시하지 않습니다. 검증 후 같은 릴리스의 Pre-release 표시를 해제해 안정판으로 전환할 수 있습니다. 이후 버전의 공개 게시 전에는 게시 범위와 테스트 결과를 다시 확인합니다.

## 나중에 Play에 배포할 때

여러 배포 경로에서 같은 앱 서명키를 쓰려면 Play App Signing 등록 시 Google 자동 생성 키 대신 **사용자 소유의 동일한 앱 서명키를 제공**하는 경로를 검토합니다. 업로드 키와 최종 APK의 앱 서명키는 역할이 다릅니다. [Android 공식 앱 서명 안내](https://developer.android.com/studio/publish/app-signing)

Play 계정·테스트 정책·비용은 실제 등록 시 최신 공식 안내로 확인합니다. 지금은 계정 생성, 결제, AAB 업로드 및 자동 업데이트 구현을 진행하지 않습니다.

## 확인된 기준선과 보존 기록

- 기존 APK: com.routinealarm.app, 0.15.22, 코드 37, 최소 SDK 26, 타깃 SDK 36, application-debuggable.
- 기존 APK SHA256: 765F3148263B525F6940A28D92C9B9EB88028C8151471727955DFE19F5FD064F.
- 기존 공개 인증서 SHA256: 1e5c10df0df1e6147ea4a77377de3e0d33486899419fde5cd6fcfff17cf4e7e4, 주체 Android Debug.
- 정식 APK (0.15.22): `LockAlarm-v0.15.22.apk`, 8,772,356 bytes, 서명 인증서 주체 `CN=LockAlarm`, APK SHA256 `CD63A91FB6D4FA5A84353489957FFE14B7720CD3FFE8442D100BD9D83FCD8270`.
- 정식 APK (0.15.23): `LockAlarm-v0.15.23.apk`, 8,772,356 bytes, 서명 인증서 주체 `CN=LockAlarm`, APK SHA256 `8127F3C71777EA667CD6334007406C1168D1F4D632E28095BD21088B7CE9A9E4`.
- 정식 APK (0.15.25 동일 버전 교체): `LockAlarm-v0.15.25.apk`, SHA-256 `387072566AE70778EBF23E347B160F5CB8408267C3A2D9347D190DA4C115FAF9`, 버전 코드 40, 서명 인증서 주체 `CN=LockAlarm`.
- 최초 공개 APK (0.15.25, 교체 전 보존 기록): SHA-256 `77DAC6B5F2E0CDA79781DF399FA7C9A5DD6BA9303E7F80A7871EA4420484537B`.
- 정식 공개 인증서 SHA256 (모든 정식 릴리스 불변): `442090DDA070A1F970CEE370A0469DE0E1A560CA0668F71FA984EF0B9DB491D8` (`CN=LockAlarm`).
- 개발용 디버그 인증서 SHA256 (릴리스 에셋 배포 엄격 금지): `1E5C10DF0DF1E6147EA4A77377DE3E0D33486899419FDE5CD6FCFFF17CF4E7E4` (`CN=Android Debug`).
- GitHub 배포 대상 저장소: [zkfks4545/LockAlarm](https://github.com/zkfks4545/LockAlarm).

### 서명 불일치 방지 및 릴리즈 에셋 운영 불변 원칙

기존 사용자가 데이터 유실 없이 앱을 업데이트하려면, GitHub Releases에 등록되는 배포 APK는 **반드시 `CN=LockAlarm` 정식 키로 서명된 `LockAlarm-v<version>.apk`**여야 합니다.
개발용 디버그 키(`CN=Android Debug`)로 서명된 APK를 GitHub Releases에 올릴 경우 Android OS가 서명 불일치(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)로 설치를 영구 거부하므로, **GitHub 릴리즈 에셋에는 디버그 APK를 절대 업로드하지 않습니다.**
정식 릴리즈 빌드는 반드시 `tools/release.ps1 -Action Build`를 통해 로컬에서 키 비밀번호를 입력받아 생성합니다.


### 2026-09-29 로컬 정식 빌드 검증

- `tools/release.ps1 -Action Build` — `verifyReleaseSigning`, `testReleaseUnitTest`, `lintRelease`, `assembleRelease` 성공.
- `:app:testReleaseUnitTest` — 146건 통과, 실패 0, 오류 0, 건너뜀 0.
- `:app:lintRelease` — 성공, 오류 0건, 경고 43건, 힌트 8건. 경고가 없다는 뜻은 아닙니다.
- `:app:assembleRelease` — 서명된 APK 생성 성공.
- `apksigner verify --print-certs` — 서명 확인 성공, 인증서 `CN=LockAlarm` 및 위 SHA-256과 일치.
- `aapt dump badging` — 패키지 `com.routinealarm.app`, versionName `0.15.22`, versionCode `37` 확인. 빌드 도구가 디버그 플래그를 찾지 못함.
- `tools/tests/release-safety.Tests.ps1` — 키 경로·기존 파일 보호 등 5개 안전성 검사 통과.
- 실제 Galaxy·잠금 화면·Doze·재부팅·OEM 절전 동작은 아직 테스트하지 않았습니다. GitHub 공개 APK 자산을 직접 내려받아 SHA-256과 서명 인증서를 확인했고 로컬 릴리스 APK와 일치합니다. 이는 기기 설치 성공을 뜻하지 않습니다.

검증 명령 (JAVA_HOME은 JDK 17, ANDROID_HOME은 로컬 Android SDK로 설정):

~~~powershell
.\gradlew.bat --no-daemon --no-configuration-cache --offline :app:testReleaseUnitTest :app:lintRelease
.\gradlew.bat --no-daemon --no-configuration-cache --offline :app:verifyReleaseSigning
.\gradlew.bat --no-daemon --no-configuration-cache --offline :app:assembleRelease
pwsh -NoProfile -File .\tools\tests\release-safety.Tests.ps1
~~~

위의 `verifyReleaseSigning` 및 `assembleRelease` 단독 명령은 비밀번호 환경 변수가 없는 셸에서 실행하면 의도적으로 차단됩니다. 이는 최초 키 누락 사전 점검 기록이며, 2026-09-29에는 `tools/release.ps1 -Action Build`에 실제 키를 숨김 입력해 서명 성공 경로와 APK 검증을 완료했습니다. 실기기 검증은 별도 단계입니다.
