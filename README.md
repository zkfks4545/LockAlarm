# LockAlarm

<p align="left">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--36)-green?style=flat-square&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Tests-162%20Passed-brightgreen?style=flat-square" alt="162 Tests Passed">
  <img src="https://img.shields.io/badge/Release-v0.15.25-blue?style=flat-square" alt="Release">
  <img src="https://img.shields.io/badge/Architecture-Clean%20%2F%20FSM-orange?style=flat-square" alt="Architecture">
</p>

안드로이드의 엄격한 절전 정책(Doze 모드)과 잠금 화면 환경에서도 신뢰성 있게 동작하도록 설계된 **오프라인 우선(Offline-first) 개인용 Android 알람 애플리케이션**입니다.

정해진 시각에 로컬 미디어(이미지·GIF·영상·음악) 또는 공식 YouTube 플레이어를 전체 화면으로 실행합니다. 핵심 로직에 대한 **162개의 JVM 단위 테스트**가 통과했지만, 이는 실기기에서의 울림을 증명하지 않습니다.

---

## 🚀 다운로드 (GitHub Releases)

최신 정식 서명 APK는 GitHub **Pre-release**에서 다운로드할 수 있습니다. 중요한 알람에 사용하기 전에는 본인 기기에서 직접 시험하세요.

- **최신 버전**: [`v0.15.25` Release](https://github.com/zkfks4545/LockAlarm/releases/tag/v0.15.25)
- **APK 다운로드**: [LockAlarm-v0.15.25.apk](https://github.com/zkfks4545/LockAlarm/releases/download/v0.15.25/LockAlarm-v0.15.25.apk)
- **APK SHA-256**: `387072566AE70778EBF23E347B160F5CB8408267C3A2D9347D190DA4C115FAF9`
- **서명 인증서 SHA-256**: `442090DDA070A1F970CEE370A0469DE0E1A560CA0668F71FA984EF0B9DB491D8` (v0.15.23 정식 배포본과 동일)

> ⚠️ **2026-10-08 동일 버전 교체**: 카드 크기를 원래대로 되돌리고 내일 재개 문구를 날짜·반복 표시 옆으로 옮기며 `예약 취소` 버튼을 제거한 APK로 v0.15.25 자산을 교체했습니다. 버전 코드가 기존 v0.15.25와 같은 40이므로 이전 파일을 받았다면 새 해시를 확인해 직접 다시 다운로드하세요. 개발용 APK가 설치된 기기는 정식 서명 APK로 덮어쓰기 업데이트할 수 없습니다. 앱 삭제 시 알람 데이터가 지워질 수 있으니 [설치 문제 확인](docs/INSTALLATION.md)을 참고하세요. 실기기 동작은 아직 검증하지 않았습니다.

---

## 🌟 핵심 아키텍처 및 기술적 특징

1. **엄격한 수명 주기 & Doze 모드 제어**
   - `AlarmManager.setExactAndAllowWhileIdle`, `WakeLock`, `Foreground Service`를 사용해 기기 슬립·화면 잠금 상황에 대응하도록 설계했습니다. 실제 울림은 기기·권한·절전 설정에 따라 확인해야 합니다.
2. **162개 단위 테스트로 검증된 유한 상태 머신 (FSM)**
   - 겹친 알람 선점, 스누즈 세션 카운트, 기기 시각 변경 및 재부팅 복구, 점진적 오디오 볼륨 복원 등 상태 전이 로직을 JVM 단위 테스트로 검사했습니다.
3. **오프라인 우선 & 제로 텔레메트리 (Zero-Telemetry)**
   - 자체 서버, 계정 연동, 원격 분석(Analytics)은 사용하지 않습니다. 로컬 알람 기능은 오프라인 우선이며, YouTube 재생에는 네트워크가 필요합니다.

---

## 📱 주요 기능

- **정밀한 알람 스케줄링**: 시간·요일·포함/제외 날짜 지원 (단발, 요일별 반복, 매일 반복)
- **반복 알람 재개·예고**: 꺼진 반복 알람을 내일부터 다시 켜기, 모든 활성 알람의 10분 전 조용한 예고와 이번 회차만 건너뛰기
- **다양한 미디어 재생**: 로컬 이미지·GIF·영상·음악 및 공식 YouTube URL IFrame 전체 화면 재생
- **YouTube 영상 길이 기반 잠금 타이머**: 확인 전 최대 5분, 수동 미리보기 1회 재생 후 영상 길이를 상한에 반영
- **인터랙티브 컨트롤**: 횟수 제한 없는 5분 스누즈, 위로 밀어 종료 제스처, 초 단위 타이머
- **지능형 세션 관리**: 겹친 알람 발생 시 새 알람이 현재 울림/스누즈 세션을 자동 선점
- **반응형 테마 & 레이아웃**: 다크/라이트 테마 유지, 모바일 하단 내비게이션 및 태블릿 2열 카드 레이아웃 지원
- **최근 기록 관리**: 자주 사용하는 로컬 파일 및 YouTube 주소 최대 30개 기기 내 보관

---

## 🛠️ 요구 사항 & 빌드

| 항목 | 기준 |
| --- | --- |
| Android Studio | 최신 안정 버전 (Koala / Ladybug 권장) |
| JDK | JDK 17 |
| Android SDK | SDK 36 (minSdk 26, targetSdk 36) |

### 빌드 및 테스트 실행

```bash
# 단위 테스트 전체 실행 (162 Tests) 및 디버그 APK 빌드
./gradlew test assembleDebug      # macOS / Linux
gradlew.bat test assembleDebug    # Windows
```

---

## 📚 프로젝트 문서

본 프로젝트는 제품 계약과 아키텍처를 Living Spec 문서 체계로 체계적으로 관리합니다.

| 문서 | 설명 |
| --- | --- |
| [사용 안내](docs/USAGE.md) | 알람 설정, 미디어 연결, 제스처 사용법 |
| [설치 문제 확인](docs/INSTALLATION.md) | 권한 허용, 배터리 최적화 예외, 서명 충돌 해결 |
| [배포 안내](docs/RELEASE_GUIDE.md) | 정식 RSA 3072 서명키 생성 및 GitHub Releases 절차 |
| [버전·빌드 상세 기록](docs/version_log.md) | 개발용 산출물 및 이전 빌드 상세 이력 |
| [제품 계약 (Living Spec)](specs/001-core-alarm/spec.md) | 핵심 기능 범위와 사용자 경험(UX) 계약 |
| [구현 계획](specs/001-core-alarm/plan.md) | 컴포넌트 아키텍처 및 구현 방향성 |
| [데이터 모델](specs/001-core-alarm/data-model.md) | 로컬 저장소 스키마 및 세션 식별자 정의 |
| [변경 내역](CHANGELOG.md) | 버전별 상세 릴리즈 노트 |
| [전체 문서 색인](docs/README.md) | 개발 및 운영 문서 전체 목록 |

---

## ⚖️ 범위와 제한

LockAlarm은 100% 개인 사용과 프라이버시 보호를 전제로 합니다. 계정 시스템, 클라우드 동기화, 사용자 행동 추적 로그는 일절 포함하지 않습니다. 기기 제조사별 배터리 절전 정책에 따라 백그라운드 제한이 다를 수 있으므로 [설치 안내](docs/INSTALLATION.md)의 배터리 최적화 해제 설정을 권장합니다.
