# CLAUDE.md

Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## Git 작업 규칙

이 저장소의 브랜치 전략, 커밋 메시지 형식, PR 규칙은 [GIT_CONVENTION.md](./GIT_CONVENTION.md)에 정의되어 있습니다. **모든 git 작업(브랜치 생성, 커밋, PR)은 반드시 이 문서를 따르세요.**

핵심 요약:
- `master`, `develop`에 직접 커밋/푸시 금지. 항상 `feature/{be|fe}/{ISSUE-KEY}-{설명}` 또는 `fix/{be|fe}/{ISSUE-KEY}-{설명}` 브랜치를 만들어 작업하고 PR로 병합합니다.
- 커밋 메시지는 Conventional Commits 형식(`type(scope): subject`)을 사용합니다. `type`은 `feat/fix/docs/style/refactor/test/chore/build/ci/perf` 중 하나. "a", "wip" 같은 의미 없는 메시지는 사용하지 않습니다.
- `master`, `develop`는 protected branch이므로 force-push가 불가능합니다. 히스토리를 되돌려야 할 때는 `git revert`를 우선 고려하세요.
- 사용자가 명시적으로 요청하지 않으면 커밋/푸시/PR 생성을 먼저 하지 않습니다.

자세한 내용, 예시, PR 병합 전략은 [GIT_CONVENTION.md](./GIT_CONVENTION.md)를 참고하세요.

## 폴더 구조

```
S15P21B205/
├── backend/     # 백엔드 서버 코드
├── frontend/    # 프론트엔드 코드
├── .claude/     # Claude Code 설정
├── README.md
├── CLAUDE.md
├── AGENT.md
└── GIT_CONVENTION.md
```
