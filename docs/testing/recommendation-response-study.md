# AI 확장 점검과 추천 응답 경계 수정

이슈: S15P21B205-184. 기준 DevelopV2 20af4cb. 작성일 2026-09-21.

> 이 문서는 당시 로지스틱 회귀 구현을 점검한 기록이다. 현재 추천 모델은
> S15P21B205-223에서 LightGBM Learning-to-Rank로 교체되었다.

## 1. 팀원 확장으로 달라진 흐름

기존 이미지 기반 상품 정보 추출에 더해 개인화 공동구매 추천이 추가되었다. 프런트 HomePage는 로그인 사용자에게 추천 목록을 조회하고, 카드 노출·클릭·상세 조회 행동을 POST로 기록한다. 탭 종료 시 세션 종료 API 역시 POST를 사용한다. 추천 조회는 GET이다. 읽기와 행동 기록의 HTTP 메서드 구분은 이번 코드 검사에서 맞게 되어 있었다.

백엔드 RecommendationRepository는 모집 중 후보와 행동·구매 이력을 가져온다. RecommendationService가 후보와 이력을 AI 서버에 전달하고 AiRecommendationClient는 `/api/v1/recommendations/rank`를 POST로 호출한다. AI는 pandas로 입력을 변환하고 브랜드·카테고리 선호, 모집률, 할인율, 마감 임박도를 사용한다. 학습 데이터가 20행 미만이거나 두 가지 정답 클래스가 없으면 가중치 기반 HEURISTIC_FALLBACK을, 그 외에는 로지스틱 회귀를 사용한다. 최근 구매 상품 점수를 낮추는 처리도 추가되었다.

시연용 데이터 시더가 추가되어 있지만 설정의 기본 seed-enabled는 false다. 이번 점검에서 시더를 켜거나 배포 DB에 데이터를 넣지 않았다. 추천 저장소는 NamedParameterJdbcTemplate의 명시적 SQL을 사용한다. 후보 조인 조회·행동 배치 삽입·세션 upsert를 다루는 구조로 JPA 엔티티 저장 방식과 다르다. 이번 MR은 외부 응답 계약 버그만 고치므로 저장 계층 전체를 JPA로 재작성하지 않는다. SQL을 사용한다는 이유만으로 성능이나 정합성이 틀렸다고 판단하지 않되, 별도 설계 정리가 필요한 경계임은 남긴다.

## 2. 발견한 응답 경계 문제

기존 recommend는 응답 전체와 rankedItems가 null인 경우, HTTP 호출 예외인 경우에는 fallback을 사용했다. 그러나 리스트 내부는 후보 ID 포함 여부만 확인했다. `[null]`이면 filter에서 NullPointerException이 발생한다. 같은 ID가 여러 번 오면 같은 카드가 여러 개 생기고 React 키도 중복된다. AI가 요청 size를 무시하면 백엔드 역시 초과한 개수를 반환한다. 후보 밖 ID만 있으면 후보가 실제로 있어도 빈 목록을 반환한다. NaN/Infinity 같은 값과 null 추천 이유는 프런트의 number/string 계약을 깨뜨릴 수 있다.

외부 AI가 현재 정상 응답을 만드는 것과 백엔드가 외부 응답을 안전하게 다루는 것은 별도 책임이다. 네트워크 오류만 잡아서는 JSON으로 역직렬화된 잘못된 내용까지 보호할 수 없다. 배포에서 이 모든 오류를 관찰했다는 주장이 아니라, 실제 서비스 코드에 비정상 응답을 주입하여 회귀 테스트로 재현한 결함이다.

## 3. 선택 기준과 변경 알고리즘

모든 RuntimeException을 잡아서 숨기는 방법은 내부 코드 오류까지 정상 추천처럼 위장하므로 선택하지 않았다. 이미 지원하는 두 AI 모델 종류를 확인하고 항목별 조건을 명시적으로 검증한다. AI가 만든 순서는 유지한다.

처리는 null 항목 제외 → 실제 후보 ID 확인 → score 존재와 유한성 확인 → ID 중복 제거 → 요청 limit 적용 → 응답 매핑 순서다. 잘못된 첫 항목 때문에 같은 ID의 뒤에 있는 정상 항목까지 제거되지 않도록 점수 검사를 중복 검사보다 먼저 한다. HashSet은 이 요청의 순차 stream에만 사용한다. 병렬 stream으로 바꾸지 않는다.

유효 결과가 하나라도 있으면 그 순위를 유지한다. 부족한 개수를 다른 종류의 점수로 섞어 채우지는 않는다. 모두 잘못되었거나 비어 있으면 기존 백엔드의 모집률 내림차순·마감 임박도 내림차순 fallback을 사용한다. 실제 후보 자체가 없으면 기존대로 EMPTY다. 이유 문자열이 없거나 비어 있으면 중립적인 기본 문구를 넣는다.

RankedItem의 score를 double에서 Double로 바꾸어 JSON score 누락을 0점으로 오인하지 않도록 했다. featureWeights도 null/비유한 값과 빈 키를 제거한다. 프런트가 알고 있지 않은 modelType이면 임의 문자열을 전달하지 않고 BACKEND_FALLBACK을 반환한다. 향후 AI 모델을 새로 추가할 때는 이 허용 목록과 프런트 계약을 함께 바꿔야 한다.

## 4. 수정 전·후 검증

처음 추가한 서비스 테스트 11개를 수정 전 코드에 실행했다. 8개 실패·3개 통과였다. 실패는 null 항목, 중복, size 초과, 후보 밖 ID뿐인 결과, 빈 AI 목록, 비유한 점수, null 이유, 잘못된 featureWeights였다. 정상 순위 유지·외부 통신 오류 fallback·후보 없음은 기존에도 통과했다. 이후 score 누락, 무효 중복 뒤 정상 항목, 알 수 없는 모델 종류 3개를 추가했다.

수정 후 전체 백엔드 테스트 XML 집계: 97개 suite, 총 629개, 실패 0, 오류 0, 제외 3개. 즉 실제 통과는 626개다. 새 추천 서비스 테스트 14개는 모두 통과했다. 제외된 기존 Redis 실환경 2개와 S3 실환경 1개는 사용자 요청으로 Docker 작업을 보류하여 실행되지 않았다. 제외를 성공으로 계산하지 않는다.

AI 프로젝트의 기존 `test_recommendation_api.py`, `test_url_api.py`, `test_gemini_analysis.py`도 로컬 QA 가상환경에서 실행하여 8개가 통과했다. HTTPX/Starlette 및 AnyIO deprecation 경고 2개가 있었다. 이미지 분석 테스트의 외부 호출은 테스트 대역을 사용하므로 Gemini 실호출 성공이나 S3 공개 URL 접근 성공을 뜻하지 않는다. 추천 API의 cold-start 응답 테스트는 실제 로컬 FastAPI 경로와 추천 계산을 지난다. 추천 정확도 평가나 대규모 학습 성능 측정은 아니다.

이번 작업은 외부 응답 정합성을 보장하는 수정이다. 벤치마크를 하지 않았으므로 성능 향상을 주장하지 않는다. 추가 처리의 시간복잡도는 AI 결과 항목 수에 선형이며 limit 충족 시 stream의 처리를 멈춘다. 테스트 소요 시간은 서비스 처리 지연의 전후 비교가 아니다.

## 5. 이번 MR 밖의 발견 사항과 검증 한계

프런트 추천 세션 ID가 사용자 ID와 묶이지 않아 같은 탭의 계정 전환 시 이전 세션을 재사용할 수 있다. 또한 백엔드의 세션 소유자 사전 조회와 upsert 사이에는 동시 최초 생성의 경합 여지가 있다. 이들은 응답 목록 검증과 별도 원인이므로 독립 버그로 수정·검증한다.

후보 SQL은 상태 RECRUITING을 확인하지만 시작·종료 시각 자체를 조건에 넣지 않는다. 상태 갱신 지연 시 마감된 상품이 추천 후보가 될 수 있는지 별도 DB 경계 테스트가 필요하다. 구매 이력 특징은 현재 공구 값으로 재계산하므로 과거 시점 특징과 같다는 보장도 없다. 이 검사만으로 추천 품질·전 금융 흐름·전체 시스템의 모든 버그가 검증 완료됐다고 판단하지 않는다.

## 6. 재실행과 코드 읽는 순서

RecommendationController → RecommendationService.recommend → AiRecommendationClient 응답 record → RecommendationServiceTest 순으로 읽는다. 이후 AI의 recommendation_api.py와 recommendation_poc/recommender.py를 보면 모델과 fallback이 구분되는 지점을 이해할 수 있다.

backend에서 Java 21로 `gradlew.bat test --tests '*RecommendationServiceTest' --console=plain`, 전체는 `gradlew.bat test --console=plain`을 실행한다. AI는 준비된 Python 환경에서 `python -m pytest test_recommendation_api.py test_url_api.py test_gemini_analysis.py -q`를 실행한다. 별도 API 키를 문서나 테스트 코드에 넣지 않는다.

독립 브랜치에서 검토 후 DevelopV2에 MR 병합한다. DevelopV1과 EC2 배포는 변경하지 않으며 로컬 테스트 결과를 CI 파이프라인 결과로 표현하지 않는다.
