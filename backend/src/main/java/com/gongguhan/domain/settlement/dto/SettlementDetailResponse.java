package com.gongguhan.domain.settlement.dto;

import java.util.List;

/**
 * STL-02 정산 상세 (API 명세서 12장, FR-STL-07~08).
 *
 * <p><b>포함된 건과 제외된 건을 함께 내보냅니다.</b> 판매자가 가장 먼저 묻는 것은
 * "왜 20명 중 19명만 정산됐나" 입니다. 포함 목록만 주면 답할 수 없습니다.
 *
 * <p>제외는 {@code settlement_items} 에 행으로 남기지 않고 조회 시점에 계산합니다 —
 * 제외를 행으로 쌓으면 그 참여 건이 나중에 정산될 때
 * {@code UNIQUE(participation_id)} 와 충돌합니다.
 */
public record SettlementDetailResponse(
        SettlementResponse settlement,
        List<Item> items
) {

    /**
     * 참여 건 한 줄.
     *
     * @param included       이번 정산에 포함됐는지
     * @param amount         포함된 건의 지급 기준 금액(분담금 + 배송비). 제외면 {@code null}
     * @param excludedReason 제외 사유. 포함이면 {@code null}
     */
    public record Item(
            String participationId,
            boolean included,
            Long amount,
            String excludedReason
    ) {

        public static Item included(Long participationId, long shareAmount, long shippingFee) {
            return new Item(String.valueOf(participationId), true, shareAmount + shippingFee, null);
        }

        public static Item excluded(Long participationId, String reason) {
            return new Item(String.valueOf(participationId), false, null, reason);
        }
    }
}
