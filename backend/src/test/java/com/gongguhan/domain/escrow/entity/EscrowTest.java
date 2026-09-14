package com.gongguhan.domain.escrow.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 에스크로 상태 전이 규칙 검증.
 *
 * <p>여기서 확인하는 것은 <b>돈이 두 번 나가거나, 확정 없이 나가지 않는지</b>다.
 * 규칙을 엔티티에 둔 이유가 이것이다 — 호출 경로가 여러 개라 서비스에 흩어 두면
 * 그중 하나가 빠뜨린다.
 */
class EscrowTest {

    private static final Long PARTICIPATION_ID = 500L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final long AMOUNT = 54_000L;

    private static final LocalDateTime HELD_AT = LocalDateTime.parse("2026-01-01T00:00:00");
    private static final LocalDateTime LATER = LocalDateTime.parse("2026-01-08T00:00:00");

    private static final boolean CONFIRMED = true;
    private static final boolean NOT_CONFIRMED = false;

    private Escrow held() {
        return Escrow.hold(PARTICIPATION_ID, GROUP_BUY_ID, AMOUNT, HELD_AT);
    }

    // ------------------------------------------------------------------
    // 생성
    // ------------------------------------------------------------------

    @Test
    @DisplayName("보관 상태로 생성되고 해제·환불 시각은 비어 있다")
    void createdAsHeld() {
        Escrow escrow = held();

        assertEquals(EscrowStatus.HELD, escrow.getStatus());
        assertTrue(escrow.isHeld());
        assertEquals(AMOUNT, escrow.getAmount());
        assertEquals(HELD_AT, escrow.getHeldAt());
        assertNull(escrow.getReleasedAt());
        assertNull(escrow.getRefundedAt());
    }

    @Test
    @DisplayName("보관 시각은 주입받은 값을 그대로 쓴다 - 테스트에서 시각 고정 가능")
    void usesInjectedTimestamp() {
        // 엔티티가 LocalDateTime.now() 를 직접 부르면 이 검증이 불가능하고,
        // DB 에 UTC/KST 가 섞인다.
        assertEquals(HELD_AT, held().getHeldAt());
    }

    // ------------------------------------------------------------------
    // 해제 (FR-ESC-02, DR-33)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("구매확정된 건은 해제된다")
    void releasesWhenConfirmed() {
        Escrow escrow = held();

        escrow.release(CONFIRMED, LATER);

        assertEquals(EscrowStatus.RELEASED, escrow.getStatus());
        assertEquals(LATER, escrow.getReleasedAt());
        assertNull(escrow.getRefundedAt());
        assertFalse(escrow.isHeld());
    }

    @Test
    @DisplayName("구매확정 전에는 해제할 수 없다 - 모집완료·송장등록만으로는 안 된다")
    void rejectsReleaseBeforeConfirmation() {
        Escrow escrow = held();

        BusinessException e = assertThrows(BusinessException.class,
                () -> escrow.release(NOT_CONFIRMED, LATER));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        // 상태가 그대로여야 한다. 예외를 던지고 값은 바꿨다면 최악이다.
        assertEquals(EscrowStatus.HELD, escrow.getStatus());
        assertNull(escrow.getReleasedAt());
    }

    @Test
    @DisplayName("이미 해제된 건은 다시 해제할 수 없다 - 정산 배치 재실행 대비")
    void rejectsDoubleRelease() {
        Escrow escrow = held();
        escrow.release(CONFIRMED, LATER);

        BusinessException e = assertThrows(BusinessException.class,
                () -> escrow.release(CONFIRMED, LATER.plusDays(1)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        // 최초 해제 시각이 덮어써지지 않아야 한다.
        assertEquals(LATER, escrow.getReleasedAt());
    }

    // ------------------------------------------------------------------
    // 환불 (FR-ESC-05)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("보관 중인 건은 환불로 종료된다")
    void refundsWhenHeld() {
        Escrow escrow = held();

        escrow.refund(LATER);

        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
        assertEquals(LATER, escrow.getRefundedAt());
        assertNull(escrow.getReleasedAt());
    }

    @Test
    @DisplayName("환불은 구매확정 여부를 보지 않는다 - 확정 전 반품이 정상 경로다")
    void refundDoesNotRequireConfirmation() {
        Escrow escrow = held();

        escrow.refund(LATER);   // 구매확정 안 된 상태에서 호출

        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
    }

    @Test
    @DisplayName("이미 환불된 건은 다시 환불할 수 없다 - 이중 환불 차단")
    void rejectsDoubleRefund() {
        Escrow escrow = held();
        escrow.refund(LATER);

        BusinessException e = assertThrows(BusinessException.class,
                () -> escrow.refund(LATER.plusDays(1)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        assertEquals(LATER, escrow.getRefundedAt());
    }

    // ------------------------------------------------------------------
    // 종료 상태에서 되돌아오는 경로가 없다
    // ------------------------------------------------------------------

    @Test
    @DisplayName("해제된 건은 환불할 수 없다 - 자금이 이미 판매자에게 나갔다")
    void rejectsRefundAfterRelease() {
        Escrow escrow = held();
        escrow.release(CONFIRMED, LATER);

        BusinessException e = assertThrows(BusinessException.class,
                () -> escrow.refund(LATER.plusDays(1)));

        // 이 경우는 상계 또는 회수 채권 문제로 넘어가야 한다.
        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        assertEquals(EscrowStatus.RELEASED, escrow.getStatus());
        assertNull(escrow.getRefundedAt());
    }

    @Test
    @DisplayName("환불된 건은 해제할 수 없다 - 이미 구매자에게 돌려줬다")
    void rejectsReleaseAfterRefund() {
        Escrow escrow = held();
        escrow.refund(LATER);

        BusinessException e = assertThrows(BusinessException.class,
                () -> escrow.release(CONFIRMED, LATER.plusDays(1)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
        assertNull(escrow.getReleasedAt());
    }

    // ------------------------------------------------------------------
    // 상태 enum
    // ------------------------------------------------------------------

    @Test
    @DisplayName("HELD 만 비종료 상태다")
    void onlyHeldIsNonTerminal() {
        assertTrue(EscrowStatus.HELD.isHeld());
        assertFalse(EscrowStatus.HELD.isTerminal());

        assertTrue(EscrowStatus.RELEASED.isTerminal());
        assertTrue(EscrowStatus.REFUNDED.isTerminal());
        assertFalse(EscrowStatus.RELEASED.isHeld());
        assertFalse(EscrowStatus.REFUNDED.isHeld());
    }

    @Test
    @DisplayName("enum 이름은 DB 에 그대로 저장되므로 바뀌면 안 된다")
    void enumNamesMatchSchema() {
        // schema v2 의 escrows.status 주석: 'HELD / RELEASED / REFUNDED'
        assertEquals("HELD", EscrowStatus.HELD.name());
        assertEquals("RELEASED", EscrowStatus.RELEASED.name());
        assertEquals("REFUNDED", EscrowStatus.REFUNDED.name());
        assertEquals(3, EscrowStatus.values().length);
    }

    // ------------------------------------------------------------------
    // 부분 환불 (FR-ESC-05)
    // ------------------------------------------------------------------

    /**
     * 이 테스트가 지키는 것이 부분 환불의 존재 이유입니다. 여기서 종료시키면 판매자가
     * 받아야 할 12,000원이 정산 대상에서 조용히 사라집니다.
     */
    @Test
    @DisplayName("일부만 환불하면 보관 상태를 유지하고 잔액이 남는다 - 남은 돈은 정산 대상이다")
    void partialRefundKeepsRemainderHeld() {
        Escrow escrow = held();

        escrow.refundPartially(42_000L, LATER);

        assertTrue(escrow.isHeld(), "잔액이 있으면 보관 상태를 유지해야 한다");
        assertEquals(EscrowStatus.HELD, escrow.getStatus());
        assertEquals(42_000L, escrow.getRefundedAmount());
        assertEquals(12_000L, escrow.heldAmount());
        assertNull(escrow.getRefundedAt(), "아직 환불로 끝난 것이 아니다");
    }

    /**
     * 불변식 점검이 {@code escrows.amount} 와 {@code participations.total_amount} 를
     * 비교하므로, 부분 환불로 amount 를 줄이면 모든 부분 환불 건이 오류로 잡힙니다.
     */
    @Test
    @DisplayName("부분 환불해도 보관 시작 금액은 줄지 않는다 - 불변식 점검 기준이다")
    void partialRefundDoesNotShrinkOriginalAmount() {
        Escrow escrow = held();

        escrow.refundPartially(42_000L, LATER);

        assertEquals(AMOUNT, escrow.getAmount());
    }

    @Test
    @DisplayName("나눠서 환불할 수 있고 누계가 쌓인다")
    void partialRefundsAccumulate() {
        Escrow escrow = held();

        escrow.refundPartially(20_000L, LATER);
        escrow.refundPartially(10_000L, LATER);

        assertEquals(30_000L, escrow.getRefundedAmount());
        assertEquals(24_000L, escrow.heldAmount());
        assertTrue(escrow.isHeld());
    }

    @Test
    @DisplayName("누계가 보관 금액에 도달하면 환불로 종료된다")
    void refundsFullyWhenAccumulatedToTotal() {
        Escrow escrow = held();

        escrow.refundPartially(42_000L, LATER);
        escrow.refundPartially(12_000L, LATER);

        assertEquals(EscrowStatus.REFUNDED, escrow.getStatus());
        assertEquals(0L, escrow.heldAmount());
        assertEquals(LATER, escrow.getRefundedAt());
    }

    /** 넘으면 받은 적 없는 돈을 돌려주게 됩니다. */
    @Test
    @DisplayName("누계가 보관 금액을 넘는 환불은 거부한다")
    void rejectsRefundBeyondHeldAmount() {
        Escrow escrow = held();
        escrow.refundPartially(42_000L, LATER);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> escrow.refundPartially(13_000L, LATER));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        assertEquals(42_000L, escrow.getRefundedAmount(), "실패한 요청이 누계를 바꾸면 안 된다");
    }

    @Test
    @DisplayName("0원 이하 부분 환불은 거부한다")
    void rejectsNonPositivePartialRefund() {
        assertThrows(BusinessException.class, () -> held().refundPartially(0L, LATER));
        assertThrows(BusinessException.class, () -> held().refundPartially(-1L, LATER));
    }

    @Test
    @DisplayName("전액 환불은 누계도 전액으로 채운다 - 환불 총액 집계 기준이다")
    void fullRefundFillsRefundedAmount() {
        Escrow escrow = held();

        escrow.refund(LATER);

        assertEquals(AMOUNT, escrow.getRefundedAmount());
        assertEquals(0L, escrow.heldAmount());
    }

    @Test
    @DisplayName("부분 환불 후 남은 금액은 구매확정 시 해제된다")
    void releasesRemainderAfterPartialRefund() {
        Escrow escrow = held();
        escrow.refundPartially(42_000L, LATER);

        escrow.release(CONFIRMED, LATER);

        assertEquals(EscrowStatus.RELEASED, escrow.getStatus());
        assertEquals(12_000L, escrow.heldAmount(), "해제되는 금액은 잔액이다");
    }

    @Test
    @DisplayName("이미 환불로 끝난 건은 부분 환불할 수 없다")
    void rejectsPartialRefundAfterTermination() {
        Escrow escrow = held();
        escrow.refund(LATER);

        assertThrows(BusinessException.class, () -> escrow.refundPartially(1_000L, LATER));
    }
}
