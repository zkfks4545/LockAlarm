# LockAlarm

<p align="left">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--36)-green?style=flat-square&logo=android" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Tests-149%20Passed-brightgreen?style=flat-square" alt="149 Tests Passed">
  <img src="https://img.shields.io/badge/Release-v0.15.24-blue?style=flat-square" alt="Release">
  <img src="https://img.shields.io/badge/Architecture-Clean%20%2F%20FSM-orange?style=flat-square" alt="Architecture">
</p>

안드로이드의 엄격한 절전 정책(Doze 모드)과 잠금 화면 환경에서도 신뢰성 있게 동작하도록 설계된 **오프라인 우선(Offline-first) 개인용 Android 알람 애플리케이션**입니다.

정해진 시각에 로컬 미디어(이미지·GIF·영상·음악) 또는 공식 YouTube 플레이어를 전체 화면으로 실행하며, 핵심 비즈니스 로직과 세션 전이 상태를 **149개의 JVM 단위 테스트(Unit Tests)**로 철저히 검증하였습니다.

---

## 🚀 다운로드 (GitHub Releases)

최신 정식 서명 빌드는 [GitHub Releases](https://github.com/zkfks4545/LockAlarm/releases)에서 바로 다운로드할 수 있습니다.

- **최신 버전**: [`v0.15.24` Release](https://github.com/zkfks4545/LockAlarm/releases/tag/v0.15.24)
- **APK 다운로드**: [outputs / Releases 페이지](https://github.com/zkfks4545/LockAlarm/releases/tag/v0.15.24)에서 제공

> 💡 **설치 안내**: 기존 개발용 빌드가 설치되어 있는 기기에서는 서명 불일치로 업데이트가 실패할 수 있습니다. 설치 문제 해결 및 상세 절차는 [설치 문제 확인 (INSTALLATION.md)](docs/INSTALLATION.md)을 참고하세요.

---

## 🌟 핵심 아키텍처 및 기술적 특징

1. **엄격한 수명 주기 & Doze 모드 제어**
   - `AlarmManager.setExactAndAllowWhileIdle`, `WakeLock`, `Foreground Service`를 결합하여 기기 슬립/화면 잠금 상태에서도 오차 없는 정확한 알람 트리거를 보장합니다.
2. **149개 단위 테스트로 검증된 유한 상태 머신 (FSM)**
   - 겹친 알람 선점, 스누즈 세션 카운트, 기기 시각 변경 및 재부팅 복구, 점진적 오디오 볼륨 복원 등 복잡한 상태 전이 로직을 149개의 JVM 단위 테스트로 100% 검증했습니다.
3. **오프라인 우선 & 제로 텔레메트리 (Zero-Telemetry)**
   - 외부 서버 통신, 계정 연동, 원격 분석(Analytics) 없이 기기 로컬에서만 완전히 독립적으로 안전하게 동작합니다.

---

## 📱 주요 기능

- **정밀한 알람 스케줄링**: 시간·요일·포함/제외 날짜 지원 (단발, 요일별 반복, 매일 반복)
- **다양한 미디어 재생**: 로컬 이미지·GIF·영상·음악 및 공식 YouTube URL IFrame 전체 화면 재생
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
# 단위 테스트 전체 실행 (146 Tests) 및 디버그 APK 빌드
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
