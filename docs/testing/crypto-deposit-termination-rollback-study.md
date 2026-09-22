# 공구 종료 시 코인 MATCHED 입금 관측의 동반 롤백 방지 (§7)

관련 인수인계: `HANDOFF-2026-09-22.md` §7. 이 문서는 "정상 금액으로 매칭된 온체인 코인 입금이, 확정 직전 공구가 종료되면 내부 완료 예외와 함께 DB에서 사라지는" 결함과 그 수정을 설명한다. §6(카드·카카오)과 같은 "내부 완료 실패가 외부 금융 증거를 동반 롤백" 패턴의 코인 버전이다.

## 1. 증상

코인 결제에서 다음이 겹치면 구매자가 실제로 보낸 온체인 입금 기록이 DB에서 사라진다.

1. 구매자가 견적 금액대로 코인을 송금하고, 영수증 검증을 통과해 `crypto_deposits`에 `MATCHED` 입금이 기록된다.
2. 결제 슬롯은 아직 만료되지 않았는데 그 사이 판매자가 판매를 철회(공구 `CANCELED`)하거나 모집이 종료된다.
3. 컨펌이 충족돼 `CryptoTxRecordService.record`(또는 폴링 `applyLateReceipt`/`refreshConfirmations`)가 확정을 시도한다.
4. `completePayment`가 `groupBuySlotPort.confirm`에서 `GROUP_BUY_NOT_RECRUITING`을 던진다.

`record`/`applyLateReceipt`는 단일 `@Transactional`이고, MATCHED 입금은 그 트랜잭션 안에서 `cryptoDepositRepository.save`로 기록된다. 4의 예외가 트랜잭션 전체를 롤백하면서 **방금 기록한 MATCHED 입금 행까지 함께 사라진다.** 돈은 우리 지갑에 있는데 DB에는 흔적이 없어 누구에게 돌려줘야 하는지 알 수 없다.

비대칭이 핵심 단서다. 금액이 다른 입금(`UNDERPAID`/`OVERPAID`)은 `CryptoDepositRecorder.recordMismatched`가 **REQUIRES_NEW**로 별도 커밋해 같은 롤백에서 살아남는다(클래스 javadoc이 그 이유를 명시). MATCHED 입금만 이 롤백-안전 보존이 없었다.

## 2. 재현

`backend/src/test/java/com/gongguhan/audit/CryptoDepositTerminationRollbackReproductionTest.java`.

`ChainClient` 영수증을 대역으로 두고, 수취 지갑으로 견적 금액이 정확히 들어온 Transfer를 넣어 확정에 이르게 한다. 공구를 `CANCELED`로 바꾼 뒤 `record`를 호출하면 확정이 롤백된다.

수정 전 관측(고정된 결함):

| 경우 | 입금 행 | 결제 상태 | 참여 | 예외 |
| --- | --- | --- | --- | --- |
| 정상 대조군(RECRUITING) | 1 (MATCHED) | PAID | 1 | — |
| record 경로, 공구 CANCELED | **0 (유실)** | PROCESSING | 0 | GROUP_BUY_NOT_RECRUITING |
| applyLateReceipt 경로, 공구 CANCELED | **0 (유실)** | PROCESSING | 0 | GROUP_BUY_NOT_RECRUITING |
| 대조: UNDERPAID(RECRUITING) | **1 (보존, UNDERPAID)** | — | — | CRYPTO_AMOUNT_MISMATCH |

정상 대조군이 완주함을 보여 fixture 문제가 아니라 제품 결함임을 구분했고, UNDERPAID 보존과의 비대칭으로 원인을 특정했다.

## 3. 근본 원인

`record`/`applyLateReceipt`는 (a) MATCHED 입금 기록과 (b) `completePayment`(참여·에스크로·인원 확정)를 **하나의 트랜잭션**으로 묶는다. `completePayment`는 확정할 수 없는 공구(종료됨)에서 `GROUP_BUY_NOT_RECRUITING` **예외**를 던지도록 설계됐는데, 이 예외가 같은 트랜잭션의 입금 기록까지 되돌린다. 이미 `classifyTerminalDeposits`가 "종료된 결제의 MATCHED 입금을 반환 후보(UNMATCHED)로 옮기는" 처리를 갖고 있지만, 이 경로는 확정이 **예외 대신 정상적으로 종료 사실을 알려줄 때만** 닿을 수 있었고, 슬롯 만료 케이스에서만 호출되고 있었다.

## 4. 수정

§6과 대칭으로 **완료 경계를 비-throw로** 만든다. 코인 확정 전용 진입점 `PaymentService.completeIfRecruiting(paymentId)`를 추가했다.

- 모집 중이면 참여·에스크로·결제를 확정하고 `COMPLETED`를 돌려준다(기존 확정 본문을 `finalizeConfirmed` private 헬퍼로 추출해 `complete()`와 공유).
- 공구가 종료됐으면 예외도, §6의 `REFUND_REQUIRED` 표시도 하지 않고 `GROUP_TERMINATED`만 돌려준다. 트랜잭션을 살려 둬 호출 측이 입금을 보존·분류할 수 있게 한다.

`CryptoTxRecordService`의 확정 지점은 `completeIfRecruiting`을 호출하고, `GROUP_TERMINATED`면 예외로 롤백하지 않고:

- `failPayment`로 결제를 종결한다(슬롯/참여를 되살리지 않는다).
- `classifyTerminalDeposits(paymentId, now, false)`로 방금 기록한(그리고 롤백되지 않은) MATCHED 입금을 **UNMATCHED 반환 후보로 재분류**한다. 이 영수증은 방금 검증했으므로 컨펌을 유지한다(`needsFreshReceipt=false`).

이로써 종료 케이스가 기존 만료 케이스와 동일한 처리를 받는다. 실제 원금 반환은 기존 `CryptoDepositRefundService`(관리자 주도, 받은 수량 그대로 환불)가 담당한다 — 이 수정은 입금을 그 경로에 올려놓을 뿐 자동 송금하지 않는다. `record`·`applyLateReceipt`·`refreshConfirmations` 세 경로가 모두 이 확정 메서드를 거치므로 일괄 적용된다.

## 5. 검증

- 재현 테스트(수정 후 desired): record·applyLateReceipt 경로 모두 예외 없이 입금 행 1건을 `UNMATCHED`로 보존, 결제 `FAILED`, 참여 0, 그리고 그 입금이 `CryptoDepositRefundService.findRefundable()`에 포함됨(반환 경로 연결 확인). 정상 대조군은 그대로 `PAID`+MATCHED+참여1. UNDERPAID 비대칭 대조 유지.
- 회귀: §6(카드·카카오)와 기존 코인 테스트(`CryptoTxRecordServiceTest`, `CryptoTerminalDepositIntegrationTest` 등) 무회귀. 전체 백엔드 회귀 **134 스위트 / 915개 통과 / 실패 0 / 오류 0 / 제외 3**(Redis 2·S3 1 환경 테스트), bootJar 성공. Java 21/H2 + 외부 체인 대역.

## 6. 한계와 남은 범위

- **Redis 게이트 해제 예외 벡터**: 종료 처리의 `failPayment`가 Redis 게이트 해제에서 예외를 던지면 여전히 트랜잭션이 롤백돼 입금이 사라질 수 있다. 이는 기존 슬롯 만료 경로도 동일하게 갖는 **선재(pre-existing) 위험**이며, 이번 수정은 두 경로를 같은 처리로 통일했다. 완전한 방어(예: MATCHED 입금도 `recordMismatched`처럼 REQUIRES_NEW로 기록)는 별도 후속 검토 사항으로 남긴다.
- **스냅샷 없는 레거시 결제**: `completeIfRecruiting`은 확정 전 `storedSnapshot`을 요구하므로, 배송지 스냅샷이 없는 과거 결제가 입금을 받고 확정을 시도하면 여전히 예외로 롤백될 수 있다(현재 동작과 동일, 악화 없음). 과거 데이터 보정은 제공사/체인 대조가 필요한 별도 작업이다.
- 이 수정은 온체인 관측을 보존하고 반환 후보로 분류할 뿐, 과거 UNKNOWN·미확인 거래의 자동 재송금을 완성하지 않는다.

## 7. 재실행

worktree `crypto-deposit-rollback`의 backend에서(JDK 21):

```
./gradlew.bat test --tests "com.gongguhan.audit.CryptoDepositTerminationRollbackReproductionTest" --console=plain
```

전체 회귀:

```
./gradlew.bat --init-script ../docs/testing/full-regression-runtime.gradle test bootJar --console=plain
```
