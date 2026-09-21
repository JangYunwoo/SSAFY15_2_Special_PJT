# C02 코인 실패 후 슬롯 게이트 반환 누락

이슈 S15P21B205-199. 기준 DevelopV2 bd82103. 실제 체인 송금·환불은 실행하지 않는다.

## 증상과 두 종류의 예약 수

슬롯은 DB의 group_buys.reserved_slots와 앞단 GroupBuySlotGate의 예약 수를 함께 관리한다. 게이트는 Redis 또는 허용된 로컬 대체 구현으로 빠른 정원 검사를 하고 DB는 최종 상태를 저장한다. 기존 CryptoTxRecordService의 전용 failPayment는 payment.markFailed와 groupBuySlotPort.release만 호출했다. DB 예약은 0이 되는데 게이트 예약은 남아, 실제 자리가 비었어도 이후 구매자에게 정원 초과가 보일 수 있었다. 결제를 FAILED로 먼저 끝내므로 PROCESSING 대상 만료 배치가 이 예약을 정리해 줄 것이라고 기대할 수도 없다.

카드·카카오페이용 공통 PaymentService.failPayment에는 이미 결제 행 잠금 → PROCESSING 재확인 → DB 슬롯 해제 → 게이트 해제 → FAILED 전이가 구현되어 있었다. 코인 경로만 이를 사용하지 않은 것이 원인이다.

## 대안과 결정

코인 서비스에 GroupBuySlotGate를 추가해서 release 한 줄을 붙이는 방법은 단순하지만 실패 상태 확인, 잠금, DB/게이트 순서를 또 복사하게 된다. 모든 슬롯을 주기적으로 초기화하는 것은 살아 있는 다른 결제 예약을 잃게 할 위험이 있다. 따라서 기존 공통 실패 경로를 호출하고 코인에 남아 있던 직접 실패 helper와 불필요해진 슬롯 포트 의존성을 제거했다.

코인 기록의 최초 Payment 조회도 findForUpdateByPaymentId로 바꿨다. 일반 조회로 PROCESSING을 읽고 나중에 실패 때만 잠그면, 그 사이 다른 요청이 종료한 결제를 영속성 컨텍스트에서 오래된 상태로 들고 있을 수 있다. record, refreshConfirmations, applyLateReceipt의 세 쓰기 경로에서 결제 상태를 잠금 하에 읽고 처리한다. RPC는 여전히 검증 서비스/스케줄러가 트랜잭션 밖에서 수행한다. 잠금을 잡은 채 체인을 호출하지 않는다.

## 실제 변경 흐름

즉시 revert, 뒤늦은 revert, 컨펌 대기 중 슬롯 만료, 뒤늦은 영수증 내용 오류의 네 경로가 paymentService.failPayment(paymentId, reason)를 호출한다. 같은 Spring 트랜잭션 안에서 공통 서비스의 잠금과 상태 변경이 실행된다. 이미 종료된 결제는 반환하고 다시 예약을 해제하지 않는다. 입금 금액/토큰/주소 대조, 미달·초과 입금 기록, 컨펌 계산은 변경하지 않았다.

## 재현 설계와 결과

CryptoFailureSlotIntegrationTest는 실제 Spring 서비스, JPA repository, H2 격리 DB를 쓰고 게이트만 Mockito 관찰 대체물로 둔다. 각 사례는 PROCESSING 코인 결제와 예약 1, 실제 자산/견적/수취지갑 행을 만든다. 체인 영수증은 테스트 인자로 제공하므로 외부 네트워크를 호출하지 않는다.

네 경로 모두 FAILED, DB reserved_slots=0, gate.release 정확히 1회를 검사한다. 동일한 late receipt/confirmation을 다시 호출해도 release가 늘지 않아야 한다. 수정 전 네 사례 모두 DB 상태 assertion은 통과했지만 gate 호출 0회로 실패했다. 이것이 단순히 실패 문구만 보는 테스트로 발견하기 어려운 이유다. 수정 후 전체 백엔드 649개 중 646개 통과, 실패 0, Redis/S3 3개 건너뜀이다.

기존 CryptoTxRecordServiceTest의 서비스 mock은 공통 서비스 내부 상태를 변경하지 않는다. 따라서 mock PaymentService에 대해 FAILED라는 메모리 상태를 기대하던 두 assertion을 정확한 공통 실패 메서드 호출 확인으로 바꿨다. 실제 FAILED/DB 슬롯 변경은 새 통합 테스트가 확인한다. 서로 다른 테스트 계층의 책임을 혼동하지 않았다.

## 한계와 성능에 대한 해석

게이트 mock은 호출 누락/중복을 확인하지만 실제 Redis 서버 장애, Lua 원자성, 네트워크 응답 유실을 검증하지 않는다. 사용자 요청으로 Docker 환경은 보류된 상태다. DB와 Redis는 분산 트랜잭션이 아니므로 공통 경로의 DB 롤백과 외부 게이트 반환 실패/재시도에 대한 장기 대조 정책까지 이 변경 하나로 해결했다고 할 수 없다. 실제 체인 reorg나 지갑 서명 소유권 검증도 별도 범위다.

행 잠금은 정합성 안전망이며 이번 작업은 성능 최적화가 아니다. 테스트 실행 시간으로 응답 시간이나 TPS 향상을 주장하지 않는다. 운영 MySQL 부하 수치는 측정하지 않았다. DevelopV1이나 배포 DB를 변경하지 않는다.

## 재실행과 학습 순서

backend에서 Java 21로 `./gradlew.bat test --tests '*CryptoFailureSlotIntegrationTest' --console=plain`, 전체는 `./gradlew.bat test --console=plain`을 실행한다. 결과는 build/test-results/test와 build/reports/tests/test에 있다. 코인 전용 helper가 왜 공통 실패 경로와 달랐는지 → 공통 PaymentService.failPayment → record의 최초 잠금 → 새 통합 테스트의 DB와 게이트 이중 assertion 순서로 읽으면 좋다.
