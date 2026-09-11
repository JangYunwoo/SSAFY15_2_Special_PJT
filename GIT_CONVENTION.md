# Git Convention

이 프로젝트(S15P21B205)의 폴더 구조, 브랜치 전략, 커밋 규칙을 정의합니다.
사람과 AI 에이전트(Claude Code 등) 모두 이 문서를 기준으로 작업합니다.

## 1. 폴더 구조 (모노레포)

```
S15P21B205/
├── backend/     # 백엔드 서버 코드 (Spring Boot)
├── frontend/    # 프론트엔드 코드 (React)
├── ai/          # AI 상품 식별 및 추천 서비스
├── docs/        # 요구사항/API/DB/외부연동/팀 문서 (docs/README.md가 색인)
├── .claude/     # Claude Code 설정
├── docker-compose.yml   # 로컬 개발용 MySQL, Redis
├── README.md
├── CLAUDE.md
├── AGENT.md
└── GIT_CONVENTION.md
```

- `backend/`, `frontend/`는 각자 독립적으로 빌드/실행 가능해야 합니다.
- 공통 문서·설정은 루트에 둡니다. 새로운 최상위 폴더(`infra/` 등)를 추가할 때는 이 문서도 함께 갱신합니다.

## 2. 브랜치 전략 (Git Flow)

| 브랜치 | 용도 | 병합 대상 |
|---|---|---|
| `master` | 배포 가능한 안정 버전. Protected. | `release/*`, `hotfix/*` 에서만 병합 |
| `develop` | 다음 배포를 위한 개발 통합 브랜치. Protected. | `feature/*`, `fix/*` 에서 PR로 병합 |
| `feature/{scope}/{ISSUE-KEY}-{설명}` | 기능 개발 | → `develop` |
| `fix/{scope}/{ISSUE-KEY}-{설명}` | 버그 수정 | → `develop` |
| `release/{version}` | 배포 준비 (버전 고정, QA) | → `master` (+ `develop`로 백머지) |
| `hotfix/{ISSUE-KEY}-{설명}` | 운영 중 긴급 수정 | → `master` (+ `develop`로 백머지) |

- `scope`는 `be`(backend) 또는 `fe`(frontend)를 사용합니다.
- `ISSUE-KEY`는 지라(Jira) 이슈 번호를 그대로 사용합니다.
- 예: `feature/be/B205-12-login-api`, `fix/fe/B205-20-button-click-error`, `hotfix/B205-30-payment-null-error`

### 규칙
- `master`, `develop`에는 직접 커밋/푸시하지 않습니다. 반드시 PR을 통해서만 병합합니다.
- `master`, `develop`는 force-push를 금지합니다 (GitLab protected branch로 설정).
- 브랜치는 작업이 끝나고 PR이 병합되면 삭제합니다.

## 3. 커밋 컨벤션 (Conventional Commits)

```
<type>(<scope>): <subject>

<body (선택)>

<footer (선택, 이슈 연동)>
```

### type
| type | 설명 |
|---|---|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 세미콜론 등 동작에 영향 없는 변경 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 코드 추가/수정 |
| `chore` | 빌드, 패키지 매니저, 설정 등 기타 변경 |
| `build` | 빌드 시스템, 외부 의존성 변경 |
| `ci` | CI 설정 변경 |
| `perf` | 성능 개선 |

### scope
`be`, `fe`, `infra` 등 변경된 영역. 전역 변경(문서, 설정)은 생략 가능합니다.

### subject
- 한글로 간결하게, 마침표 없이, 명령형/현재형으로 작성합니다.
- 빈 메시지("a", "8/27" 등)나 의미 없는 메시지는 금지합니다.

### 예시
```
feat(be): 로그인 API 구현
fix(fe): 로그인 버튼 클릭 시 발생하는 오류 수정
docs: README 프로젝트 소개 갱신
chore: 깃 컨벤션 문서 추가

이슈: B205-12
```

## 4. PR 규칙

- PR 제목은 커밋 컨벤션과 동일한 형식(`type(scope): subject`)을 따릅니다.
- 리뷰어 최소 1명의 승인(Approve)이 있어야 병합할 수 있습니다.
- `feature/*`, `fix/*` → `develop`: Squash and merge를 사용합니다 (커밋 히스토리를 깔끔하게 유지).
- `release/*`, `hotfix/*` → `master`: Merge commit을 사용합니다 (릴리즈 이력 보존).
- PR 설명에는 관련 지라 이슈 링크/번호를 반드시 포함합니다.

## 5. 이슈 연동

- 모든 작업은 지라 이슈를 기반으로 시작합니다. 이슈 없는 작업 브랜치는 만들지 않습니다.
- 브랜치명과 커밋 footer에 이슈 키(`B205-xx`)를 포함해 추적성을 확보합니다.
