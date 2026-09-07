# 공구한 (Gongguhan) - Backend

공동구매 플랫폼 백엔드. 카드/카카오페이/디지털 자산(코인) 3가지 결제수단과 에스크로·정산을
포함한 거래 전체 라이프사이클을 다룬다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| 언어/런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 (Spring Framework 7) |
| 빌드 | Gradle 9 (Groovy DSL), Gradle Wrapper 포함 |
| 영속성 | Spring Data JPA (Hibernate) + QueryDSL 5.1 (동적 쿼리) |
| DB | MySQL 8.0 |
| 마이그레이션 | Flyway |
| 캐시/세션 | Redis 7 (결제 슬롯, 캐시 등) |
| 인증 | Spring Security + JWT (`io.jsonwebtoken:jjwt`) |
| 결제 연동 | SSAFY 교육용 금융망 API(카드/계좌이체), 카카오페이 API, MetaMask + Polygon Amoy(ERC-20) |

버전은 `docs/api/공구한_API_명세서_v1.md`와 `docs/database/공구한_schema_v2.sql`을 기준으로
2026-09 시점 최신 안정 버전을 선택했다. `springdoc-openapi`는 Spring Boot 4(Spring
Framework 7)와의 Spring Data 버전 충돌로 기동 실패가 확인되어 제외했다 - 호환 버전이
나오면 추가한다.

## 로컬 실행

```bash
# 1. MySQL, Redis 기동 (저장소 루트에서)
docker compose up -d

# 2. 애플리케이션 실행 (backend/에서)
./gradlew bootRun
```

기본 접속 정보(로컬 전용, `application.yml` 기본값)는 `docker-compose.yml`과 일치한다.
운영/개발 서버 값은 커밋하지 않고 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
`REDIS_HOST`, `REDIS_PORT`, `JWT_SECRET` 등)로 주입한다.

애플리케이션 기동 시 Flyway가 `src/main/resources/db/migration/V1__init_schema.sql`
(=`docs/database/공구한_schema_v2.sql`과 동일)을 자동 적용한다. `spring.jpa.hibernate.ddl-auto=validate`이므로
엔티티 매핑이 실제 스키마와 다르면 기동 시점에 바로 에러가 난다 - 스키마 변경이 필요하면
새 `V2__...sql` 마이그레이션을 추가한다.

## API 기본 경로

`server.servlet.context-path=/api/v1`이 설정되어 있으므로, 컨트롤러의 `@RequestMapping`은
API 명세서의 경로에서 `/api/v1`을 뺀 상대 경로로 작성한다.
예: 명세서의 `POST /api/v1/auth/signup` → 컨트롤러는 `@PostMapping("/auth/signup")`.

## 패키지 구조

```
src/main/java/com/gongguhan/
├── GongguhanApplication.java
├── global/                  # 전 도메인 공통 - 임의로 구조를 바꾸지 말고 PR로 논의
│   ├── common/               # ApiResponse, ApiError, PageResponse, BaseTimeEntity
│   ├── config/                # SecurityConfig, QuerydslConfig, JpaConfig, SchedulingConfig, TraceIdFilter
│   ├── error/                  # ErrorCode, BusinessException, GlobalExceptionHandler
│   └── security/                # JWT 발급/검증 (구현 예정)
└── domain/
    ├── account/          # 계정·인증·판매자 인증
    ├── product/          # 상품·카테고리·AI 추출
    ├── groupbuy/         # 공동구매
    ├── payment/
    │   ├── common/        # 결제 슬롯 오케스트레이션(결제수단 전략 인터페이스)
    │   ├── card/           # 카드결제
    │   ├── kakaopay/        # 카카오페이
    │   └── crypto/           # 디지털 자산(코인) 결제
    ├── participation/    # 참여·구매확정·이의제기
    ├── escrow/            # 에스크로
    ├── delivery/          # 배송
    ├── returns/           # 반품 (return은 자바 예약어라 returns 사용)
    ├── refund/            # 환불
    ├── settlement/        # 판매자 정산
    ├── penalty/           # 판매자 제재
    ├── notification/      # 알림
    └── admin/             # 정책·감사로그 등 전역 관리자 기능
```

각 도메인 패키지는 `controller / service / repository / entity / dto` 5개 하위 패키지로
시작한다. 담당자와 관련 API ID는 각 패키지의 `package-info.java`에 적혀 있다.

담당 배분과 협업 규칙은 [`docs/team/role-division.md`](../docs/team/role-division.md),
브랜치·커밋·PR 규칙은 루트 [`GIT_CONVENTION.md`](../GIT_CONVENTION.md)를 따른다.
