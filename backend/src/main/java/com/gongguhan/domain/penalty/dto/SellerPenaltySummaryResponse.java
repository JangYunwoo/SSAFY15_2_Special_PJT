package com.gongguhan.domain.penalty.dto;

import java.util.List;

/**
 * PEN-01 판매자 위반 이력·누적 응답 (FR-PEN-09~12).
 *
 * <p>목록만 주면 판매자가 직접 확정 건을 세어야 하고, 후보를 확정으로 오해할 수
 * 있습니다. <b>지금 몇 회이고 몇 회에 정지되는지</b>를 서버가 계산해 내려줍니다.
 *
 * @param confirmedCount 확정된 위반 횟수. 후보·기각·취소는 세지 않습니다
 * @param penaltyLimit   정지 임계 횟수 (정책값 {@code PENALTY_LIMIT})
 * @param suspended      현재 신규 등록·공동구매 생성·신규 결제가 막혔는지.
 *                       기존 거래의 배송·반품·정산은 계속 가능합니다
 */
public record SellerPenaltySummaryResponse(
        int confirmedCount,
        int penaltyLimit,
        boolean suspended,
        List<PenaltyResponse> penalties
) {
}
