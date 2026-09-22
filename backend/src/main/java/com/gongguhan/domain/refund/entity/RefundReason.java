package com.gongguhan.domain.refund.entity;

/**
 * 환불 사유 ({@code refunds.reason_type}).
 *
 * <p><b>사유를 남기는 이유는 통계가 아니라 금액 계산 근거입니다.</b> 같은 참여 건이라도
 * 단순변심 반품이면 최초 배송비를 돌려주지 않고, 판매자 귀책이면 전액을 돌려줍니다.
 * 나중에 "왜 이 금액인가" 를 따질 때 이 값이 유일한 단서입니다.
 *
 * <p>값마다 행을 만드는 주체가 다릅니다. 환불은 한 사람이 아니라 여러 도메인이
 * 일으키므로, 이 enum 이 그 목록 역할도 합니다.
 */
public enum RefundReason {

    /** 마감까지 목표 인원을 못 채워 공동구매가 무산됨 (FR-RFD-01). */
    RECRUIT_FAILED,

    /** 모집 완료 전 구매자가 참여를 취소함 (FR-JOI-12~13, FR-RFD-02). */
    PARTICIPATION_CANCEL,

    /**
     * 결제 슬롯이 만료된 뒤 입금이 도착함 (FR-SLT-10).
     *
     * <p>자리는 이미 다른 사람에게 넘어갔는데 돈은 우리에게 들어온 상태입니다.
     * 참여 건이 만들어지지 않으므로 {@code participation_id} 가 없습니다.
     */
    SLOT_EXPIRED,

    /** 반품 승인·수거 완료에 따른 환불 (FR-RTN-17). */
    RETURN,

    /** 판매자 귀책(미배송·가짜 송장 등)으로 인한 환불. */
    SELLER_FAULT,

    /**
     * 견적과 맞지 않는 입금 (FR-PAY-26).
     *
     * <p>금액 불일치·기간 만료 등으로 결제에 붙이지 못한 입금을 돌려보냅니다.
     * 참여 건이 없고 {@code deposit_id} 만 있습니다.
     */
    UNMATCHED_DEPOSIT,

    /**
     * 결제 승인 후 공동구매가 종료돼 참여 확정이 불가능해진 결제의 환불.
     *
     * <p>외부 청구는 성공했으나 그 사이 판매 철회·모집 종료로 공구가 끝나 참여가 만들어지지
     * 않았습니다. 참여 건이 없어 {@code participation_id} 없이 {@code payment_id} 로 연결합니다.
     */
    GROUP_BUY_TERMINATED
}
