package com.gongguhan.domain.participation.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ParticipationTransitionTest {

    private static final LocalDateTime DELIVERED_AT = LocalDateTime.of(2026, 1, 10, 0, 0);

    private Participation paidParticipation() {
        return Participation.create(100L, 1L, 50000L, 3000L, 53000L,
                "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동 101호");
    }

    @Test
    void createStartsAsPaid() {
        assertEquals(Participation.STATUS_PAID, paidParticipation().getStatus());
    }

    @Test
    void createHoldsActiveMarker() {
        assertEquals(Participation.ACTIVE_MARKER, paidParticipation().getActiveMarker());
    }

    /**
     * 종료된 뒤에도 마커가 남으면 전액 환불받은 사람이 같은 공동구매에 다시 참여할 수
     * 없습니다. uk_participation_active(group_buy_id, user_id, active_marker) 가
     * 막기 때문입니다. 스키마 주석도 "활성 Y, 취소 시 NULL" 입니다.
     */
    @Test
    void markRefundedReleasesActiveMarker() {
        Participation participation = paidParticipation();
        participation.markRefunding();
        participation.markRefunded();

        assertEquals(Participation.STATUS_REFUNDED, participation.getStatus());
        assertNull(participation.getActiveMarker());
    }

    @Test
    void fullTransitionPaidToDelivered() {
        Participation participation = paidParticipation();

        participation.markFulfilled();
        assertEquals(Participation.STATUS_FULFILLED, participation.getStatus());

        participation.markShipping();
        assertEquals(Participation.STATUS_SHIPPING, participation.getStatus());

        participation.markDelivered(DELIVERED_AT, DELIVERED_AT.plusDays(7), DELIVERED_AT.plusDays(7));
        assertEquals(Participation.STATUS_DELIVERED, participation.getStatus());
        assertEquals(DELIVERED_AT, participation.getDeliveredAt());
        assertEquals(DELIVERED_AT.plusDays(7), participation.getWithdrawalPeriodEndAt());
        assertEquals(DELIVERED_AT.plusDays(7), participation.getSettlementEligibleAt());
    }

    @Test
    void markFulfilledRejectedWhenNotPaid() {
        Participation participation = paidParticipation();
        participation.markFulfilled();

        BusinessException exception = assertThrows(BusinessException.class, participation::markFulfilled);

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void markShippingRejectedWhenNotFulfilled() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paidParticipation().markShipping());

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void markDeliveredRejectedWhenNotShipping() {
        Participation participation = paidParticipation();
        participation.markFulfilled();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> participation.markDelivered(DELIVERED_AT, DELIVERED_AT, DELIVERED_AT));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    private Participation deliveredParticipation() {
        Participation participation = paidParticipation();
        participation.markFulfilled();
        participation.markShipping();
        participation.markDelivered(DELIVERED_AT, DELIVERED_AT.plusDays(7), DELIVERED_AT.plusDays(7));
        return participation;
    }

    @Test
    void confirmManuallyMarksConfirmedWithNoticeAgreement() {
        Participation participation = deliveredParticipation();

        participation.confirmManually(DELIVERED_AT.plusDays(1));

        assertEquals(Participation.STATUS_CONFIRMED, participation.getStatus());
        assertEquals(Participation.CONFIRM_TYPE_MANUAL, participation.getConfirmType());
        assertEquals(DELIVERED_AT.plusDays(1), participation.getConfirmedAt());
        assertEquals(true, participation.isConfirmNoticeAgreed());
        assertEquals(DELIVERED_AT.plusDays(7), participation.getSettlementEligibleAt());
    }

    @Test
    void confirmAutomaticallyMarksConfirmedWithoutNoticeAgreement() {
        Participation participation = deliveredParticipation();

        participation.confirmAutomatically(DELIVERED_AT.plusDays(7));

        assertEquals(Participation.STATUS_CONFIRMED, participation.getStatus());
        assertEquals(Participation.CONFIRM_TYPE_AUTO, participation.getConfirmType());
        assertEquals(false, participation.isConfirmNoticeAgreed());
    }

    @Test
    void confirmRejectedBeforeDelivered() {
        Participation participation = paidParticipation();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> participation.confirmManually(DELIVERED_AT));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }

    @Test
    void confirmRejectedTwice() {
        Participation participation = deliveredParticipation();
        participation.confirmManually(DELIVERED_AT.plusDays(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> participation.confirmAutomatically(DELIVERED_AT.plusDays(7)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }
}
