package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentSlotReservationTransactionTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    private GroupBuyRepository groupBuyRepository;
    private ParticipationRepository participationRepository;
    private PaymentRepository paymentRepository;
    private UserAddressRepository userAddressRepository;
    private PaymentSlotReservationTransaction transaction;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        transaction = new PaymentSlotReservationTransaction(
                groupBuyRepository, participationRepository, paymentRepository, userAddressRepository, clock);
    }

    private PaymentPrepareRequest cardRequest() {
        return new PaymentPrepareRequest("CARD", 10L, null);
    }

    @Test
    void failsWhenGroupBuyNotFound() {
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void failsWhenActiveParticipationExists() {
        when(groupBuyRepository.findById(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(participationRepository.existsByGroupBuyIdAndUserIdAndActiveMarker(
                GROUP_BUY_ID, USER_ID, Participation.ACTIVE_MARKER)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.DUPLICATE_PARTICIPATION, exception.getErrorCode());
    }

    @Test
    void failsWhenActiveSlotExists() {
        when(groupBuyRepository.findById(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(paymentRepository.existsByGroupBuyIdAndUserIdAndStatus(
                GROUP_BUY_ID, USER_ID, Payment.STATUS_PROCESSING)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.DUPLICATE_PARTICIPATION, exception.getErrorCode());
    }

    @Test
    void failsWhenAddressNotOwnedByUser() {
        when(groupBuyRepository.findById(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void failsWhenCryptoMethodMissingAssetId() {
        when(groupBuyRepository.findById(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(mock(UserAddress.class)));

        PaymentPrepareRequest request = new PaymentPrepareRequest("CRYPTO", 10L, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, request, IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void failsWhenGroupBuyIsFull() {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(mock(UserAddress.class)));
        doThrow(new BusinessException(ErrorCode.GROUP_BUY_FULL)).when(groupBuy).reserveSlot();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> transaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.GROUP_BUY_FULL, exception.getErrorCode());
    }
}
