# AGENT.md

이 저장소에서 작업하는 모든 AI 에이전트(Claude Code 포함)를 위한 공통 지침입니다.

## Git 작업 규칙

브랜치 전략, 커밋 메시지 형식, PR 규칙은 [GIT_CONVENTION.md](./GIT_CONVENTION.md)에 정의되어 있습니다. **모든 git 작업은 반드시 이 문서를 따르세요.**

핵심 요약:
- `master`, `develop`에 직접 커밋/푸시 금지. 항상 `feature/{be|fe}/{ISSUE-KEY}-{설명}` 또는 `fix/{be|fe}/{ISSUE-KEY}-{설명}` 브랜치를 만들어 작업하고 PR로 병합합니다.
- 커밋 메시지는 Conventional Commits 형식(`type(scope): subject`)을 사용합니다.
- `master`, `develop`는 protected branch이므로 force-push가 불가능합니다.
- 사용자가 명시적으로 요청하지 않으면 커밋/푸시/PR 생성을 먼저 하지 않습니다.

자세한 내용은 [GIT_CONVENTION.md](./GIT_CONVENTION.md)를 참고하세요.

## 폴더 구조

```
S15P21B205/
├── backend/     # 백엔드 서버 코드 (Spring Boot)
├── frontend/    # 프론트엔드 코드 (React)
├── docs/        # 요구사항/API/DB/외부연동/팀 문서 (docs/README.md가 색인)
├── .claude/     # Claude Code 설정
├── docker-compose.yml   # 로컬 개발용 MySQL, Redis
├── README.md
├── CLAUDE.md
├── AGENT.md
└── GIT_CONVENTION.md
```
