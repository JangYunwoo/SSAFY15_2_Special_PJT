# 공구한 (Gongguhan)

여러 명이 모여 한 상품을 공동구매하는 플랫폼. 카드/카카오페이/디지털 자산(코인) 3가지
결제수단을 지원하고, 코인 결제는 에스크로로 대금을 보호한다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 프론트엔드 | React 19, Vite |
| 백엔드 | Java 21, Spring Boot 4.1.1, Spring Data JPA, QueryDSL, Spring Security + JWT |
| DB | MySQL 8.0 (Flyway 마이그레이션) |
| 캐시 | Redis 7 |
| 결제 연동 | SSAFY 교육용 금융망 API(카드/계좌이체), 카카오페이, MetaMask + Polygon Amoy(ERC-20 USDT) |

세부 버전과 선정 이유는 [`backend/README.md`](./backend/README.md)를 참고한다.

## 폴더 구조

```
S15P21B205/
├── backend/     # Spring Boot 백엔드 (실행 방법은 backend/README.md)
├── frontend/    # React 프론트엔드 (실행 방법은 frontend/README.md)
├── docs/        # 요구사항/API/DB/외부연동/팀 문서 (docs/README.md가 색인)
├── docker-compose.yml   # 로컬 개발용 MySQL, Redis
├── README.md
├── CLAUDE.md            # AI 에이전트 작업 지침
├── AGENT.md
└── GIT_CONVENTION.md    # 브랜치/커밋/PR 규칙
```

## 시작하기

```bash
# 1. 인프라(MySQL, Redis) 기동
docker compose up -d

# 2. 백엔드
cd backend && ./gradlew bootRun

# 3. 프론트엔드
cd frontend && npm install && npm run dev
```

## 문서

- [docs/README.md](./docs/README.md) - 요구사항 명세서, API 명세서, DB 스키마, 외부 연동 자료 색인. 문서 간 충돌 시 **API 명세서 > DB 스키마 > 요구사항 명세서** 순으로 판단한다.
- [docs/team/role-division.md](./docs/team/role-division.md) - 백엔드 3인 역할 분담(결제수단별 도메인 배분)
- [GIT_CONVENTION.md](./GIT_CONVENTION.md) - 브랜치 전략, 커밋 컨벤션, PR 규칙 (필독)

## 협업 규칙 요약

- `master`, `develop`에 직접 커밋/푸시 금지. `feature/{be|fe}/{ISSUE-KEY}-{설명}` 브랜치에서
  작업 후 PR로 병합한다.
- 커밋 메시지는 Conventional Commits(`type(scope): subject`)를 따른다.
- 자세한 내용은 [GIT_CONVENTION.md](./GIT_CONVENTION.md) 참고.
