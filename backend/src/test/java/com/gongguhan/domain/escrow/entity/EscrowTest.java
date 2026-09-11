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
}
