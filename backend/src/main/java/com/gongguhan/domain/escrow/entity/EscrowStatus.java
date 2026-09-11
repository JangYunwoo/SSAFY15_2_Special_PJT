package com.gongguhan.domain.escrow.entity;

/**
 * 에스크로 상태 (FR-ESC, 요구사항 4.2).
 *
 * <pre>
 *   HELD ──구매확정──────────────────────────────▶ RELEASED
 *        └─모집실패·참여취소·확정전반품·무산─────▶ REFUNDED
 * </pre>
 *
 * <p><b>종료 상태에서 되돌아오는 경로는 없습니다.</b> {@link #RELEASED} 는 자금이 이미
 * 판매자에게 나갔다는 뜻이므로, 그 뒤의 반품은 에스크로 환불이 아니라 상계 또는
 * 회수 채권 문제로 넘어갑니다. {@link #REFUNDED} 는 구매자에게 돌려준 상태입니다.
 *
 * <p>DB 컬럼은 {@code VARCHAR(20)} 이고 이름이 그대로 저장됩니다
 * ({@code @Enumerated(EnumType.STRING)}). 이름을 바꾸면 기존 행을 읽을 수 없으니
 * 함부로 바꾸지 않습니다.
 */
public enum EscrowStatus {

    /** 결제 완료 후 보관 중. 아직 어느 쪽으로도 정해지지 않은 유일한 상태. */
    HELD,

    /** 구매확정으로 해제됨. 정산 대상이 된다 (FR-ESC-02, DR-34). */
    RELEASED,

    /** 환불로 종료됨. 정산 대상에서 빠진다 (FR-ESC-05). */
    REFUNDED;

    public boolean isHeld() {
        return this == HELD;
    }

    /** 더 이상 상태가 바뀌지 않는가. */
    public boolean isTerminal() {
        return this != HELD;
    }
}
