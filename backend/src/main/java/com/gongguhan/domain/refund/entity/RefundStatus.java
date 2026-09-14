package com.gongguhan.domain.refund.entity;

/**
 * 환불 처리 상태 ({@code refunds.status}).
 *
 * <p>행이 만들어지는 순간 이미 {@link #PROCESSING} 입니다. "환불 예정" 같은 이전 단계가
 * 없는 이유는, 환불을 낼지 말지는 호출 측(반품·모집 실패·참여 취소)이 이미 판단한
 * 뒤에 여기로 오기 때문입니다. 이 행의 존재 자체가 "환불하기로 결정됐다" 입니다.
 *
 * <p>{@link #FAILED} 는 종료가 아니라 <b>재처리 대기</b>입니다. RFD-04 관리자 재처리가
 * 이 상태의 건을 다시 {@link #PROCESSING} 으로 되돌립니다(FR-RFD-09, FR-ADM-08).
 * 외부 이체·온체인 송금은 잔액 부족이나 노드 장애처럼 시간이 지나면 해소되는 이유로
 * 실패하므로, 한 번 실패했다고 구매자 돈을 포기할 수는 없습니다.
 */
public enum RefundStatus {

    /** 환불 결정됨. 아직 외부 송금·이체가 확정되지 않았습니다. */
    PROCESSING,

    /** 외부 송금·이체가 확정됐습니다. 되돌릴 수 없습니다. */
    COMPLETED,

    /** 실패. 재처리 대상입니다. */
    FAILED
}
