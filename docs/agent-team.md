# Routine Alarm Agent Team

이 프로젝트는 [Agency Agents](https://github.com/msitarzewski/agency-agents)의 전문 역할·명시적 산출물·증거 기반 검수 방식을 가볍게 적용한다. 역할은 회의를 늘리기 위한 조직도가 아니라, 제품 결정·Android 제약·검증 증거가 서로 섞이지 않게 하는 책임 경계다.

## 역할

### Product Manager

- 사용자 결과와 비목표를 먼저 정의한다.
- 범위 변경을 Living Spec, 수락 시나리오, 성공 기준에 반영한다.
- 모호한 요구를 구현자가 추측하지 않도록 고영향 결정 목록을 유지한다.
- 현재 기준 문서: `specs/001-core-alarm/spec.md`와 `README.md`.

### Android Mobile Architect

- Kotlin/Compose, AlarmManager, 포그라운드 서비스, 저장소와 재부팅 복구 경계를 설계한다.
- 가장 단순한 신뢰성 모델을 선택하고 트레이드오프를 계획과 ADR에 남긴다.
- 현재 기준 문서: `specs/001-core-alarm/plan.md`와 `specs/001-core-alarm/data-model.md`.

### Reality Checker

- 기본 판정은 `NEEDS WORK`이며 빌드 성공만으로 기능 완료를 인정하지 않는다.
- 명세의 Given/When/Then, 자동화 결과, 실제 Galaxy 증거를 연결한다.
- 잘못된 알람, 누락, 중복 울림, 다른 세션 상태 복원은 즉시 실패로 분류한다.
- 현재 기준 문서: `specs/001-core-alarm/current-state.md`, 테스트와 `CHANGELOG.md` 검증 기록.

### Primary Implementer

- 위 세 역할의 결론을 Spec Kit 문서와 코드로 통합한다.
- 미결정 정책을 조용히 가정하지 않고, 구현 가능한 세로 슬라이스와 차단 범위를 구분한다.
- 변경 후 `lint`, 단위 테스트, APK 조립을 실행하고 Reality Checker에게 다시 넘긴다.

## 작업 루프

```text
사용자 결정
  → Product Manager: 범위·수락 기준
  → Android Architect: 상태·저장·예약 설계
  → Primary Implementer: 명세·코드·자동화
  → Reality Checker: 증거 검수
       ↘ NEEDS WORK이면 수정 후 재검수(최대 3회)
       ↘ 통과하면 실제 Galaxy 검증 후보
```

## 이번 결정

- D 범위는 복수 알람, `ONE_TIME`, 선택 요일 `WEEKLY`, 명시적 포함/제외 날짜다.
- 스누즈는 최초 울림에서 즉시 사용할 수 있고, 한 발생 건에 1회, 5분 고정이다.
- 스누즈는 같은 세션을 유지한다. 최초 시작의 기기 스냅샷과 초기 밝기·음량 적용은 반복하지 않고, 최종 해제에서만 복원 정책을 적용한다.
- 다른 알람이 울림 또는 스누즈와 겹치는 정책은 아직 고영향 미결정 사항이므로, 복수 런타임 세션을 완성하기 전에 확정한다.
