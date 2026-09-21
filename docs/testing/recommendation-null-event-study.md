# 추천 이벤트 배열 null 검증 학습 기록

이슈: S15P21B205-190. 기준: DevelopV2 aa19698. 배포본 DevelopV1과 실제 금융 거래는 변경하지 않는다.

## 1. 증상과 입력 계약

추천 행동 기록 API는 `POST /recommendations/events`다. 정상 요청은 sessionId와 이벤트 객체 배열을 받는다. 객체에 groupBuyId, eventType을 넣는다. 그런데 `events: [null]`이나 정상 객체 뒤에 null을 넣는 요청도 JSON으로는 올바르다. 기존 코드는 리스트 자체가 비어 있지 않은지 검사하고 각 객체의 필드를 검사하지만, 객체 자체의 부재는 막지 않았다.

서비스의 `recordEvents`는 각 항목을 `normalizeEvent`로 넘기고 필드를 읽는다. null에 필드를 읽으면 NullPointerException이다. 이는 사용자 입력 오류인데 서버 500으로 분류될 수 있으며, 공통 오류 로그까지 발생한다. 정상 프런트가 이런 배열을 보내지 않는다는 기대는 API 입력 검증을 대체하지 않는다.

## 2. 왜 기존 검증으로 부족했나

`@NotEmpty`는 바깥 리스트를 검사한다. `[null]`의 길이는 1이라 통과한다. `@Valid`는 존재하는 객체 안의 제약을 재귀적으로 검사하는 역할이며 null을 금지하는 제약이 아니다. 따라서 `List<@NotNull @Valid RecommendationEventItem>`처럼 요소의 존재와 요소 내부의 유효성을 별도로 선언해야 한다.

## 3. 대안과 선택

서비스에서 null을 필터링하면 손상된 요청 일부를 성공으로 저장하게 되고 클라이언트가 오류를 알지 못한다. 서비스에서 NPE를 잡아 일반 오류로 바꾸는 것은 발생 위치를 뒤로 미루고 입력 계약을 DTO와 분리한다. 공통 예외 처리기의 모든 NPE를 400으로 바꾸면 실제 서버 버그도 사용자 책임으로 오인한다. 따라서 입력 경계에서 null을 거부하는 최소 수정이 적합하다. 컨트롤러의 기존 `@Valid`와 GlobalExceptionHandler를 그대로 활용한다.

## 4. 실제 구현과 요청 흐름

변경 파일은 RecommendationEventRequest 하나다. events의 제네릭 요소에 `@NotNull`을 추가했다. 요청 역직렬화 → Bean Validation → 잘못된 요소가 있으면 MethodArgumentNotValidException → 공통 응답 `400 / VALIDATION_ERROR` 순서로 처리한다. 정상 요청에 대한 DTO 필드, URL, 응답 형식, 서비스 트랜잭션, DB 구조는 바뀌지 않는다.

## 5. 수정 전후 검증 설계와 결과

새 RecommendationEventValidationTest는 실제 RecommendationController와 GlobalExceptionHandler를 MockMvc standalone에 올린다. 보안 계층은 이 테스트의 대상이 아니어서 AuthenticationPrincipal만 테스트 사용자 1로 공급하고 서비스는 mock으로 둔다. 핵심 관찰점은 HTTP 상태뿐 아니라 잘못된 입력이 서비스에 단 한 번도 전달되지 않는지다.

| 사례 | 수정 전 | 수정 후 |
| --- | --- | --- |
| events가 [null] | 예상 400 대신 200, 테스트 실패 | 400 VALIDATION_ERROR, 서비스 호출 0 |
| 정상 객체 + null | 예상 400 대신 200, 테스트 실패 | 400 VALIDATION_ERROR, 서비스 호출 0 |
| 정상 이벤트 1개 | 200, 서비스 전달 | 동일하게 200, 서비스 전달 |

수정 전 200은 테스트에서 서비스를 mock으로 둔 결과다. 이를 실제 서버에서 200이었다고 해석하면 안 된다. 검증을 통과해 서비스까지 도달한다는 사실을 재현한 것이며, 실제 서비스의 null 역참조가 500 원인이다. 수정 전 테스트 실행은 3개 중 2개 실패, 수정 후 전체 회귀는 639개 중 636개 통과·3개 건너뜀·실패 0·오류 0(99 suites)이었다. 환경은 Windows, Java 21, 프로젝트 Gradle Wrapper다.

이번 수정은 기능/입력 정합성 수정이다. 처리 성능 개선을 주장하지 않으며 Gradle 소요 시간을 API 지연 시간으로 제시하지 않는다.

## 6. 재실행과 한계

backend에서 Java 21을 지정하고 `./gradlew.bat test --tests '*RecommendationEventValidationTest' --console=plain`으로 경계 테스트, `./gradlew.bat test --console=plain`으로 전체 회귀를 실행한다. 전체 결과는 build/test-results/test와 build/reports/tests/test/index.html에 생성된다.

Redis 2건과 S3 1건은 Docker 환경을 나중에 진행한다는 사용자 지시에 따라 여전히 건너뛴다. HTTP 검증 테스트는 인증/인가 필터 전체나 운영 DB/외부 AI를 실행하지 않는다. 이 수정은 내부 Java 호출에 자동 DTO 검증을 추가하는 변경도 아니다. 외부 요청은 기존 컨트롤러 @Valid를 통해 검증된다. 다른 추천 품질/후보 기간 문제는 별도 이슈에서 다룬다.

학습할 때 DTO → 컨트롤러의 @Valid → 공통 예외 처리 → 테스트의 verifyNoInteractions 순서로 읽으면, 입력 검증과 업무 처리의 경계를 이해하기 쉽다.
