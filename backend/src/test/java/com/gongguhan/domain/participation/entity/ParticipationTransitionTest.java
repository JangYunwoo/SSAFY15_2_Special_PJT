package com.gongguhan.domain.participation.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
