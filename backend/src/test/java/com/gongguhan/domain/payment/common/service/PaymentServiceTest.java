package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
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

class PaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    private GroupBuyRepository groupBuyRepository;
    private ParticipationRepository participationRepository;
    private EscrowRepository escrowRepository;
    private PaymentRepository paymentRepository;
    private UserAddressRepository userAddressRepository;
    private GroupBuySlotGate groupBuySlotGate;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        escrowRepository = mock(EscrowRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        groupBuySlotGate = mock(GroupBuySlotGate.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        paymentService = new PaymentService(
                groupBuyRepository, participationRepository, escrowRepository,
                paymentRepository, userAddressRepository, groupBuySlotGate, clock);
    }

    private PaymentPrepareRequest cardRequest() {
        return new PaymentPrepareRequest("CARD", 10L, null);
    }

    @Test
    void prepareFailsWhenGroupBuyNotFound() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void prepareFailsWhenActiveParticipationExists() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(participationRepository.existsByGroupBuyIdAndUserIdAndActiveMarker(
                GROUP_BUY_ID, USER_ID, Participation.ACTIVE_MARKER)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.DUPLICATE_PARTICIPATION, exception.getErrorCode());
    }

    @Test
    void prepareFailsWhenActiveSlotExists() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(paymentRepository.existsByGroupBuyIdAndUserIdAndStatus(
                GROUP_BUY_ID, USER_ID, Payment.STATUS_PROCESSING)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.DUPLICATE_PARTICIPATION, exception.getErrorCode());
    }

    @Test
    void prepareFailsWhenAddressNotOwnedByUser() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void prepareFailsWhenCryptoMethodMissingAssetId() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID))
                .thenReturn(Optional.of(mock(GroupBuy.class)));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(mock(UserAddress.class)));

        PaymentPrepareRequest request = new PaymentPrepareRequest("CRYPTO", 10L, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, request, IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void prepareFailsWhenGroupBuyIsFull() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(userAddressRepository.findByAddressIdAndUserId(10L, USER_ID))
                .thenReturn(Optional.of(mock(UserAddress.class)));
        doThrow(new BusinessException(ErrorCode.GROUP_BUY_FULL)).when(groupBuy).reserveSlot();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.GROUP_BUY_FULL, exception.getErrorCode());
    }

    @Test
    void prepareReturnsExistingPaymentOnIdempotentRetry() {
        Payment existing = mock(Payment.class);
        when(existing.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(existing.getPaymentId()).thenReturn(PAYMENT_ID);
        when(existing.getMethod()).thenReturn("CARD");
        when(existing.getStatus()).thenReturn(Payment.STATUS_PROCESSING);
        when(existing.isProcessing()).thenReturn(true);
        when(existing.getSlotExpiresAt()).thenReturn(java.time.LocalDateTime.now());
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY);

        verify(paymentRepository, never()).saveAndFlush(any(Payment.class));
        verify(groupBuyRepository, never()).findWithLockByGroupBuyId(any());
    }

    @Test
    void abortFailsWhenNotOwner() {
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.abort(USER_ID, PAYMENT_ID));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void abortFailsWhenPaymentAlreadyProcessed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(false);
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.abort(USER_ID, PAYMENT_ID));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, exception.getErrorCode());
    }

    @Test
    void abortReleasesSlotAndMarksFailed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        paymentService.abort(USER_ID, PAYMENT_ID);

        verify(groupBuy).releaseSlot();
        verify(groupBuySlotGate).release(GROUP_BUY_ID);
        verify(payment).markFailed("USER_ABORTED");
    }

    @Test
    void prepareRejectsImmediatelyWhenGateDeniesWithoutTouchingPessimisticLock() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(50);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 50)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.GROUP_BUY_FULL, exception.getErrorCode());
        verify(groupBuyRepository, never()).findWithLockByGroupBuyId(any());
    }

    @Test
    void prepareReleasesGateWhenDbSideFailsAfterGateGrant() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        verify(groupBuySlotGate, times(1)).release(GROUP_BUY_ID);
    }
}
