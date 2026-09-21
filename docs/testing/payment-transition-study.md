# 결제 종료 경합과 슬롯 중복 반환 수정

이슈: S15P21B205-188. B16의 공통 결제 상태 변경 경합을 다룬다. 기준 코드는 DevelopV2 aa19698이며 배포본 DevelopV1, 실제 결제, 운영 데이터는 변경하지 않는다.

## 증상과 문제의 범위

결제 슬롯 하나를 취소했는데 예약 인원이 두 번 감소하면 다른 사람의 자리를 다시 판매할 수 있다. 기존 코드는 PROCESSING인지 확인한 다음 공구 슬롯을 반환하고 FAILED로 바꾼다. 한 요청만 실행하면 정상이다. 그러나 요청 A와 B가 모두 변경 전 PROCESSING을 읽으면 각각 반환을 실행한다. `if (!payment.isProcessing())` 검사는 이미 종료된 행을 재조회했을 때만 도움이 되며, 동시에 읽은 두 트랜잭션을 직렬화하지 못한다.

이 문제는 버튼 연타뿐 아니라 사용자 취소, 외부 결제 실패, 슬롯 만료 배치, 승인 완료가 서로 겹칠 때도 발생할 수 있다. 공구 행의 잠금만으로는 부족하다. 공구 카운터 감소가 순서대로 실행되어도 각 요청이 결제의 이전 상태를 이미 읽었다면 감소를 두 번 수행할 수 있기 때문이다.

## 선택한 수정과 다른 대안

PaymentRepository에 결제 ID 기준 PESSIMISTIC_WRITE 조회를 추가했다. 소유자 검증이 필요한 취소는 기존 소유자+ID 잠금 조회를 사용한다. expireSlotIfDue, failPayment, completePayment도 같은 결제 행 잠금 규칙을 따른다. 기존 abortPayment와 completeOwnedPayment는 이미 잠금 조회를 사용하므로 그대로 둔다. 조회 전용 getPayment/listHistory에는 잠금을 추가하지 않는다.

같은 결제에 대한 두 번째 요청은 첫 번째 트랜잭션의 종료를 기다린 뒤 최신 상태를 확인한다. 첫 요청이 커밋하면 두 번째 요청은 종료 상태를 확인하여 슬롯을 반환하지 않는다. 첫 요청이 롤백하면 두 번째 요청이 아직 진행 중인 결제를 처리할 수 있다. 잠금은 DB 트랜잭션 범위이며 JVM 내부 synchronized가 아니므로 여러 서버 인스턴스에서도 같은 DB 행을 기준으로 조율한다.

낙관적 잠금도 대안이지만 충돌 예외가 발생하기 전에 Redis 등 DB 밖의 부수 효과가 실행될 수 있어 이번 최소 수정에는 선택하지 않았다. 공구 전체를 먼저 잠그는 방법은 서로 다른 결제까지 불필요하게 경쟁시키며 결제 상태의 소유권을 명확히 표현하지 못한다. DB 조건부 UPDATE로 실행권을 얻는 방법도 가능하지만 JPA 상태 변경 흐름과 재조회 계약을 더 크게 바꾸므로 이번 버그의 최소 범위에는 비관적 잠금이 적합했다.

## 구현을 읽는 순서

1. PaymentRepository의 findForUpdateByPaymentId와 findForUpdateByPaymentIdAndUserId에서 잠금 대상이 결제 한 행임을 확인한다.
2. PaymentService의 abort → expireSlotIfDue → failPayment → completePayment에서 잠금 조회 후 상태 검사를 하는 순서를 확인한다.
3. 각 메서드의 @Transactional을 확인한다. 트랜잭션 없이 잠금 조회만 추가하면 상태 변경까지 보호할 수 없다.
4. PaymentTransitionIntegrationTest의 두 스레드와 latch를 읽는다. 잠깐 기다리는 것을 서비스 속도 측정으로 해석하지 않는다. 첫 요청이 미커밋인 동안 두 번째 요청이 같은 상태를 처리하지 못한다는 동시성 검증이다.

## 수정 전후 검증 설계와 결과

Windows/Java 21/프로젝트 Gradle Wrapper/H2 MySQL 호환 모드에서 실제 Spring 트랜잭션과 JPA 저장소를 실행했다. 결제 행은 테스트 DB에 생성한다. 첫 취소를 슬롯 반환 지점에서 멈추고 두 번째 취소 또는 실패 처리를 시작한다. 첫 요청의 커밋 전에는 두 번째 요청이 끝나지 않아야 하고, 종료 후 슬롯 반환 및 게이트 반환은 각각 정확히 1회, 최종 결제 상태는 FAILED여야 한다.

| 관찰 대상 | 수정 전 | 수정 후 |
| --- | --- | --- |
| 취소 2개 동시 요청 | 대기 조건 불충족, 회귀 테스트 실패 | 두 번째 요청 대기, 반환 1회 |
| 취소와 실패 동시 요청 | 대기 조건 불충족, 회귀 테스트 실패 | 두 번째 요청 대기, 반환 1회 |
| 전체 백엔드 회귀 | 새 테스트 2개 실패로 문제 재현 | 총 638개 중 635개 통과, 3개 건너뜀, 실패/오류 0 |

기존 PaymentServiceTest는 상태 변경 경로가 새 잠금 조회를 사용하도록 mock 기대값을 바꿨다. 상태·반환 횟수에 대한 기존 검증을 제거하지 않았다. `git diff --check`도 통과했다. 이 변경은 정합성 수정이지 성능 최적화가 아니다. 테스트 실행 시간을 처리량이나 응답 시간의 개선 수치로 주장하지 않는다. 같은 결제의 경합 요청은 의도적으로 잠금 대기 비용을 부담한다.

## 증거의 한계와 후속 항목

이 통합 테스트의 GroupBuySlotPort와 GroupBuySlotGate는 mock이다. 따라서 실제 JPA 결제 행 잠금과 호출 중복 방지는 검증하지만 실제 MySQL 공구 카운터, Redis 원자성, 분산 장애 복구를 검증했다고 주장하지 않는다. Redis 2개/S3 1개는 사용자가 Docker 작업을 미룬 상태여서 기존대로 건너뛰었다. DB 롤백과 Redis 반환 간 불일치 문제 역시 이 행 잠금 하나로 해결되지 않는다.

외부 카드 승인은 이 트랜잭션보다 앞서 실행되므로 중복 금융 호출이나 승인 중 슬롯 만료 문제는 별도 실행권 수정이 필요하다. 이 MR을 카드·카카오·코인 전체 금융 실행의 exactly-once 보장으로 해석하면 안 된다. 마찬가지로 배포 서버는 아직 이 코드를 사용하지 않는다.

backend에서 `./gradlew.bat test --tests '*PaymentTransitionIntegrationTest' --console=plain`으로 집중 재현하고 `./gradlew.bat test --console=plain`으로 전체 회귀를 실행한다. 결과 XML은 build/test-results/test, HTML은 build/reports/tests/test/index.html에 생성된다.
