# B21: 잘못된 HTTP 메서드를 서버 장애로 처리하지 않기

## 1. 추적과 범위

Jira: [S15P21B205-180](https://ssafy.atlassian.net/browse/S15P21B205-180). 브랜치 `fix/be/S15P21B205-180-http-method`, 기준 DevelopV2 `248b4a806f5e69c08b4bcbf9b2519947fbec274c`. 앞선 토큰 갱신 MR !145까지 포함한다.

대표 재현 대상은 POST /admin/refunds/{refundId}/retry다. 사용자가 지적한 'POST로 전달해야 할 요청이 GET으로 실행되는가'라는 질문에서, 이 경로는 원래부터 POST로만 매핑되어 있었고 GET으로 환불을 실행하는 것은 아니었다. 실제 문제는 잘못된 메서드가 500으로 응답되고 Allow 안내가 사라지는 것이었다. 이를 GET 금융 실행 취약점으로 과장하지 않는다.

## 2. 원인과 수정 전 증거

Spring MVC는 같은 URL에 해당 메서드가 없으면 HttpRequestMethodNotSupportedException을 발생시킨다. 기존 전역 예외 처리기는 이 예외의 전용 처리가 없어 Exception 전체 처리기로 보냈다. 그 처리기는 일반 서버 장애를 의미하는 INTERNAL_SERVER_ERROR/500을 만들었다.

실제 AdminRefundController와 MVC 라우팅·GlobalExceptionHandler를 연결한 테스트를 먼저 작성했다. GET, PUT, PATCH, DELETE 요청은 모두 405여야 하지만 수정 전 500이 나왔다. 총 6개 중 이 4개가 실패하고, 정상 POST의 업무 경계 도달과 기존 GET 상세 조회 경로 2개는 통과했다.

잘못된 방식은 클라이언트 요청 문제다. 이를 500으로 표현하면 사용자 안내뿐 아니라 서버 장애 집계나 재시도 판단도 부정확해진다. 그러나 단순히 모든 예외를 4xx로 바꾸면 진짜 서버 오류를 숨기므로 예외 종류를 특정해야 한다.

## 3. 구현과 선택 기준

ErrorCode에 METHOD_NOT_ALLOWED를 추가하고, GlobalExceptionHandler에 HttpRequestMethodNotSupportedException 전용 메서드를 추가했다. 응답은 기존 공통 ApiResponse/ApiError 모양을 유지하며 HTTP 405, error.code METHOD_NOT_ALLOWED를 사용한다.

Allow 헤더는 문자열 POST로 하드코딩하지 않고 예외가 제공하는 헤더를 복사한다. 환불 재처리는 POST만 허용하지만 다른 컨트롤러에는 GET과 PATCH 등 여러 메서드가 있을 수 있으므로 프레임워크가 판단한 허용 목록을 유지해야 한다. 테스트 대상에서는 Allow: POST를 기대한다.

컨트롤러에 GET 별칭을 추가하거나 GET 요청을 POST로 바꾸는 대안은 제외했다. 조회 요청이 외부 금융 처리로 이어져서는 안 된다. 프런트가 올바른 HTTP 방식을 보내도록 수정해야 하는 상황에서도 서버가 편의를 위해 위험한 별칭을 만들지 않는다.

기존 B01에서 추가한 잘못된 JSON 본문의 400 처리와 다른 업무 예외 처리기는 그대로 보존했다. 이번 수정은 ErrorCode와 전역 처리기 두 곳에만 있으며 환불 오케스트레이션, 인증 규칙, 프런트 스타일은 변경하지 않는다.

## 4. 검증이 보장하는 것

HttpMethodBoundaryTest는 실제 환불 컨트롤러·MVC·전역 예외 처리기를 사용하고 RefundQueryService와 RefundOrchestrationService만 모킹한다. 잘못된 네 가지 메서드의 응답 상태·Allow 헤더·공통 오류 코드를 검사하고 두 업무 서비스가 한 번도 호출되지 않았는지도 확인한다. 환불이 안 나갔다는 것을 단순히 UI 메시지로만 판단하지 않는다.

정상 POST는 실제 금융 처리를 하지 않고 업무 경계에서 의도한 INVALID_STATE_TRANSITION을 발생시키도록 설정한다. 409와 서비스 한 번 호출을 확인하여 이 변경이 모든 재처리 요청을 통째로 막는 것은 아닌지 검사한다. 기존 GET 상세 조회는 RESOURCE_NOT_FOUND/404와 조회 서비스 호출을 확인한다.

Standalone MVC에는 인증 필터가 없다. 이 테스트만으로 관리자 인가를 확인했다고 주장하지 않는다. 실제 서명 JWT와 필터를 거치는 B20의 권한 통합 시험은 전체 회귀에서 함께 실행한다. 특히 인증되지 않은 요청은 MVC 도달 전에 401/403으로 차단될 수 있으며, 모든 호출에서 무조건 405가 먼저 나와야 하는 것은 아니다.

## 5. 결과와 남은 범위

수정 전 6개 중 4 실패/2 통과였으며, 수정 후 해당 6개 모두 통과했다. 전체 백엔드는 96개 클래스, 615개 중 612 통과, 실패·오류 0, 환경 제외 3개다. Gradle BUILD SUCCESSFUL과 XML 집계를 확인했다. 제외는 실제 Redis 게이트 2개·S3 1개이며, `B21-http-method-results.json`에도 수치를 기록했다. GitLab CI가 아닌 로컬 검증 결과다. 수정 전 증거와 수정 후 결과는 분리한다.

대표 POST 경로와 공통 예외 처리기를 검증한 것이며 전체 API의 모든 GET 부작용 여부를 완전 탐색한 결과는 아니다. 누락 헤더·잘못된 쿼리 파라미터 등 다른 MVC 예외를 한꺼번에 같은 코드로 처리하지 않는다. 외부 금융망, EC2 배포, 기존 실패 거래 재처리를 수행하지 않았다.

이 변경은 오류 의미와 업무 경계의 정합성 수정으로 성능 최적화가 아니다. 테스트 실행 시간이나 로그 감소를 처리량 개선 수치로 제시하지 않는다. 잘못된 요청을 서버 장애로 잘못 분류하지 않도록 하는 것이 이번 결과다.

## 6. 학습과 재실행

AdminRefundController의 @PostMapping → MVC 메서드 매칭 실패 → GlobalExceptionHandler의 구체적 예외 처리 → 공통 오류 응답 순서로 읽는다. 인증 실패, 라우팅 실패, 업무 실패, 서버 장애가 각각 다른 단계에서 생긴다는 점을 구분하면 이후 오류 매핑도 설계하기 쉽다.

backend에서 `./gradlew.bat test --tests '*HttpMethodBoundaryTest'`를 실행하고 `./gradlew.bat test`로 전체 회귀를 확인한다. 실제 환불 대상이나 금융망 키는 필요하지 않다.
