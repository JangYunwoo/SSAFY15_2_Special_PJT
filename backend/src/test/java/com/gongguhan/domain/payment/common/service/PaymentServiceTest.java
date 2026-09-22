package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final String IDEMPOTENCY_KEY = "idem-key-1";

    private GroupBuyRepository groupBuyRepository;
    private PaymentRepository paymentRepository;
    private UserAddressRepository userAddressRepository;
    private GroupBuySlotGate groupBuySlotGate;
    private GroupBuySlotPort groupBuySlotPort;
    private ParticipationWriterPort participationWriterPort;
    private EscrowWriterPort escrowWriterPort;
    private PaymentSlotReservationTransaction slotReservationTransaction;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        groupBuySlotGate = mock(GroupBuySlotGate.class);
        groupBuySlotPort = mock(GroupBuySlotPort.class);
        participationWriterPort = mock(ParticipationWriterPort.class);
        escrowWriterPort = mock(EscrowWriterPort.class);
        slotReservationTransaction = mock(PaymentSlotReservationTransaction.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        paymentService = new PaymentService(
                groupBuyRepository, paymentRepository, userAddressRepository, groupBuySlotGate,
                groupBuySlotPort, participationWriterPort, escrowWriterPort,
                slotReservationTransaction, clock);
    }

    private GroupBuySlotPort.GroupBuySlot slotView() {
        return new GroupBuySlotPort.GroupBuySlot(
                GROUP_BUY_ID, 50000L, 3000L, 50, 0, 0,
                LocalDateTime.now(), GroupBuy.STATUS_RECRUITING);
    }

    private PaymentPrepareRequest cardRequest() {
        return new PaymentPrepareRequest("CARD", 10L, null);
    }

    @Test
    void prepareReturnsExistingPaymentOnIdempotentRetry() {
        Payment existing = mock(Payment.class);
        when(existing.getUserId()).thenReturn(USER_ID);
        when(existing.getAddressId()).thenReturn(10L);
        when(existing.getPrepareCryptoAssetId()).thenReturn(null);
        when(existing.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(existing.getPaymentId()).thenReturn(PAYMENT_ID);
        when(existing.getMethod()).thenReturn("CARD");
        when(existing.getStatus()).thenReturn(Payment.STATUS_PROCESSING);
        when(existing.isProcessing()).thenReturn(true);
        when(existing.getSlotExpiresAt()).thenReturn(java.time.LocalDateTime.now());
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(existing));
        when(groupBuySlotPort.get(GROUP_BUY_ID)).thenReturn(slotView());

        paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY);

        verify(groupBuyRepository, never()).findSlotCounts(any());
        verify(slotReservationTransaction, never()).execute(any(), any(), any(), any());
    }

    @Test
    void prepareReusesActiveSlotWhenIdempotencyKeyDiffers() {
        // 새로고침하면 클라이언트가 만든 멱등키가 사라져 같은 화면에서도 새 키로 온다.
        // 그때마다 자리를 새로 잡으면 사용자가 자기 자신 때문에 정원을 채운다.
        Payment activeSlot = mock(Payment.class);
        when(activeSlot.getUserId()).thenReturn(USER_ID);
        when(activeSlot.getAddressId()).thenReturn(10L);
        when(activeSlot.getPrepareCryptoAssetId()).thenReturn(null);
        when(activeSlot.getMethod()).thenReturn("CARD");
        when(activeSlot.isExpired(any())).thenReturn(false);
        when(activeSlot.getPaymentId()).thenReturn(PAYMENT_ID);
        when(activeSlot.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(activeSlot.getStatus()).thenReturn(Payment.STATUS_PROCESSING);
        when(activeSlot.isProcessing()).thenReturn(true);
        when(activeSlot.getSlotExpiresAt()).thenReturn(LocalDateTime.now());
        when(paymentRepository.findByIdempotencyKey("another-key")).thenReturn(Optional.empty());
        when(paymentRepository.findByGroupBuyIdAndUserIdAndStatus(
                GROUP_BUY_ID, USER_ID, Payment.STATUS_PROCESSING)).thenReturn(Optional.of(activeSlot));
        when(groupBuySlotPort.get(GROUP_BUY_ID)).thenReturn(slotView());

        PaymentResponse response =
                paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), "another-key");

        assertEquals(String.valueOf(PAYMENT_ID), response.paymentId());
        verify(groupBuySlotGate, never()).tryReserve(any(), anyInt(), anyInt());
        verify(slotReservationTransaction, never()).execute(any(), any(), any(), any());
    }

    @Test
    void prepareDoesNotReuseActiveSlotOfAnotherMethod() {
        // 카드로 잡아둔 자리를 코인 결제로 이어 쓸 수 없다. 금액 검증 대상과 승인
        // 경로가 다르다. 이전 자리 반납은 호출 측(PAY-03)이 한다.
        Payment cardSlot = mock(Payment.class);
        when(cardSlot.getMethod()).thenReturn("CARD");
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(paymentRepository.findByGroupBuyIdAndUserIdAndStatus(
                GROUP_BUY_ID, USER_ID, Payment.STATUS_PROCESSING)).thenReturn(Optional.of(cardSlot));
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        PaymentPrepareRequest cryptoRequest = new PaymentPrepareRequest("CRYPTO", 10L, 1L);
        PaymentResponse expected = mock(PaymentResponse.class);
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cryptoRequest, IDEMPOTENCY_KEY))
                .thenReturn(expected);

        PaymentResponse response =
                paymentService.prepare(USER_ID, GROUP_BUY_ID, cryptoRequest, IDEMPOTENCY_KEY);

        assertEquals(expected, response);
    }

    @Test
    void prepareRejectsImmediatelyWhenGateDeniesWithoutStartingDbTransaction() {
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
        verify(slotReservationTransaction, never()).execute(any(), any(), any(), any());
    }

    @Test
    void prepareDelegatesToTransactionWhenGateGrants() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        PaymentResponse expected = mock(PaymentResponse.class);
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenReturn(expected);

        PaymentResponse response = paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY);

        assertEquals(expected, response);
        verify(groupBuySlotGate, never()).release(any());
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
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenThrow(new BusinessException(ErrorCode.DUPLICATE_PARTICIPATION));

        assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        verify(groupBuySlotGate, times(1)).release(GROUP_BUY_ID);
        // 낙관적 락 충돌이 아닌 업무 규칙 위반은 재시도해도 결과가 같으므로 재시도하지 않는다.
        verify(slotReservationTransaction, times(1)).execute(any(), any(), any(), any());
    }

    @Test
    void prepareRetriesOnOptimisticLockConflictThenSucceeds() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        PaymentResponse expected = mock(PaymentResponse.class);
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenThrow(new ObjectOptimisticLockingFailureException(GroupBuy.class, GROUP_BUY_ID))
                .thenReturn(expected);

        PaymentResponse response = paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY);

        assertEquals(expected, response);
        verify(slotReservationTransaction, times(2)).execute(any(), any(), any(), any());
        verify(groupBuySlotGate, never()).release(any());
    }

    @Test
    void prepareReleasesGateAndThrowsConflictWhenRetriesExhausted() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenThrow(new ObjectOptimisticLockingFailureException(GroupBuy.class, GROUP_BUY_ID));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.SLOT_RESERVATION_CONFLICT, exception.getErrorCode());
        verify(slotReservationTransaction, times(3)).execute(any(), any(), any(), any());
        verify(groupBuySlotGate, times(1)).release(GROUP_BUY_ID);
    }

    /**
     * 부하테스트로 재현된 실제 상황: 비관적 락을 뗀 뒤 여러 트랜잭션이 payments의
     * group_buy_id FK 체크로 group_buys 행에 공유 락을 동시에 잡고, 각자 UPDATE로 배타
     * 락 승격을 시도하면서 InnoDB 데드락(CannotAcquireLockException)이 발생했다 - 버전
     * 충돌(ObjectOptimisticLockingFailureException)이 아니라도 재시도 대상이어야 한다.
     */
    @Test
    void prepareRetriesOnDeadlockThenSucceeds() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        GroupBuyRepository.SlotCounts slotCounts = mock(GroupBuyRepository.SlotCounts.class);
        when(slotCounts.getReservedSlots()).thenReturn(0);
        when(slotCounts.getCurrentParticipants()).thenReturn(0);
        when(slotCounts.getTargetParticipants()).thenReturn(50);
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.of(slotCounts));
        when(groupBuySlotGate.tryReserve(GROUP_BUY_ID, 50, 0)).thenReturn(true);
        PaymentResponse expected = mock(PaymentResponse.class);
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
                .thenReturn(expected);

        PaymentResponse response = paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY);

        assertEquals(expected, response);
        verify(slotReservationTransaction, times(2)).execute(any(), any(), any(), any());
        verify(groupBuySlotGate, never()).release(any());
    }

    @Test
    void prepareSkipsGateWhenGroupBuyNotFound() {
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(groupBuyRepository.findSlotCounts(GROUP_BUY_ID)).thenReturn(Optional.empty());
        when(slotReservationTransaction.execute(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.prepare(USER_ID, GROUP_BUY_ID, cardRequest(), IDEMPOTENCY_KEY));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(groupBuySlotGate, never()).tryReserve(any(), anyInt(), anyInt());
        verify(groupBuySlotGate, never()).release(any());
    }

    @Test
    void abortFailsWhenNotOwner() {
        when(paymentRepository.findForUpdateByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.abort(USER_ID, PAYMENT_ID));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
    }

    @Test
    void abortFailsWhenPaymentAlreadyProcessed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.abort(USER_ID, PAYMENT_ID));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, exception.getErrorCode());
    }

    @Test
    void abortReleasesSlotAndMarksFailed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(paymentRepository.findForUpdateByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));

        paymentService.abort(USER_ID, PAYMENT_ID);

        verify(groupBuySlotPort).release(GROUP_BUY_ID);
        verify(groupBuySlotGate).release(GROUP_BUY_ID);
        verify(payment).markFailed("USER_ABORTED");
    }

    @Test
    void findExpiredSlotPaymentIdsMapsRepositoryResultToIds() {
        Payment payment = mock(Payment.class);
        when(payment.getPaymentId()).thenReturn(PAYMENT_ID);
        when(paymentRepository.findByStatusAndSlotExpiresAtBefore(
                Payment.STATUS_PROCESSING, LocalDateTime.now(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC))))
                .thenReturn(List.of(payment));

        List<Long> ids = paymentService.findExpiredSlotPaymentIds();

        assertEquals(List.of(PAYMENT_ID), ids);
    }

    @Test
    void listHistoryLoadsAllGroupBuySlotsInOneBatch() {
        Payment first = historyPayment(201L, 101L);
        Payment second = historyPayment(202L, 101L);
        Payment third = historyPayment(203L, 102L);
        PageRequest page = PageRequest.of(0, 20);
        when(paymentRepository.findByUserId(USER_ID, page))
                .thenReturn(new PageImpl<>(List.of(first, second, third), page, 3));
        GroupBuySlotPort.GroupBuySlot firstSlot = slotView(101L);
        GroupBuySlotPort.GroupBuySlot secondSlot = slotView(102L);
        when(groupBuySlotPort.getAll(Set.of(101L, 102L)))
                .thenReturn(Map.of(101L, firstSlot, 102L, secondSlot));

        var history = paymentService.listHistory(USER_ID, page);

        assertEquals(
                List.of("101", "101", "102"),
                history.map(PaymentResponse::groupBuyId).toList());
        verify(groupBuySlotPort).getAll(Set.of(101L, 102L));
        verify(groupBuySlotPort, never()).get(any());
    }

    @Test
    void expireSlotIfDueDoesNothingWhenPaymentMissing() {
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        paymentService.expireSlotIfDue(PAYMENT_ID);

        verify(groupBuySlotPort, never()).release(any());
    }

    @Test
    void expireSlotIfDueDoesNothingWhenAlreadyResolved() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        paymentService.expireSlotIfDue(PAYMENT_ID);

        verify(groupBuySlotPort, never()).release(any());
        verify(payment, never()).markExpired();
    }

    @Test
    void expireSlotIfDueDoesNothingWhenNotYetExpired() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        paymentService.expireSlotIfDue(PAYMENT_ID);

        verify(groupBuySlotPort, never()).release(any());
        verify(payment, never()).markExpired();
    }

    @Test
    void expireSlotIfDueReleasesSlotAndMarksExpired() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(true);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        paymentService.expireSlotIfDue(PAYMENT_ID);

        verify(groupBuySlotPort).release(GROUP_BUY_ID);
        verify(groupBuySlotGate).release(GROUP_BUY_ID);
        verify(payment).markExpired();
    }

    @Test
    void expireSlotIfDueFailsWhenGroupBuyMissing() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(true);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(groupBuySlotPort.release(GROUP_BUY_ID)).thenThrow(
                new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.expireSlotIfDue(PAYMENT_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(payment, never()).markExpired();
    }

    private Payment historyPayment(Long paymentId, Long groupBuyId) {
        Payment payment = mock(Payment.class);
        when(payment.getPaymentId()).thenReturn(paymentId);
        when(payment.getGroupBuyId()).thenReturn(groupBuyId);
        when(payment.getStatus()).thenReturn(Payment.STATUS_PROCESSING);
        when(payment.getMethod()).thenReturn("CARD");
        when(payment.getKrwAmount()).thenReturn(53_000L);
        when(payment.getSlotExpiresAt()).thenReturn(LocalDateTime.of(2026, 1, 1, 1, 0));
        when(payment.isProcessing()).thenReturn(true);
        return payment;
    }

    private GroupBuySlotPort.GroupBuySlot slotView(Long groupBuyId) {
        return new GroupBuySlotPort.GroupBuySlot(
                groupBuyId, 50_000L, 3_000L, 50, 0, 0,
                LocalDateTime.of(2026, 1, 2, 0, 0), GroupBuy.STATUS_RECRUITING);
    }

    @Test
    void completePaymentFailsWhenAlreadyProcessed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.completePayment(PAYMENT_ID));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, exception.getErrorCode());
        verify(groupBuySlotPort, never()).confirm(any());
    }

    @Test
    void completePaymentFailsWhenSlotExpired() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(true);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.completePayment(PAYMENT_ID));

        assertEquals(ErrorCode.PAYMENT_SLOT_EXPIRED, exception.getErrorCode());
        verify(groupBuySlotPort, never()).confirm(any());
    }

    @Test
    void completePaymentConfirmsSlotCreatesParticipationHoldsEscrowAndMarksPaid() {
        Payment payment = mock(Payment.class);
        when(payment.getShippingSnapshot()).thenReturn(new com.gongguhan.domain.payment.common.entity.PaymentShippingSnapshot(
                "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동 101호", "문 앞"));
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(false);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(payment.getUserId()).thenReturn(USER_ID);
        when(payment.getKrwAmount()).thenReturn(53000L);
        // completePayment가 이제 PaymentResponse를 반환하므로(카카오페이 연동, -73)
        // 응답 조립에 쓰이는 슬롯 만료 시각도 스텁이 필요하다.
        when(payment.getSlotExpiresAt()).thenReturn(LocalDateTime.now());
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(groupBuySlotPort.confirmForCompletion(GROUP_BUY_ID))
                .thenReturn(GroupBuySlotPort.ConfirmOutcome.confirmed(slotView()));
        ParticipationWriterPort.ParticipationRecord participationRecord =
                new ParticipationWriterPort.ParticipationRecord(300L, GROUP_BUY_ID, USER_ID);
        when(participationWriterPort.create(any())).thenReturn(participationRecord);

        paymentService.completePayment(PAYMENT_ID);

        ArgumentCaptor<ParticipationWriterPort.ParticipationCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(ParticipationWriterPort.ParticipationCreateCommand.class);
        verify(participationWriterPort).create(commandCaptor.capture());
        ParticipationWriterPort.ParticipationCreateCommand command = commandCaptor.getValue();
        assertEquals(GROUP_BUY_ID, command.groupBuyId());
        assertEquals(USER_ID, command.userId());
        assertEquals(53000L, command.totalAmount());
        assertEquals("홍길동", command.recipientName());

        verify(escrowWriterPort).hold(eq(300L), eq(GROUP_BUY_ID), eq(53000L), any());
        verify(payment).markPaid(eq(300L), any());
    }

    @Test
    void failPaymentFailsWhenPaymentMissing() {
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.failPayment(PAYMENT_ID, "H1054"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void failPaymentDoesNothingWhenAlreadyResolved() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(false);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        paymentService.failPayment(PAYMENT_ID, "H1054");

        verify(groupBuySlotPort, never()).release(any());
        verify(payment, never()).markFailed(any());
    }

    @Test
    void failPaymentReleasesSlotAndMarksFailed() {
        Payment payment = mock(Payment.class);
        when(payment.isProcessing()).thenReturn(true);
        when(payment.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(paymentRepository.findForUpdateByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(payment));

        paymentService.failPayment(PAYMENT_ID, "H1054");

        verify(groupBuySlotPort).release(GROUP_BUY_ID);
        verify(groupBuySlotGate).release(GROUP_BUY_ID);
        verify(payment).markFailed("H1054");
    }
}
