# 판매 철회 전원 환불의 원장 누락과 중단 복구

이슈: S15P21B205-217. 기준 브랜치는 DevelopV2의 판매 철회 후보 수정 병합본 `24fcf68`이다. DevelopV1, 배포 DB, 실 금융망은 변경하지 않는다.

## 왜 별도 버그인가

이전 C06 수정은 판매 철회의 위반 후보를 공구 취소와 함께 저장하여 후보 누락을 막았다. 그러나 환불 작업 자체는 여전히 취소 준비 후 참여자를 한 명씩 처리하면서 그때그때 원장을 만들었다. 첫 외부 호출에서 예외가 발생하면 for 루프를 빠져나가므로 뒤 참여자는 REFUNDING이지만 refunds 행이 없는 상태가 된다. 프로세스가 첫 호출 전에 종료되어도 같은 문제가 생긴다.

기존 복구는 이미 존재하는 미송신 원장과 모집 실패 공구(FAILED)의 PAID 참여를 찾는다. 판매 철회 공구는 CANCELED이고 참여는 REFUNDING이므로, 원장이 없는 뒤 참여자는 이 두 조건 어디에도 걸리지 않는다. 재시도 버튼만 추가해도 어떤 참여자에게 이미 송금했는지 확정할 원장이 없다면 안전한 복구가 되지 않는다.

## 수정 전 재현

실제 Spring 서비스와 H2 트랜잭션을 사용하고 금융망 클라이언트만 mock으로 바꿨다. 판매자 1명, 구매자 2명, 모집 중 공구와 승인 결제·에스크로를 만든 뒤 다음을 검사했다.

| 재현 | 기대 | 수정 전 관찰 |
| --- | --- | --- |
| 철회 준비 커밋 직후 | 2명의 PROCESSING 환불 원장이 모두 존재 | 원장 0개로 실패(expected 2, actual 0) |
| 첫 외부 호출이 예상 밖 예외를 던짐 | 뒤 환불 작업도 실행 가능한 상태 유지 | `IllegalStateException: QA provider response lost`가 withdraw 밖으로 전파되어 실패 |

이 두 재현 테스트는 수정 전 제품 코드에서 모두 실패했다. 처음 작성한 fixture의 테스트 계좌 문자열이 컬럼 길이를 넘어서 발생한 실패는 별도로 수정했으며 제품 버그 재현으로 계산하지 않았다. 위 표는 fixture를 바로잡은 다음 실행한 결과다.

## 선택한 구현과 제외한 대안

`GroupBuyWithdrawalService.prepareWithdrawal`이 공구 취소·참여 REFUNDING·위반 후보뿐 아니라 **대상 전원의 환불 원장**까지 같은 트랜잭션으로 예약한다. 결제는 PAID인지 확인하며 금액은 승인 결제의 확정 KRW 금액을 쓴다. 기존 수단 매핑인 카드→ACCOUNT_TRANSFER, 카카오페이→KAKAOPAY, 코인→CRYPTO는 유지한다. 새로운 카드 취소/이체 정책을 만든 변경이 아니다.

한 명의 예약이라도 실패하면 취소, 참여 전이, 앞서 예약한 원장, 후보가 함께 롤백된다. 아직 금융 호출이 없기 때문에 안전하게 이전 상태를 유지할 수 있다. 커밋에 성공한 뒤 반환된 refundId들을 `resumePersisted`로 실행한다. 원장을 다시 열지 않고 기존 ID를 사용하므로 최초 요청과 복구 작업이 같은 작업을 가리킨다.

외부 실행 루프는 한 원장의 RuntimeException을 잡아 안전한 식별자와 예외 종류만 기록하고 다음 원장으로 진행한다. 외부 응답 본문이나 계좌 원문은 새 로그에 넣지 않는다. 실행 전에 끝난 장애는 미송신 원장으로 남아 복구가 가능하고, 실행권을 잡은 뒤 결과가 불명확한 장애는 실행권이 남아 자동 재송금되지 않는다. 실행 중인 환불을 임의로 FAILED로 덮거나 실행권을 해제하지 않는다.

`withdraw`에는 NEVER 트랜잭션을 지정해 외부 호출이 상위 DB 트랜잭션에 끼어드는 것도 거부한다. 기존 self proxy로 부르는 prepareWithdrawal만 짧은 쓰기 트랜잭션이다. 외부 요청 중에는 이 트랜잭션과 잠금을 유지하지 않는다.

단순히 for 루프에 try/catch만 추가하는 대안은 프로세스 종료 후 뒤 작업을 잃으므로 제외했다. 실패마다 새 원장을 만들어 다시 송금하는 대안은 응답 유실 이후 이중 지급 위험 때문에 제외했다. 별도 메시지 브로커나 새 outbox 테이블은 기존 refunds가 이미 영속 작업과 실행권을 표현하고 있으므로 이번 버그 수정에 추가하지 않았다.

## 기존 복구와 연결되는 방식

`RefundRepository.findUnsent`는 참여 환불 원장 중 PROCESSING, executionClaimed=false, CARD_CANCEL 아님, 생성 후 30초 경과 조건을 사용한다. `RefundRecoveryService.run`은 이 ID를 같은 `resumePersisted`에 넘긴다. 이번 변경은 새로운 복구 스케줄러나 금융 호출 경로를 만들지 않고 이 기존 계약에 판매 철회 원장을 넣는다.

최초 HTTP 요청이 실행 중일 때 스케줄러가 같은 원장을 발견할 수 있다. 공통 실행권은 외부 송금 전에 DB에서 하나의 요청만 획득한다. 이미 완료된 원장은 내부 참여/에스크로 종결만 확인하며 송금하지 않는다. UNKNOWN에 해당하는 실행권 보유 원장과 실패 원장을 자동 재송금하도록 조건을 완화하지 않았다.

HTTP 철회 응답 성공은 '취소 및 전원 환불 개시가 영속화됐다'는 뜻이지 '모든 구매자에게 이미 반환 완료됐다'는 뜻이 아니다. 실제 결과는 환불 원장의 PROCESSING/COMPLETED/FAILED 및 실행권 상태로 확인한다. 중간 외부 장애를 HTTP 재호출로 무조건 재송금하는 대신 원장에 남긴 작업을 안전하게 복구한다.

## 검증 시나리오

신규 단위 테스트 2개는 예약 작업이 먼저 끝난 뒤 각 refundId를 실행하는 순서, 첫 예외 후 두 번째 실행, 실행 도중 원장을 새로 여는 옛 메서드를 호출하지 않는 것을 확인한다.

신규 실제 트랜잭션 통합 테스트는 9개다. 전원 원장 선행 예약, 첫 금융 예외 후 다른 구매자 완료와 결과불명 재송금 금지, 준비 직후 프로세스가 종료된 상황의 DB 스캔 복구, 준비 실패의 전체 롤백, 최초 요청과 복구의 경합에서 구매자당 한 번의 외부 호출, 과거 원장 없는 취소의 자동 송금 금지, 카카오페이·코인 수단 매핑 2개, 상위 트랜잭션에서 금융 실행 거부를 검사한다.

경합 테스트는 첫 외부 송금을 latch로 멈춘 상태에서 원장들을 복구 조회 가능 시각으로 조정하고 복구를 실행한다. 복구가 두 번째 구매자를 먼저 처리하더라도 원래 요청이 다시 같은 ID에 접근했을 때 추가 송금은 없어야 한다. 최종 COMPLETED 원장 2개와 총 외부 호출 2번을 함께 확인한다. 정상 반환 상태만 보는 것보다 이중 송금을 더 직접적으로 검증한다.

기존 철회 후보 테스트는 외부 실행 인터페이스가 refundParticipation에서 resumePersisted로 바뀐 점과 외부 예외 격리 계약에 맞게 수정했다. 후보 저장·롤백 보장은 유지한다. 모집 실패 복구 테스트도 함께 실행하여 기존 FAILED 공구 복구가 영향을 받지 않았는지 확인한다.

## 과거 원장 없는 취소는 자동 재송금하지 않는다

최종 검증 결과: Java 21/H2 전체 백엔드 회귀 123개 스위트, 794개 중 791개 통과·3개 스킵, 실패·오류 0개. 신규 통합 테스트 9개 및 신규 단위 테스트 2개도 모두 통과했다. 실제 Redis/S3 환경 조건에 따른 기존 스킵 3개를 통과로 세지 않았다. 전체 실행은 3분 51초였고 `git diff --check`도 통과했다. 재실행은 backend에서 `./gradlew.bat test --tests '*WithdrawalRefundRecoveryIntegrationTest' --tests '*GroupBuyWithdrawalServiceTest' --tests '*WithdrawalPenalty*Test' --tests '*FailedRecruitmentRecoveryIntegrationTest'`, 이후 `./gradlew.bat test` 순서다.

과거 CANCELED+REFUNDING+refunds 없음, WITHDRAWAL 후보 존재를 발견하더라도 이것만으로 '외부 반환이 없었다'는 증명은 아니다. 오래된 시스템이 원장을 남기지 못하고 외부 처리를 했을 가능성을 배제할 수 없다. 따라서 이번 배포가 과거 거래를 새로 송금하는 스캔을 추가하지 않는다. 테스트도 후보가 있는 과거 모양의 데이터를 만들고 복구가 자동 송금하지 않는지 확인한다.

운영자는 아래와 같은 **조회만**으로 검토 대상을 찾을 수 있다. 이 문서의 SQL은 저장 데이터를 바꾸지 않고 자동 실행되지 않는다. 계좌번호나 비밀값을 조회할 필요 없이 먼저 거래 ID로 범위를 좁힌다.

```sql
SELECT g.group_buy_id, p.participation_id, pay.payment_id, pay.krw_amount,
       p.status AS participation_status, pay.status AS payment_status
FROM group_buys g
JOIN participations p ON p.group_buy_id = g.group_buy_id
JOIN payments pay ON pay.participation_id = p.participation_id
WHERE g.status = 'CANCELED' AND p.status = 'REFUNDING'
  AND pay.status = 'PAID'
  AND NOT EXISTS (SELECT 1 FROM refunds r WHERE r.participation_id = p.participation_id)
ORDER BY g.group_buy_id, p.participation_id;
```

이 결과는 환불 실행 지시가 아니라 대조 목록이다. 관리자가 기존 금융망·PG·체인 결과와 수동 처리 이력을 확인하고, 미지급을 확정한 대상에 대해 별도 복구를 승인해야 한다. 이번 MR에는 과거 원장의 일괄 생성/재송금 관리자 API를 추가하지 않는다.

## 성능과 별개 버그의 경계

이번 작업은 정합성 복구이며 성능 최적화가 아니다. 정상적으로 전원 환불하던 요청에 필요한 원장 수와 외부 호출 수는 동일하지만 N명의 원장 INSERT가 하나의 준비 트랜잭션에 모인다. 거래별 저장 실패가 전체 취소를 롤백할 수 있다는 비용을 영속 작업의 누락 방지와 교환했다. 실제 처리량이나 잠금 대기 p95는 측정하지 않았으므로 빠르다고 표현하지 않는다. 테스트 수행 시간도 API 성능 수치로 쓰지 않는다.

읽기 점검에서 판매 철회의 reservedSlots=0 처리 뒤 PROCESSING 결제의 abort/fail/expire가 슬롯 없음 예외로 롤백되는 별도 경계도 확인했다. 모집 실패 판정도 같은 초기화 구조를 쓴다. 이는 환불 원장 누락과 다른 슬롯 수명주기 문제이므로 Jira218의 별도 변경으로 분리했고 이 MR에서 슬롯 카운터·승인 규칙을 고치지 않는다.

실제 MySQL·Redis·S3 통합은 Docker 보류 상태이며, 실제 금융망으로 새 송금을 발생시키지 않았다. H2와 제어한 금융 응답으로 트랜잭션·실행권·복구를 검증한 결과와 실 외부 서비스 검증은 구분해야 한다. 학습 시 준비 트랜잭션 → refunds 실행권 → 외부 실행 → 내부 종결 → 미송신 복구 순서로 읽으면 된다.

## 2026-09-22 검토 보강: 개인 취소와 판매 철회의 잠금 순서

원장 예약만 같은 트랜잭션으로 모아도 안전성 검토가 끝난 것은 아니었다. 개인 참여 취소는 참여 행을 먼저 잠그고 공구 행을 잠근다. 판매 철회가 공구 행부터 잠근 다음 참여를 변경하면, 구매자는 참여 잠금을 가진 채 공구를 기다리고 판매자는 공구 잠금을 가진 채 참여를 기다릴 수 있다. 양쪽 모두 올바르게 비관 잠금을 사용하더라도 순서가 반대면 교착이 된다. 또한 일반 조회로 받은 PAID 참여를 나중에 갱신하면 이미 개인 취소된 상태나 환불 사유를 덮을 위험도 있다.

이를 실제 Spring/JPA 경합으로 재현했다. 첫 트랜잭션에서 구매자의 참여 행을 잠그고 대기시킨 후, 다른 스레드에서 판매 철회를 실행했다. 판매 철회가 대기하는 동안 첫 트랜잭션이 실제 ParticipationCancelService.prepareCancel을 호출하도록 했다. 기존 공구 우선 순서에서 같은 테스트는 **1개 중 1개 실패**했고 H2가 참여 UPDATE에서 `Deadlock detected [40001-240]`를 보고하며 트랜잭션을 롤백했다. 테스트 목을 잘못 설정해서 생긴 예외가 아니라 실제 DB 잠금 경합이었다. 비교를 마친 뒤 수정 코드를 복원했다.

수정된 준비 과정은 공구 version 스칼라 조회, 참여 ID 오름차순 스칼라 조회, 각 참여의 비관 잠금, 공구의 비관 잠금 순서다. 엔티티를 미리 일반 조회하지 않는 이유는 영속성 컨텍스트의 1차 캐시에 들어간 예전 상태가 뒤 잠금 조회에서 재사용되는 것을 피하기 위해서다. 잠근 참여의 최신 상태가 PAID인 것만 판매 철회 환불 대상으로 삼는다. 이미 개인 취소된 참여는 그 상태와 PARTICIPATION_CANCEL 원장을 그대로 보존한다.

참여 잠금을 먼저 잡는 것만으로는 새 참여의 누락을 해결하지 못한다. ID 목록을 읽은 직후 새 구매자가 결제를 확정하면 그 ID는 이미 읽은 목록에 없다. 그래서 목록보다 먼저 공구 version을 읽고 최종 공구 잠금에서 읽은 version과 비교한다. 정상 결제 확정은 공구의 참여 인원 증가와 참여 INSERT를 같은 트랜잭션으로 커밋하므로 이 사이 새 참여가 확정되면 version도 달라진다. 개인 취소나 다른 공구 변경도 version을 바꾸므로 보수적으로 재확인한다. 관리자가 DB를 직접 수정하면서 version 갱신을 생략하는 작업까지 자동 보장하는 장치는 아니다.

version 차이가 나면 전용 WithdrawalSnapshotChangedException으로 준비 트랜잭션을 전부 롤백한다. 트랜잭션 밖의 withdraw에서 최대 3번 프록시 메서드를 다시 호출한다. 같은 트랜잭션에서 SELECT만 반복하는 방식은 채택하지 않았다. MySQL REPEATABLE READ에서는 첫 일반 조회의 스냅샷이 계속 보일 수 있기 때문이다. 애플리케이션의 `open-in-view: false` 설정과 새 트랜잭션 경계를 통해 새 영속성 컨텍스트에서 목록을 다시 읽는다. 세 번 모두 충돌하면 잠시 후 재요청하라는 오류를 반환하고 금융 호출은 하지 않는다. 권한 오류, 입력 오류, SQL 오류 전체를 무조건 재시도하지 않는다.

추가한 두 통합 시나리오는 다음과 같다. 첫째, 개인 취소가 참여 잠금을 먼저 잡은 상태에서 철회를 실행해 교착 없이 양쪽을 완료하고, 첫 구매자의 사유는 PARTICIPATION_CANCEL, 다른 구매자는 SELLER_FAULT이며 판매 철회 금융 호출은 한 번인지 확인한다. 둘째, 철회가 참여 목록을 확보한 뒤 공구 잠금 직전에 멈춘 동안 별도 트랜잭션에서 JPA 공구 확정과 새 참여/결제/에스크로 INSERT를 커밋한다. 철회가 새 트랜잭션에서 version을 두 번째로 조회하고 세 구매자 모두를 원장에 포함하며 금융 호출도 정확히 세 번인지 확인한다. 테스트에서 순서를 멈추기 위한 저장소 spy는 실제 저장소 구현에 위임하며, DB 동작이나 잠금 결과를 가짜로 반환하지 않는다.

단위 테스트 세 개도 추가했다. 전용 충돌 한 번 뒤 성공한 예약만 실행하는지, 세 번 충돌하면 실행 없이 중단하는지, 무관한 준비 오류는 재시도하지 않는지를 확인한다. 보강 직후 집중 회귀는 기존 원장/후보 시나리오까지 합쳐 21개가 통과했다. 실제 MySQL의 격리 수준별 잠금 동작은 별도 검증 대상이며 H2 결과를 MySQL 실측으로 표현하지 않는다.

성능상 이 변경은 읽기 쿼리를 줄이는 최적화가 아니다. 정상 철회 시 종전 공구 잠금 1회+PAID 목록 조회 1회 대신 version 조회 1회+ID 목록 조회 1회+참여 N개 잠금 조회+공구 잠금 1회를 수행한다. 과거 종료 참여도 같은 공구의 ID 목록에 포함되므로 N은 PAID 수만 뜻하지 않는다. 변경이 감지되면 최대 3회 이 준비 비용을 낸다. 대신 잠금 순서를 통일하고 환불 대상의 누락·덮어쓰기를 막는다. 외부 호출 수는 최종 예약된 구매자별 1회 원칙을 유지하며 처리량/p95 개선을 주장하지 않는다.

### 보강 이후 통합 검증 기록

앞 본문의 794개 결과는 9월 21일 원장 예약 수정 시점 기록이다. 9월 22일 잠금 보강 후 이전 기준에서 전체 회귀는 **799개 중 796개 통과, 3개 스킵, 실패 0개**였다(123개 스위트, 1분 48초).

이후 DevelopV2 `017cc73`으로 재배치한 전체 실행은 **866개 중 847개 통과, 16개 실패, 3개 스킵**이었다(2분 48초). 첫 실패는 ActorBoundaryIntegrationTest의 Spring 컨텍스트 생성 중 OutOfMemoryError였고 같은 컨텍스트의 나머지 15개도 초기화 실패로 이어졌다. 전체 성공으로 간주하지 않는다. 이 결과만으로 제품 권한 로직이 실패했다고 단정하지도 않는다. 이번 철회 경합 테스트는 해당 실행에서 통과했고, 실패했던 접근제어 테스트는 별도 작은 실행으로 확인한다. 테스트 JVM의 힙 크기/컨텍스트 캐시 조정은 최종 통합 실행 담당자가 따로 기록하며 이 MR에서 무관한 빌드 설정을 바꾸지 않는다.

최종 코드 기준은 DevelopV2 `635a787`이다. 재배치 후 WithdrawalRefundRecoveryIntegrationTest, GroupBuyWithdrawalServiceTest, WithdrawalPenaltyIntegrationTest, ActorBoundaryIntegrationTest를 함께 실행해 **37개 전부 통과, 스킵·실패·오류 0개**를 확인했다(44초). 따라서 접근제어 16개는 분리 실행에서 정상 완료됐으나, 이것을 OOM이 있었던 전체 실행의 성공으로 대체하지 않는다. 최종 병합 통합 전체 실행은 별도로 수행해야 한다. `git diff --check`도 통과했다.

### 테스트 실행 자원 보강 후의 전체 재검증

같은 제품 코드 `b601e17`에서 Gradle init-script로 **테스트 JVM에만 최대 힙 2GB와 Spring 컨텍스트 캐시 8개**를 지정해 전체 실행을 추가했다. 제품 JVM·application 설정·build.gradle은 바꾸지 않았다. 결과는 887개 중 864개 통과, 20개 실패, 3개 스킵(2분 13초)이었다. OOM은 발생하지 않았지만 ReturnRequestRepositoryTest 8개와 SettlementBatchIntegrationTest 12개가 fixture 준비에서 실패했다.

이번 실패를 제품 SQL 오류라고 뭉뚱그리지 않고 XML의 원인을 확인했다. 실제 원인은 `Table users not found (this database is empty)`와 settlement_items 테이블 부재였다. 공용 테스트 프로필은 `jdbc:h2:mem:gongguhan`과 `ddl-auto: create-drop`을 공유한다. 같은 DB를 쓰는 서로 다른 Spring 컨텍스트가 캐시에서 축출되어 종료되면, 다른 살아 있는 컨텍스트가 쓰는 테이블까지 drop할 수 있다. 따라서 캐시를 무조건 작게 만드는 것은 이 테스트 구조에 맞지 않았다. 캐시 유지 한도를 충분히 확보해 다시 검증하며, 이 20개 실패도 성공으로 세지 않는다.

같은 코드에서 최대 힙 2GB, 캐시 64개로 바꾼 전체 실행은 **887개 중 882개 통과, 2개 실패, 3개 스킵**이었다(2분 25초). OOM과 테이블 부재는 사라졌지만 SellerGroupBuyIntegrationTest의 두 시험이 실패했다. 원문을 확인하면 생성 API 자체는 두 번 모두 HTTP 201로 정상 동작했다. 생성 응답의 ID는 920005/920006인데 테스트가 전역 `findAll().getFirst()`로 가져온 ID 1에 조회를 요청해 RESOURCE_NOT_FOUND 404를 받았다. 다른 시험도 전역 findAll의 크기가 1이어야 한다는 전제에서 실제 2개가 있어 실패했다. 따라서 실제로 생성한 공구를 찾지 않고 다른 클래스의 잔여 공구를 고른 테스트 데이터 격리 문제를 제품 생성 실패와 구분해야 한다.

실패 XML의 SellerGroupBuy 스위트 시작 시각은 2026-09-22T00:20:55.494Z였다. 바로 앞 공구 관련 순서는 GroupBuyQuery(00:20:54.844Z), GroupBuyRecruitment(00:20:55.280Z), GroupBuySlotConcurrency(00:20:55.435Z)였다. 코드에서 가장 직접적인 ID 1 잔존 후보는 SettlementBatchIntegrationTest다. 이 클래스는 공용 DB에 group_buy_id=1, seller_id=201, status=DELIVERED를 INSERT하고 테스트 종료 후 이를 지우는 AfterEach나 전체 롤백 경계가 없다. 단, 실패 실행에서 그 ID 1 행의 판매자/상태 및 생성 주체를 별도로 추적하지 않았으므로 특정 클래스가 이 실행의 유일한 원인이었다고 단정하지 않는다. 확정한 관찰은 정상 생성 응답, 조회 대상 ID 불일치, 전역 행 수 증가다.

이후 **같은 2GB/캐시64 설정에서 SellerGroupBuyIntegrationTest만 실행하면 2개 모두 통과**했다(20초). 제품 코드를 바꾸거나 검증을 삭제하지 않았다. 공용 DB의 전체 행 수가 1이라는 기존 테스트 전제를 안정적으로 보장하는 클래스별 테스트 DB 분리는 별도 통합 검증 작업에서 처리한다. 이 MR의 철회 제품 변경에 무관한 수정으로 섞지 않는다. 따라서 여기까지의 887개 실행을 전체 통과로 선언하지 않으며, 테스트 격리 보강을 포함한 최종 통합 전체 결과는 통합 검증 문서에서 확인해야 한다.

실제 실행 명령은 backend에서 아래와 같다. init-script는 테스트 작업에만 `maxHeapSize = '2g'`, `spring.test.context.cache.maxSize = '64'`를 지정한다. 저장소 제품 빌드 설정의 변경이 아니라 로컬 검증 자원 설정이다.

```powershell
./gradlew.bat test --init-script C:/Users/SSAFY/특화/worktrees/integrated-verification/docs/testing/full-regression-runtime.gradle --console=plain
./gradlew.bat test --tests '*SellerGroupBuyIntegrationTest' --init-script C:/Users/SSAFY/특화/worktrees/integrated-verification/docs/testing/full-regression-runtime.gradle --console=plain
```
