# 위반 확정 공구의 정산 보호 (§8)

관련 인수인계: `HANDOFF-2026-09-22.md` §8. 판매자 위반이 확정됐을 때, 그 공구의 아직 지급되지 않은 정산이 위반 판매자에게 자동 지급되는 공백을 막는다.

## 1. 결정한 정책과 그 경계

이 트랙은 코드보다 **돈 정책 결정**이 먼저 필요한 영역이라, 인수인계의 강한 경계를 지켜 **명백히 안전한 보호 처리에 한정**했다.

> **특정 공구에 위반이 CONFIRMED되면, 그 공구의 정산을 판매자에게 자동 지급하지 않고 환불 가능한 상태(에스크로 HELD / 정산 HOLD)로 묶는다.**

지킨 경계:

- **이미 지급 완료(COMPLETED)된 정산은 건드리지 않는다.** 상계(offset)·회수 채권(receivable) 스키마는 최신 ERD v2에서 제외됐고 코드에도 없다(마이그레이션·엔티티 grep 0건). 이미 나간 돈의 회수는 이 자동 처리 범위가 아니며 관리자·정책 결정 대상으로 남긴다.
- **판매자의 다른 공구에는 영향을 주지 않는다.** 판매자 정지(SUSPENDED)가 아니라 **위반이 확정된 그 공구(penalty.group_buy_id)** 에만 적용한다. 프로젝트 설계상 정지 판매자의 기존 거래는 계속 진행되므로("이미 돈을 낸 구매자를 볼모로 잡지 않기 위해"), 판매자 전체 공구를 무차별로 막지 않는다.
- **자동 환불·자동 송금·신규 스키마를 만들지 않는다.** 이 수정은 지급을 **멈춰서 돈을 환불 가능한 상태로 보존**할 뿐, 구매자에게 자동 환불하지 않는다. 실제 환불/회수는 관리자의 기존 보류·반품·환불 도구로 결정한다.

즉 이 수정은 "지급 후 제재" 정책 전체를 구현하는 것이 아니라, 그 정책이 정해질 때까지 **돈이 위반 판매자에게 새어 나가지 않도록 보존**하는 보호 장치다.

## 2. 현재 공백

`PenaltyService.confirm()`은 제재 상태 전이 + 누적 카운트(임계 도달 시 판매자 SUSPENDED) + `SellerSanctionedEvent` 발행만 한다. 이 이벤트의 소비자는 알림 리스너 하나뿐이고, 정산/에스크로/환불 도메인은 구독하지 않는다.

정산 대상 선정 쿼리(`SettlementTargetRepository.SELECT_TARGET`)에는 제재·정지 판매자나 위반 공구를 제외하는 조건이 없다. 따라서 위반이 확정된 공구도 조건만 맞으면 그대로 정산·판매자 송금된다. 정산 보류(`Settlement.hold`)와 per-participation 환불 원장은 이미 존재하지만 제재와 연결돼 있지 않다.

## 3. 수정 (기존 machinery, 정산 도메인 내, 신규 스키마 없음)

### Part 1 — 정산 대상 제외 (에스크로 HELD 유지)
`SELECT_TARGET`에 위반 확정 공구 제외 조건을 추가한다.

```sql
AND NOT EXISTS (
    SELECT 1 FROM seller_penalties sp
     WHERE sp.group_buy_id = p.group_buy_id
       AND sp.status = 'CONFIRMED'
)
```

정산 배치가 그 공구의 참여를 대상에서 빼므로 **에스크로가 HELD로 유지**된다. HELD는 아직 지급되지 않은, 환불 가능한 상태다. 제외 사유 리포트(`findExcluded`)에도 `SELLER_PENALIZED`를 추가해 판매자·관리자가 "왜 이 건이 정산에서 빠졌는지"를 볼 수 있게 했다.

### Part 2 — 송금 실행 가드 (이미 생성된 PENDING 정산)
정산 배치가 위반 확정 **전에** 이미 PENDING 정산을 만든 경우(에스크로는 이미 RELEASED), Part 1로는 늦다. 그래서 실제 송금 지점(`SettlementTransferService.execute`)에서 송금 전에 위반 확정 여부를 확인해, 확정 공구면 송금하지 않고 **정산을 HOLD로 전환**한다(기존 `Settlement.hold` 재사용).

주의할 순서 문제: 송금 실행의 `loadExecutable`은 `claimTransfer()`로 실행권(`transfer_claimed`)을 잡는데, `Settlement.hold`는 **이미 claim된 정산의 보류를 거부**한다. 따라서 claim한 뒤 보류하면 예외가 난다. 그래서 확인과 보류를 **claim 이전에, 한 트랜잭션**으로 처리하는 `SettlementRecordService.holdIfSellerPenalized`를 두고, `execute`가 `loadExecutable`보다 **먼저** 호출한다. 이 방식은 조회-보류 사이의 TOCTOU 경합도 없앤다.

```java
// SettlementTransferService.execute — loadExecutable(=claim) 이전
Settlement penaltyHeld = recordService.holdIfSellerPenalized(settlementId);
if (penaltyHeld != null) { return penaltyHeld; }
Settlement settlement = recordService.loadExecutable(settlementId);
...

// SettlementRecordService (한 트랜잭션): 실행 대상(PENDING·미claim)이고 확정 위반이면 보류
@Transactional
public Settlement holdIfSellerPenalized(Long settlementId) {
    Settlement s = load(settlementId);
    if (!s.isExecutable()) return null;                 // COMPLETED/HOLD/claim된 건은 대상 아님
    if (!penaltyRepository.existsByGroupBuyIdAndStatus(s.getGroupBuyId(), "CONFIRMED")) return null;
    s.hold("판매자 위반 확정으로 정산 보류");
    return s;
}
```

`isExecutable()`이 PENDING·미claim만 통과시키므로, **이미 지급 완료(COMPLETED)나 보류 중인 정산은 대상이 아니라 건드리지 않는다 — 지급된 돈을 되돌리는 클로백은 없다.** 두 파트가 함께 "위반 확정 전/후" 두 창을 모두 닫는다.

## 4. 되돌림·후속

- 제재가 `REVOKED`/`DISMISSED`로 바뀌면 두 가드 모두 조건에서 빠져 정산이 정상 재개된다(별도 조치 불필요). HOLD된 정산은 관리자가 `release-hold`로 풀 수 있다.
- 보존된 미정산 건의 실제 처리(구매자 환불 vs 판매자 지급 재개)는 관리자의 기존 보류·반품·환불 흐름에서 결정한다. 이 수정은 그 결정을 강제하지 않고 돈을 보존만 한다.
- 이미 지급 완료된 위반 공구의 회수(구매자 환불 재원·판매자 회수 책임·절차·상계/채권)는 여전히 정책 결정과 별도 설계가 필요하다. 이 수정은 그 영역을 다루지 않는다.

## 5. 검증

- 재현/검증 테스트(`PenalizedGroupBuySettlementProtectionTest`):
  - Part 1 — 위반 확정 공구의 참여가 `findTargets`에서 제외되고 `findExcluded`가 `SELLER_PENALIZED`를 반환. 제재 없음/CANDIDATE/DISMISSED 대조군은 정상 포함.
  - Part 2 — 위반 확정 공구의 PENDING 정산을 `execute`하면 `transferClient.transfer` 호출 없이 HOLD로 전환. 제재 없는 정산은 정상 송금.
- 회귀: 기존 정산(배치·송금·반품배송비)·제재 테스트 무회귀. 전체 백엔드 회귀 **136 스위트 / 923개 통과 / 실패 0 / 오류 0 / 제외 3**(Redis 2·S3 1 환경 테스트), bootJar 성공. Java 21/H2 + 금융망 대역.
