# Codex × Antigravity 업무데스크 운영 안내

작성: 2026-09-29. Antigravity의 협업 명세 1.1.0과 한글 교환소를 보존하고 업무데스크 연결 규칙을 1.2.0에 추가했습니다. 알람 앱 자체에는 서버나 계정 기능을 추가하지 않았습니다.

## 역할과 저장 위치

- Codex: 직접 구현하는 테크 리드. 일부 업무 배정, 결과 검토, 통합.
- Antigravity: 배정된 조사·구현·검증·자체검수·보고 담당. 권한 대기 중에도 담당 유지.
- 사용자: 어느 쪽에도 직접 지시 가능. Antigravity 직접 대화는 사전 지시서 작성 불필요.
- `.agent-tasks/`: Antigravity가 만든 공통 교환소. 기존 한글 양식 유지.
- `.agent/`: 원본 실행 출력, 해시, 권한 대기, 검토 기록. 별도의 새 업무 큐가 아님.
- 전역 도구: `C:\Users\user\.codex\skills\antigravity-handoff`.

## 화면 열기

이 컴퓨터에 확인된 Python과 PowerShell 7 경로를 사용하는 예입니다. 경로가 바뀌면 실제 설치 경로로 바꿉니다.

```powershell
& 'C:\Users\user\AppData\Local\Programs\Python\Python310\python.exe' `
  'C:\Users\user\.codex\skills\antigravity-handoff\scripts\workdesk.py' `
  --project-root 'C:\Users\user\Desktop\alarm' `
  --pwsh 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
```

출력된 `http://127.0.0.1:포트/#세션토큰` 주소를 열면 됩니다. 서버를 유지해야 화면이 동작합니다. 재시작하면 새 주소·토큰을 사용하세요. 토큰을 외부에 공유하지 않습니다. 서버는 이 컴퓨터의 루프백에만 바인딩하며 원격 공개·자동 시작은 하지 않습니다.

## 사용 순서

1. **업무 등록**: 요청자, 목표, 수정 허용 파일, 범위, 검증 방법을 작성합니다. 수정 파일을 비우면 읽기 전용입니다. 등록만으로 AI를 실행하지 않습니다.
2. **Antigravity에 배정 · 실행**: 지시서가 `in_progress`로 이동합니다. 작업과 자체검수 후 원본 보고서가 `completed` 및 Codex 검토함에 남습니다.
3. **승인 대기**: 거부 행동과 사유를 확인합니다. 실제 Antigravity CLI 권한을 사용자가 확인·설정한 다음 같은 작업을 재개합니다. 화면의 확인란은 권한 설정 기능이 아닙니다.
4. **보고 검토**: Codex가 실제 파일·diff·검증 출력을 확인하고 검토 메모를 남깁니다. 사용자는 화면에서 승인 또는 수정 의견을 기록할 수 있습니다. 둘은 별도입니다.

Codex가 화면 없이 동일 흐름으로 배정할 때는 위 명령에 `--dispatch TASK-업무ID`를 추가합니다. 권한 대기 재개에는 `--resume --cli-permission-ready`를 추가합니다. 이는 CLI 권한 우회 옵션이 아닙니다.

## 기존 수동 작업과의 호환

- `TASK-001-name.md` 보고서는 `REPORT-001-name.md`처럼 같은 이름을 사용합니다.
- 한글 수동 지시서와 보고서를 읽고 보여줍니다. 수정 범위를 추측해 자동 실행하지 않습니다. 자동 실행에는 전역 스킬의 JSON 메타데이터와 필수 섹션을 명시하거나 화면에서 새 업무를 등록해야 합니다.
- 사용자가 Antigravity에 직접 준 업무를 자동 감지하지는 않습니다. 필요하면 작업 후 지시서·원본 보고서를 교환소에 남깁니다. 기존 파일을 덮어쓰지 않습니다.
- 수동 `in_progress` 항목이 있으면 다른 CLI 업무를 시작하지 않습니다. 직접 지시가 우선하더라도 실행 중 프로세스를 임의 강제 종료하거나 같은 파일을 동시에 수정하지 않습니다.
- `completed`는 보고 제출입니다. 테스트 통과, Codex 검토, 최종 승인까지 자동으로 뜻하지 않습니다.

## 검증 범위와 한계

- Python 자동 검사 10개: 접수·요청자 구분·파일 범위·수동 양식 표시·권한 대기·교환소 이동·사용자 승인 분리·HTTP 접근 검사, 모의 CLI 전체 왕복 포함.
- PowerShell 공통 검사 22개와 전역 통합 검사 17개 통과.
- 별도 모의 CLI 검사: 권한 거부, Antigravity 담당 유지, 변경된 프로젝트 재개 거절, 동일 대화 재개와 자체검수, 보고함 등록 통과.
- 위 모의 CLI 결과는 실제 Antigravity 응답이나 실제 권한 해결의 증거가 아닙니다.
- CLI는 OS 샌드박스가 아닙니다. 명세와 해시 비교는 범위 검사이며 강제 보안 경계가 아닙니다. 같은 checkout의 동시 수정은 CLI 전체 해시 검사와 충돌하므로 분리 checkout 또는 수동 모드를 사용합니다.
- 자동 Codex 호출·상시 감시·IDE 승인 창 자동 처리·할당량 자동 전환·OpenCode 재위임은 구현하지 않았습니다.
- 알람 기능, Android 빌드, 실기기, GitHub 배포, 서명키 작업은 이번 변경 범위 밖입니다.

검사 소스는 전역 스킬 `scripts/tests/`, 보존된 시험 근거와 기존 스킬 원본은 `.agent/archive/workdesk-v2/`에 있습니다.

### 실제 Antigravity 연결 시험

- 격리된 시험 프로젝트에서 화면으로 `TASK-20260929-150000-c61b25`를 등록·배정했습니다.
- 공식 `agy` CLI가 구현/조사와 자체검수 두 단계 모두 종료 코드 0으로 반환했습니다. 두 응답의 대화 ID는 `272c5d7a-de68-4901-af78-56bd7c9172a7`로 같습니다.
- 결과 상태는 `REVIEW`. 교환소 `completed`에 TASK와 원본 REPORT가 저장되고 Codex 검토함 항목이 생성되었습니다. 해시 비교에서 제품 파일 변경은 없었습니다.
- 원본 근거: `.agent/archive/workdesk-v2/ui-fixture/.agent/reports/desk-736944f1970a7954d32f7d5d.json`.
- 실제 시험은 작업 지시서 본문만 읽고 답한 읽기 전용 연결 시험입니다. 파일 쓰기, 빌드·테스트 명령 실행, 권한 거부 후 실제 재개, 앱 배포까지 검증한 것이 아닙니다. 보고서의 `NOT RUN`을 그대로 보존했습니다.

### 실행한 검사 명령

아래 검사들은 작업용 스테이징에서 실행한 명령입니다. 설치 후 18개 파일을 스테이징과 SHA256으로 대조했습니다. 전역 스킬의 기존 파일은 `.agent/archive/workdesk-v2/install-backup-20260929-150336/`에 보존했습니다.

```powershell
$deskPwsh = 'C:\Users\user\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe'
$deskPython = 'C:\Users\user\AppData\Local\Programs\Python\Python310\python.exe'
$deskSkill = 'C:\Users\user\Desktop\alarm\.agent\archive\workdesk-v2\skill'
$deskEvidence = 'C:\Users\user\Desktop\alarm\.agent\archive\workdesk-v2\evidence'
& $deskPwsh -NoProfile -File "$deskSkill/scripts/tests/Global.Tests.ps1" -EvidenceRoot $deskEvidence
& $deskPwsh -NoProfile -File "$deskSkill/scripts/tests/Workdesk.Tests.ps1" -EvidenceRoot $deskEvidence
$env:DESK_TEST_AGY = "$deskEvidence/desk-fixture-3d786670833b4d60ace57c8fb83e0161/.agent/desk/fake-agy.exe"
$env:DESK_TEST_PWSH = $deskPwsh
& $deskPython "$deskSkill/scripts/tests/test_workdesk.py"
$env:PYTHONUTF8 = '1'
& $deskPython 'C:\Users\user\.codex\skills\.system\skill-creator\scripts\quick_validate.py' $deskSkill
```

다른 시점에 새 모의 실행 파일을 생성했다면 `DESK_TEST_AGY`를 그때 출력된 시험 디렉터리의 `fake-agy.exe`로 바꾸세요. 모의 실행 파일은 시험 프로세스에만 사용하며 실제 Antigravity 설치나 권한 설정을 대체하지 않습니다.
