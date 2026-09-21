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

## 상품 이미지 저장소 (S3)

상품 이미지는 S3에 저장한다 (`S3ProductImageStorage`). 자격증명은 AWS SDK 기본 자격증명
체인을 사용하므로 애플리케이션 설정에는 Access Key를 두지 않는다 - 로컬 개발은
`AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` 환경변수, 배포 환경은 EC2/ECS 인스턴스
프로필을 사용한다. **Access Key는 절대 커밋하지 않는다.**

| 환경변수 | 설명 |
| --- | --- |
| `AWS_S3_BUCKET` | 상품 이미지 버킷 이름 (필수) |
| `AWS_REGION` | 기본값 `ap-northeast-2` |
| `AWS_S3_ENDPOINT` | S3 호환 스토리지(LocalStack/MinIO 등) 테스트 시 엔드포인트 오버라이드 |
| `AWS_S3_PUBLIC_BASE_URL` | 비워두면 `https://{bucket}.s3.{region}.amazonaws.com`으로 조립 |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | 로컬 개발용 자격증명 (배포 환경은 인스턴스 프로필 사용 권장) |

로컬에서 매번 `export`/`$env:`로 넣기 귀찮으면 `backend/.env.example`을 `backend/.env`로
복사해서 값을 채워두면 된다. `.env`는 `.gitignore`에 걸려 있고, `./gradlew bootRun`이
자동으로 읽어서 프로세스 환경변수로 주입한다(빌드 결과물에는 포함되지 않음). IntelliJ Run
Configuration으로 직접 실행할 때는 `.env`가 자동 반영되지 않으니, EnvFile 플러그인을 설치해서
Run Configuration에 `backend/.env`를 지정하거나 Environment variables에 직접 붙여넣는다.

## AI 상품 정보 추출

`POST /api/v1/sellers/products/ai-extraction`은 업로드된 대표 이미지와 추가 이미지를 합쳐 최대
5장까지 분석한다. 백엔드는 파일을 분석용 임시 S3 객체로 올리고, 공개 HTTPS URL 목록을
`image_urls` JSON으로 FastAPI에 전달한다. FastAPI 응답이 성공이든 실패든 임시 객체는 즉시
삭제되며 상품을 최종 등록할 때 이미지를 다시 영구 저장한다. 따라서 실제 AI 성공 경로에는
`AWS_S3_BUCKET`과 Gemini가 읽을 수 있는 `AWS_S3_PUBLIC_BASE_URL`이 필요하다. 로컬 파일 저장소나
HTTP MinIO URL은 수동 입력 폴백 검증에는 쓸 수 있지만 Gemini 성공 경로에는 쓸 수 없다.

기본 제한시간은 10초이며, 저장소 장애·AI 장애·시간 초과·빈 응답은 `FAILED`와 빈 필드로 변환해
판매자가 수동 입력을 계속할 수 있게 한다. 이미지 형식·장수 같은 사용자 입력 오류는 400으로
반환한다. AI가 반환한 카테고리 ID를 우선 검증하고, ID가 없으면 활성 DB 카테고리 이름을 대조해
ID로 바꾼다. 매칭되지 않으면 해당 필드만 비우고 경고를 반환한다.

| 환경변수 | 설명 |
| --- | --- |
| `AI_EXTRACTION_BASE_URL` | FastAPI 서버 주소, 기본값 `http://localhost:8000` |
| `AI_EXTRACTION_PATH` | 이미지 분석 경로, 기본값 `/api/v1/products/extract` |
| `AI_EXTRACTION_TIMEOUT_MS` | 연결·응답 제한시간, 기본값 `10000` |

## 개인화 추천 로컬 데이터

운영 DB를 복사하지 않고 로컬 통합 화면에서 추천 순위 변화를 확인하려면
`backend/.env`에 다음 값을 추가한다.

```dotenv
RECOMMENDATION_SEED_ENABLED=true
```

이 옵션은 `local` 프로필에서만 동작하며, 기동 시 가상 상품 24개와 로그인 가능한
구매자 3명, 사용자별 행동 로그 120건, 구매 이력을 멱등하게 생성한다.

| 이메일 | 선호 경향 | 비밀번호 |
| --- | --- | --- |
| `recommend101@gongguhan.test` | 가공식품·맛있는청년들 | `Recommend1234!` |
| `recommend102@gongguhan.test` | 농산물·참달아 | `Recommend1234!` |
| `recommend103@gongguhan.test` | 건강기능식품·종근당 | `Recommend1234!` |

각 계정으로 로그인하면 홈의 추천 순서가 서로 달라야 한다. 시드는 고정 ID를 사용하고
`ON DUPLICATE KEY UPDATE`로 넣으므로 백엔드를 다시 실행해도 중복 생성되지 않는다.
운영 프로필에서는 이 컴포넌트가 로드되지 않으며 기본값도 `false`다.

추천 세션의 무활동 판정은 시연에서 추천 순위 변화를 빠르게 확인하도록
1초로 설정했다. 운영 전환 시에는 30분으로 조정할 예정이다.

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
