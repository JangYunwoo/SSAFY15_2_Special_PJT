# B18 결제 슬롯 발급의 모집 기간 검증

이슈 S15P21B205-195. 기준 DevelopV2 a9fa9c1. 카드·카카오페이·코인이 공유하는 결제 준비 트랜잭션의 기간 누락을 고친다.

## 1. 증상과 재현

공동구매 상태가 RECRUITING이지만 모집 시작이 내일인 경우에도 결제 준비를 요청하면 예약 슬롯과 PROCESSING 결제가 만들어졌다. 마감 직후 상태 전환 배치가 아직 실행되지 않았을 때도 같았다. 상태 필드는 모집 기간을 대신하지 못한다. 화면에서 버튼을 숨겨도 직접 요청하거나 열린 화면에서 마감을 맞는 경우를 막을 수 없다.

새 PaymentRecruitmentWindowTest는 실제 GroupBuy 엔티티와 고정 UTC Clock을 사용한다. 세 결제수단 각각 모집 1초 전·마감 정확히·마감 1초 후를 넣는 총 9개 오류 사례가 수정 전 모두 슬롯 발급을 허용하여 실패했다. 정확한 시작과 마감 1초 전의 정상 2개는 통과했다. 실제 외부 승인이나 송금은 호출하지 않는다.

## 2. 코드 원인과 경계 선택

PaymentService.prepare가 임시 게이트를 획득한 뒤 PaymentSlotReservationTransaction.execute에 위임한다. 트랜잭션은 공동구매 조회, 중복 참여/슬롯, 배송지, 수단을 확인하고 GroupBuy.reserveSlot을 호출한다. reserveSlot은 상태와 정원을 검사하지만 시각을 받지 않는다. 따라서 모집 기간을 판단하는 코드가 어느 쪽에도 없었다.

이번 수정은 쓰기를 수행하는 트랜잭션에서 서버 Clock을 읽고 `[recruitStartAt, recruitEndAt)`를 확인한다. 시작과 같으면 허용하고 종료와 같으면 거부한다. 애플리케이션 ClockConfig가 systemUTC를 제공하고 DB의 모집 기간은 UTC LocalDateTime 계약을 사용한다. 클라이언트 시각을 믿지 않는다.

## 3. 대안과 선택 이유

프런트 버튼만 막는 방법은 서버 검증을 대체하지 못한다. PaymentService의 게이트 이전 검사만 넣으면 사전 조회와 실제 쓰기 사이의 시간이 달라질 수 있다. 배치를 더 자주 실행해도 종료 후 짧은 시간의 불일치를 없애지 못한다. 모든 GroupBuySlotPort 응답에 시작 시각을 추가하는 대안은 구현 위치상 불필요했다. 현재 낙관적 락 트랜잭션이 실제 GroupBuy 엔티티를 직접 읽기 때문에 두 시각을 이미 갖고 있다.

따라서 별도 포트 계약 변경 없이 reserveSlot 직전에 검증하고 기존 GROUP_BUY_NOT_RECRUITING(409)을 반환했다. 기존 상태·정원 검사는 계속 GroupBuy가 책임진다. 거부 전에 예약 수를 올리거나 Payment를 저장하지 않는다. 한 번 얻은 now는 기존 TTL 계산에도 재사용하여 준비 시각을 일관되게 한다.

## 4. 기존 테스트가 드러낸 UTC fixture 문제

첫 전체 회귀에서는 PaymentServiceConcurrencyTest 한 건이 실패했다. 테스트 데이터가 recruit_start_at을 생략하고 DB 기본 현재시각에 의존했는데 테스트 JVM/DB 시각과 서버 UTC의 계약이 맞지 않았다. 동시성 시나리오 자체는 모집 중인 상품을 전제로 하므로 해당 fixture에 주입된 Clock 기준 시작 -1일과 종료 +1일을 명시했다. 정원 초과 assertion을 완화하지 않았다. 기존 mock의 정원 초과 단위 테스트에도 유효한 모집 기간을 지정했다. 실패를 숨긴 것이 아니라 새 계약에 맞게 테스트 전제조건을 명확히 한 것이다.

## 5. 테스트 설계와 결과

단위 경계 11개는 오류 코드뿐 아니라 실제 엔티티 reservedSlots가 0이며 saveAndFlush가 호출되지 않는지 확인한다. 정상 시작의 카드 TTL 300초도 유지한다. 추가 PaymentRecruitmentWindowIntegrationTest 2개는 Spring 프록시, 실제 JPA repository, 격리 H2 DB로 시작 전/마감 후 경로를 실행하여 DB의 reserved_slots=0, payments count=0을 확인한다. fixture는 테스트 트랜잭션 종료 때 롤백한다.

최종 전체 회귀 결과는 654개 중 651개 통과, 실패 0, Redis/S3 관련 3개 건너뜀이다. 기존 10명 동시 요청 중 단 한 명만 슬롯을 받는 경합 테스트도 통과했다. 단위 재현은 수정 전 11개 중 9개 실패 → 수정 후 통과다. 환경은 Windows, Java 21, 프로젝트 Gradle Wrapper다. 이는 처리 정합성 검증이며 TPS나 응답시간 최적화 지표가 아니다.

## 6. 범위와 한계

이 변경은 새 슬롯 발급 시점을 제한한다. 마감 전에 발급받은 슬롯의 기존 5분/10분 TTL을 임의로 줄이지 않았으며 마감 직전 발급 후 승인·마감 배치가 경합하는 정책을 새로 정의하지 않는다. 이미 유효한 결제를 같은 요청 키로 조회하는 동작도 PaymentService의 별도 멱등성 계약을 유지한다. TTL과 모집 마감의 우선순위 전체는 승인/만료 경합 검증에서 함께 판단해야 한다.

H2/로컬 게이트 테스트는 실제 Redis Lua와 MySQL 운영 부하를 대신하지 않는다. Docker 보류로 관련 환경 테스트는 수행하지 않았고, 실제 카카오 인증·체인 송금·배포 서버 데이터도 변경하지 않았다. 이전 실패 거래를 자동 재실행하지 않는다.

## 7. 재실행 및 학습 순서

backend에서 Java 21로 `./gradlew.bat test --tests '*PaymentRecruitmentWindow*' --console=plain`을 실행하고, 전체는 `./gradlew.bat test --console=plain`을 실행한다. XML/HTML 결과는 build/test-results/test와 build/reports/tests/test에 생성된다.

학습 순서는 PaymentService.prepare의 게이트 보상 → PaymentSlotReservationTransaction의 검증 순서 → GroupBuy.reserveSlot의 정원 책임 → 고정 Clock 경계 테스트 → 실제 JPA 통합 테스트다. 단순 상태 문자열 검사와 시간 조건, 데이터 변경 전 검증의 차이를 이 순서로 확인할 수 있다.
