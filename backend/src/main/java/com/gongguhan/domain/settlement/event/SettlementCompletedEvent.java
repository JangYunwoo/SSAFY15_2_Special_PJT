package com.gongguhan.domain.settlement.event;

import java.time.LocalDateTime;

/**
 * 정산 이체가 확정될 때 settlement 도메인(S15P21B205-82)이 발행하는 이벤트.
 *
 * <p>판매자에게 "정산금이 입금됐다" 를 알리기 위한 것입니다. 소비 측(notification
 * 도메인)은 이 계약만 의존하므로 두 작업을 독립적으로 진행할 수 있습니다 —
 * {@link com.gongguhan.domain.penalty.event.SellerSanctionedEvent} 와 같은 구조입니다.
 *
 * <p><b>아직 소비자가 없습니다.</b> 알림 도메인에 리스너가 추가되기 전까지 이 이벤트는
 * 발행만 되고 아무 일도 일어나지 않습니다. 발행을 먼저 넣어두는 이유는, 이체 성공
 * 경로에 나중에 손을 대는 것보다 지금 계약을 고정해 두는 편이 안전하기 때문입니다.
 *
 * @param settlementId    정산 식별자. 중복 알림을 막는 멱등 키의 근거
 * @param amount          실제로 이체된 금액(KRW)
 * @param depositTxNo     SSAFY 입금 거래고유번호. 판매자가 은행에서 대조할 수 있는 값
 */
public record SettlementCompletedEvent(
        Long settlementId,
        Long sellerId,
        Long groupBuyId,
        int roundNo,
        long amount,
        Long depositTxNo,
        LocalDateTime occurredAt
) {
}
