# 공구한 문서

세 문서(API 명세서, DB 스키마, 요구사항 명세서)가 서로 충돌하면 아래 우선순위로 판단한다.

**API 명세서 > DB 스키마 > 요구사항 명세서**

(배경은 [api/공구한_API_명세서_v1.md](./api/공구한_API_명세서_v1.md) 0장 "작성 기준과 선결 검토사항" 참고)

## 폴더

| 폴더 | 내용 |
| --- | --- |
| [requirements/](./requirements) | 요구사항 명세서 v4 (원본 PDF) |
| [api/](./api) | API 명세서 v1 - 엔드포인트, 요청/응답 예시, 에러 코드, 상태 전이 규칙 |
| [database/](./database) | DB 스키마(DDL) v2, ERD 이미지 |
| [external/](./external) | SSAFY 교육용 금융망 API 연동 자료 (플로우, 인터페이스 예제) |
| [market-research/](./market-research) | 시장조사 |
| [performance/](./performance) | 성능 병목의 선정 이유, 최적화 설계, 전·후 측정값과 재현 방법 |
| [infra/](./infra) | 서버/배포 아키텍처 설계서(EC2). 배포 실행 절차는 루트 [deploy/](../deploy) 참고 |
| [team/](./team) | 백엔드 역할 분담. 브랜치/커밋/PR 규칙은 루트 [GIT_CONVENTION.md](../GIT_CONVENTION.md) 참고 |

## 학습 가이드

- [김태민 담당 MVP 구현 학습 가이드](./team/김태민_MVP_구현_학습_가이드.md): 상품 → 공동구매 → 카카오페이 → 반품·환불의 의존 관계, 설계 판단, JPA/SQL 경계, 테스트와 수동 점검 순서를 처음 보는 개발자 기준으로 설명한다.
- [결제 이력 N+1 최적화 성능 테스트](./performance/결제_이력_N+1_최적화_성능_테스트.md): 최적화 대상을 선정한 이유부터 일괄 조회 설계, 통제 변수, 전·후 성능 지표, 측정 한계와 운영형 후속 시험까지 설명한다.

## 코드와의 연결

- DB 스키마는 `backend/src/main/resources/db/migration/V1__init_schema.sql`에 Flyway 마이그레이션으로 그대로 반영되어 있다. 스키마를 바꿀 때는 `docs/database/공구한_schema_v2.sql`과 새 마이그레이션 파일(`V2__...`)을 함께 추가한다 - 이미 적용된 `V1`은 수정하지 않는다.
- API 명세서의 도메인 구분은 `backend/src/main/java/com/gongguhan/domain/*` 패키지와 1:1로 대응한다. 각 패키지의 `package-info.java`에 담당자와 관련 API ID가 적혀 있다.
