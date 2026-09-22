# 승인 중 공구 종료와 고립 청구의 환불 재조정 (§6)

관련 인수인계: `HANDOFF-2026-09-22.md` §6. 이 문서는 "외부 결제 승인은 성공했는데 그 사이 공구가 종료되어 내부 확정이 롤백되는" 경합에서 청구가 고립되는 결함과 그 수정을 설명한다. 두 단계로 구현했으며 하나의 MR로 병합한다.

- **Phase 1** — 고립 청구를 결과 불명(UNKNOWN)이 아니라 **환불 대상(REFUND_REQUIRED)** 으로 durable하게 기록한다(§5).
- **Phase 2** — 그 결제를 기존 수단별 환불 실행기(카드→계좌이체, 카카오→원거래 취소)로 자동 환불하는 복구 스캔을 추가한다(§6). 경합 경로에 인라인 자동 송금은 없다 — 실제 송금은 검토된 기존 환불 machinery에서만 일어난다.

## 1. 증상

카드/카카오페이 결제에서 다음 순서가 겹치면 구매자의 돈이 빠져나갔는데도 시스템 어디에서도 자동으로 해소되지 않는다.

1. 결제 승인 진입 시 `getApprovalContext`가 공구를 RECRUITING으로 확인한다.
2. 외부 결제망(SSAFY 카드 / 카카오페이)에 승인을 요청하고 **성공**한다(실제 청구 발생).
3. 응답을 기다리는 사이 판매자가 판매를 철회(공구 CANCELED)하거나 모집이 종료(FULFILLED/FAILED)된다.
4. 내부 확정 `PaymentService.completePayment`가 `groupBuySlotPort.confirm`을 호출하는데, 종료된 공구에서 `GroupBuy.confirmParticipant()`가 `GROUP_BUY_NOT_RECRUITING`을 던져 **전체 확정이 롤백**된다.

결과:
- 참여(participation)·에스크로가 생기지 않는다.
- 카드는 `card_approval_status`가 `UNKNOWN`(결과 불명)으로 기록되고 `status`는 `PROCESSING`으로 방치된다. **외부 청구는 명백히 성공했는데 "결과 불명"으로 오분류**된다.
- 카카오는 승인 상세(`markApproved`)까지 같은 트랜잭션에서 롤백된다.
- 판매철회 환불(`GroupBuyWithdrawalService`)과 모집실패 복구 스캔(`FailedRecruitmentRefundRepository`)은 둘 다 `status='PAID'` + 참여행을 대상으로 한다. 이 결제는 참여행이 없어 **어느 환불 경로에도 잡히지 않는다.** 돈이 고립된다.

## 2. 재현

`backend/src/test/java/com/gongguhan/audit/ApprovalInFlightTerminationReproductionTest.java`.

스레드/latch 없이 결정적으로 재현한다. 외부 게이트웨이 mock의 `thenAnswer` 안에서 공구 상태를 종료로 바꾼 뒤 성공 응답을 돌려주면, "외부 HTTP 왕복 중 공구가 종료됐다"가 정확히 재현된다.

```java
when(bank.createCreditCardTransaction(...)).thenAnswer(inv -> {
    externalCalls.incrementAndGet();
    terminate(f.id, terminalState);   // 판매철회/모집종료가 이 순간 커밋
    return cardSuccess();             // 외부 청구는 성공
});
```

수정 전 관측(고정된 결함):

```
method=CARD terminal=CANCELED externalCalls=1 finalStatus=PROCESSING cardApproval=UNKNOWN participations=0
method=KAKAOPAY terminal=FAILED externalCalls=1 finalStatus=PROCESSING cardApproval=null participations=0
```

카드·카카오 × 철회(CANCELED)/모집실패(FAILED)/이행(FULFILLED) 6종 모두 동일하게 고립된다. 정상 대조군(공구가 끝까지 RECRUITING → PAID + 참여 1건)을 함께 두어 fixture 문제가 아니라 제품 결함임을 구분했다.

## 3. 근본 원인

- `completePayment`는 하나의 트랜잭션에서 `confirm`(비관 락) → 참여·에스크로 생성 → `markPaid`를 원자적으로 처리한다. 이 원자성은 정상 경로에서는 옳지만, `confirm`이 `GROUP_BUY_NOT_RECRUITING` **예외**를 던지면 트랜잭션이 rollback-only가 되어 "외부 청구가 성공했다"는 사실을 내부에 기록할 여지 없이 전부 되돌린다.
- 카드의 catch는 이 롤백을 "결과 불명(UNKNOWN)"으로 기록한다. 그러나 이 경합에서 외부 결과는 **명백한 성공**이다. UNKNOWN은 "제공사 대조가 필요한 진짜 불명 건"과 의미가 겹쳐, 실제로는 환불이 필요한 건이 수동 대조 사각지대로 사라진다.
- 카카오는 승인 상세와 내부 확정이 한 트랜잭션이라, 롤백 시 외부 승인 증거(`aid`)까지 사라진다.
- 두 환불 경로(판매철회·모집실패 복구)는 참여행 + `PAID`를 전제로 해서, 참여로 이어지지 못한 이 결제를 구조적으로 못 본다.

## 4. 설계 대안과 선택

사용자와 함께 세 방향을 검토했고, 모두 결국 구매자 환불로 끝나지만 메커니즘·위험·범위가 다르다.

| 방향 | 요지 | 선택 여부 |
| --- | --- | --- |
| 환불필요 상태 + 재조정 | 외부 청구 증거를 보존한 채 결제를 `REFUND_REQUIRED`로 durable하게 기록하고, 기존 환불 machinery로 별도 재조정. 경합 경로에 인라인 송금 없음. | **채택** |
| 감지 즉시 자동환불 | 감지 지점에서 즉시 수단별 실행기로 송금. 경합 경로에서 자동 송금 + 공용 서비스 수정. | 배제 |
| 공구 종료 직렬화 | 판매철회/모집종료가 in-flight 승인을 감지해 보류, 승인을 정상 PAID로 확정. 카카오/코인용 durable in-flight 마커 신설 필요. | 배제 |

채택 이유: 인수인계 §6이 "외부 HTTP 중 긴 DB 잠금", "결과 불명을 시간 경과만으로 재승인", "경합 경로 인라인 자동 송금"을 명시적으로 경계했고, "확인된 외부 성공 증거를 내부 완료와 독립 보존하고 재조정"을 선호 방향으로 남겼다. 이 방향은 그 선호와 정확히 일치하며, 실제 송금은 검토된 기존 환불 경로에서만 일어나게 분리한다.

이 결정은 돈 정책에 해당하므로 코드만으로 추정하지 않고 사용자 확인을 받은 뒤 구현했다.

## 5. 구현 — Phase 1 (감지·분류)

### 5.1 완료 경계 확정을 비-throw로

`GroupBuySlotPort.confirmForCompletion` + `ConfirmOutcome(confirmed, slot)`를 추가했다. `GroupBuySlotService.confirmForCompletion`은 락을 잡고 **모집 상태를 먼저 검사**해, 종료된 공구면 예외 대신 `confirmed=false`를 돌려준다(모집 중이면 기존 `confirm`과 동일하게 `confirmParticipant` + `judgeTargetReached`). 예외가 없으므로 호출 트랜잭션이 rollback-only로 오염되지 않아, 같은 건강한 트랜잭션에서 환불 예약을 durable하게 기록할 수 있다. 기존 `confirm`은 그대로 두어 다른 호출자에 영향이 없다.

### 5.2 REFUND_REQUIRED durable 기록

- `V23__mark_payment_refund_required.sql`: `payments.reconcile_state VARCHAR(20)` + 인덱스. 과거 행은 backfill 하지 않는다(과거 UNKNOWN 카드 행이 이 케이스인지 코드만으로 단정 불가 — 제공사 대조 필요).
- `Payment.markRefundRequired`: `status=FAILED`, `reconcile_state=REFUND_REQUIRED`, `slot_marker=null`, 카드 in-flight 실행권은 `COMPLETED`로 확정(UNKNOWN 아님). 참여·에스크로를 만들지 않고, **슬롯 해제도 하지 않는다** — 종료된 공구가 이미 `reserved_slots`를 회수했으므로 이중 차감을 피한다. `status=FAILED`로 두면 만료 배치/`abort`/슬롯 재사용(모두 `isProcessing` 기반)이 자연히 이 건을 건드리지 않고, `reconcile_state`가 clean FAILED와 구분한다.

### 5.3 결제수단별 제어 흐름

- `PaymentService`: `completePayment`(코인/레거시 — 종료 시 기존대로 예외, **동작 불변**)와 `completeOrScheduleRefund`(카드/카카오 — 종료 시 REFUND_REQUIRED 기록 후 반환)로 분기하는 private `complete(id, flag)` 코어. 카카오용 `completeOwnedPaymentOrScheduleRefund` 추가.
- `CardPaymentService.approve`: `completeOrScheduleRefund` 사용. 환불 예약 결과는 UNKNOWN을 기록하는 catch **밖**에서 `GROUP_BUY_TERMINATED_REFUND_SCHEDULED`(409)로 사용자에게 알린다(예외가 catch에 걸려 UNKNOWN으로 오분류되지 않게 함).
- `KakaoPayRecordService.complete`: `completeOwnedPaymentOrScheduleRefund` 사용. 비-throw이므로 같은 트랜잭션에서 `markApproved`(승인 `aid`/`tid`)가 함께 커밋되어 환불에 필요한 외부 승인 증거가 durable하게 보존된다. `KakaoPayService.approve`가 트랜잭션 밖에서 사용자 대면 예외를 던진다.
- `PaymentResponse`에 `reconcileState`를 추가해 호출 측·이력 화면이 환불 예약을 판별할 수 있게 했다.

## 6. 구현 — Phase 2 (자동 재조정)

`REFUND_REQUIRED` 결제를 기존 환불 machinery로 자동 환불한다. **기존 공용 환불 경로(참여/반품/입금 환불, `resumePersisted`, `findUnsent`)는 수정하지 않고** 격리된 신규 경로만 추가해 회귀 위험을 없앴다.

- **환불 원장을 결제에 직접 연결**: `refunds`에는 결제 참조가 없었다(참여/반품/입금으로만 연결). `V24`로 `refunds.payment_id`(+ FK, `uk_refunds_active_payment(payment_id, refund_marker)` 유일성)를 추가하고, `Refund.forPayment` 팩토리·`RefundLedgerService.openForPayment`(paymentId 기준 멱등 개시)를 더했다. 사유는 새 `RefundReason.GROUP_BUY_TERMINATED`.
- **수단 매핑**: 카드는 `ACCOUNT_TRANSFER`(카드 취소는 카드번호·CVC 재입력이 필요해 자동 환불이 부를 수 없다 — 인수인계가 저장을 금지한 것과 같은 이유. API 명세 11장이 계좌이체 전환을 허용). 카카오페이는 원거래 취소 어댑터(`RefundPort`). 하위 실행기(`refundByTransfer`/`refundByPort`)는 참여 없이 `payment` 객체만으로 동작한다.
- **복구 스캔**: `StrandedPaymentRefundRepository.findCandidates`(reconcile_state=REFUND_REQUIRED인 카드/카카오 결제) → `StrandedPaymentRefundRecordService.reserve`(락+재검증, `openForPayment`, 결제를 `REFUND_RESERVED`로 전이해 재선정 차단 — 금융 호출 없음) → `RefundOrchestrationService.resumeStrandedPayment`(트랜잭션 밖 실행). `RefundRecoveryService.run()`(60초 주기)에 이 루프를 추가했다.
- **중복·크래시 안전**: `REFUND_RESERVED` 전이와 `uk_refunds_active_payment`가 이중 개시를 막고, 예약 후 미송신(크래시) 건은 `findUnsentStrandedPayment`가 다음 주기에 재개한다. 실패한 결제 단위 환불은 관리자 재처리(`retry`)가 결제로 자산을 특정해 재실행하도록 보강했다.

## 7. 검증

- **Phase 1 재현 테스트**(`ApprovalInFlightTerminationReproductionTest`, 수정 후 desired): 카드·카카오 × 철회/모집실패/이행 6종 모두 `first=GROUP_BUY_TERMINATED_REFUND_SCHEDULED`, `externalCalls=1`, `finalStatus=FAILED`, `reconcile=REFUND_REQUIRED`, `participations=0`. 카드 `cardApproval=COMPLETED`(UNKNOWN 아님)·`card_payments` 1건, 카카오 `aid` NOT NULL. 재호출은 새 외부 청구를 만들지 않고(`externalCalls=1` 유지) `PAYMENT_ALREADY_PROCESSED`로 막히며 상태를 유지한다.
- **Phase 2 재조정 테스트**(`StrandedPaymentRefundReconcileIntegrationTest`): 고립 카드 결제 → `recovery.run()` → `ACCOUNT_TRANSFER` 환불 `COMPLETED`(외부 이체 1회), 고립 카카오 결제 → `KAKAOPAY` 취소 `COMPLETED`(취소 1회), 사유 `GROUP_BUY_TERMINATED`, 결제 `reconcile_state=REFUND_RESERVED`. `run()`을 두 번 호출해도 환불 1건·외부 송금 1회(멱등).
- **회귀**: 카드/카카오/코인 결제·슬롯 경계·기존 환불(모집실패 복구·판매철회·참여 취소) 테스트가 그대로 통과. `CryptoTxRecordServiceTest`로 코인 경로 동작 불변을 확인했다. 전체 백엔드 회귀는 인수인계의 선택형 실행 설정(`full-regression-runtime.gradle`, Test JVM 2GiB/컨텍스트 캐시 64)으로 실행하며 **133 스위트·911개 통과·실패 0·오류 0·제외 3**(Redis 2·S3 1 환경 테스트), bootJar 성공이다.

## 8. 한계와 남은 범위

- 코인(§7)은 이 수정 범위가 아니다. 코인 완료 경로는 `completePayment`(기존 예외 동작)를 그대로 쓰며, 코인의 종료-후-입금·고립은 별도 트랙에서 다룬다.
- 자동 환불은 구매자에게 **SSAFY 계좌**(계좌이체 환불용)가 등록돼 있어야 실행된다. 미등록이면 환불이 `FAILED`로 남아 관리자 재처리 대상이 된다(계좌 등록 후 재처리). 조용히 사라지지 않도록 실패 사유를 원장에 남긴다.
- 프론트엔드는 `GROUP_BUY_TERMINATED_REFUND_SCHEDULED`(409) 응답과 `reconcileState` 필드를 받으면 "결제가 승인됐으나 공구가 종료되어 환불 예정"임을 안내하도록 개선할 수 있다(기능 정합성에는 영향 없어 선택 사항).
- 과거에 이미 이 상태로 고립됐을 수 있는 기존 UNKNOWN/PROCESSING 카드 행은 자동 backfill하지 않았다. 제공사 거래내역과 대조한 뒤에만 처리한다.
- 이 회귀는 Java 21/H2와 외부 금융망 대역을 쓴다. 실제 MySQL·실 금융망·종단 배포 검증은 별도다.

## 9. 재실행

worktree `approval-inflight-termination`의 backend에서(JDK 21):

```
./gradlew.bat test --tests "com.gongguhan.audit.ApprovalInFlightTerminationReproductionTest" --console=plain
```

전체 회귀:

```
./gradlew.bat --init-script ../docs/testing/full-regression-runtime.gradle test bootJar --console=plain
```
