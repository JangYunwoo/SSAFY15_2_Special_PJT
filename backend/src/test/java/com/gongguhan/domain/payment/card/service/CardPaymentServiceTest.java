package com.gongguhan.domain.payment.card.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.entity.CardPayment;
import com.gongguhan.domain.payment.card.repository.CardPaymentRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class CardPaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PAYMENT_ID = 200L;
    private static final Long ADDRESS_ID = 10L;
    private static final String INSTITUTION_TX_NO = "20260408135600123456";

    private PaymentRepository paymentRepository;
    private UserRepository userRepository;
    private UserAddressRepository userAddressRepository;
    private CardPaymentRepository cardPaymentRepository;
    private PaymentService paymentService;
    private PolicyService policyService;
    private SsafyCardApiClient ssafyCardApiClient;
    private SsafyApiLogService ssafyApiLogService;
    private CardPaymentService cardPaymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        userRepository = mock(UserRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        cardPaymentRepository = mock(CardPaymentRepository.class);
        paymentService = mock(PaymentService.class);
        policyService = mock(PolicyService.class);
        ssafyCardApiClient = mock(SsafyCardApiClient.class);
        ssafyApiLogService = mock(SsafyApiLogService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        cardPaymentService = new CardPaymentService(
                paymentRepository, userRepository, userAddressRepository, cardPaymentRepository,
                paymentService, policyService, ssafyCardApiClient, ssafyApiLogService,
                new ObjectMapper(), clock);
    }

    private CardApproveRequest request() {
        return new CardApproveRequest("1005518816096479", "725");
    }

    private Payment approvablePayment() {
        Payment payment = mock(Payment.class);
        when(payment.getPaymentId()).thenReturn(PAYMENT_ID);
        when(payment.getMethod()).thenReturn("CARD");
        when(payment.isProcessing()).thenReturn(true);
        when(payment.isExpired(any())).thenReturn(false);
        when(payment.getAddressId()).thenReturn(ADDRESS_ID);
        when(payment.getKrwAmount()).thenReturn(54000L);
        return payment;
    }

    private void stubOwnedPayment(Payment payment) {
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.of(payment));
    }

    private void stubLinkedUser() {
        User user = mock(User.class);
        when(user.getSsafyUserKey()).thenReturn("userkey-abc");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void stubPolicyAndInstitutionNumber() {
        when(policyService.getLong(PolicyKey.SSAFY_MERCHANT_ID)).thenReturn(1L);
        when(policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(ssafyApiLogService.generateInstitutionTransactionUniqueNo(anyString())).thenReturn(INSTITUTION_TX_NO);
    }

    private SsafyCardApiClient.SsafyCardTransactionResponse successResponse() {
        return new SsafyCardApiClient.SsafyCardTransactionResponse(
                new SsafyCardApiClient.SsafyCardTransactionResponse.Header("H0000", "정상처리 되었습니다."),
                new SsafyCardApiClient.SsafyCardTransactionResponse.Rec(
                        "12", "CG-4fa85f6425ad1d3", "대형마트", "1", "코스트코",
                        "20260408", "135242", "54000"));
    }

    @Test
    void failsWhenNotOwner() {
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
        verifyNoInteractions(ssafyCardApiClient, ssafyApiLogService, policyService);
    }

    @Test
    void failsWhenMethodIsNotCard() {
        Payment payment = approvablePayment();
        when(payment.getMethod()).thenReturn("KAKAOPAY");
        stubOwnedPayment(payment);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verifyNoInteractions(ssafyCardApiClient, ssafyApiLogService, policyService, userRepository);
    }

    @Test
    void failsWhenAlreadyProcessed() {
        Payment payment = approvablePayment();
        when(payment.isProcessing()).thenReturn(false);
        stubOwnedPayment(payment);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, exception.getErrorCode());
        verifyNoInteractions(ssafyCardApiClient, ssafyApiLogService, policyService, userRepository);
    }

    @Test
    void failsWhenSlotExpired() {
        Payment payment = approvablePayment();
        when(payment.isExpired(any())).thenReturn(true);
        stubOwnedPayment(payment);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.PAYMENT_SLOT_EXPIRED, exception.getErrorCode());
        verifyNoInteractions(ssafyCardApiClient, ssafyApiLogService, policyService, userRepository);
    }

    @Test
    void failsWhenSsafyMemberNotLinked() {
        Payment payment = approvablePayment();
        stubOwnedPayment(payment);
        User user = mock(User.class);
        when(user.getSsafyUserKey()).thenReturn(null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.SSAFY_MEMBER_NOT_LINKED, exception.getErrorCode());
        verifyNoInteractions(ssafyCardApiClient, ssafyApiLogService, policyService);
    }

    @Test
    void failsPaymentAndPropagatesWhenSsafyDeclines() {
        Payment payment = approvablePayment();
        stubOwnedPayment(payment);
        stubLinkedUser();
        stubPolicyAndInstitutionNumber();
        SsafyCardApiClient.SsafyCardTransactionResponse declined = new SsafyCardApiClient.SsafyCardTransactionResponse(
                new SsafyCardApiClient.SsafyCardTransactionResponse.Header("A1054", "카드번호가 유효하지 않습니다."),
                null);
        when(ssafyCardApiClient.createCreditCardTransaction(
                eq(INSTITUTION_TX_NO), eq("00100"), eq("001"), eq("userkey-abc"),
                eq("1005518816096479"), eq("725"), eq(1L), eq(54000L)))
                .thenReturn(declined);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardPaymentService.approve(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.EXTERNAL_PAYMENT_ERROR, exception.getErrorCode());
        verify(paymentService).failPayment(PAYMENT_ID, "A1054");
        verify(paymentService, never()).completePayment(any(), any());
        verify(cardPaymentRepository, never()).save(any());
        verify(ssafyApiLogService).record(eq("createCreditCardTransaction"), eq(INSTITUTION_TX_NO),
                eq("PAYMENT"), eq(PAYMENT_ID), any(), eq("A1054"), eq("카드번호가 유효하지 않습니다."), eq(declined));
    }

    @Test
    void approvesCardAndCompletesPaymentOnSuccess() {
        Payment payment = approvablePayment();
        stubOwnedPayment(payment);
        stubLinkedUser();
        stubPolicyAndInstitutionNumber();
        when(ssafyCardApiClient.createCreditCardTransaction(
                eq(INSTITUTION_TX_NO), eq("00100"), eq("001"), eq("userkey-abc"),
                eq("1005518816096479"), eq("725"), eq(1L), eq(54000L)))
                .thenReturn(successResponse());

        UserAddress address = mock(UserAddress.class);
        when(address.getRecipientName()).thenReturn("홍길동");
        when(address.getRecipientPhone()).thenReturn("010-1234-5678");
        when(address.getZipcode()).thenReturn("06236");
        when(address.getAddress1()).thenReturn("서울시 강남구");
        when(address.getAddress2()).thenReturn("101동 101호");
        when(userAddressRepository.findById(ADDRESS_ID)).thenReturn(Optional.of(address));

        cardPaymentService.approve(USER_ID, PAYMENT_ID, request());

        ArgumentCaptor<CardPayment> cardPaymentCaptor = ArgumentCaptor.forClass(CardPayment.class);
        verify(cardPaymentRepository).save(cardPaymentCaptor.capture());
        CardPayment saved = cardPaymentCaptor.getValue();
        assertEquals(PAYMENT_ID, saved.getPaymentId());
        assertEquals("************6479", saved.getCardNoMasked());
        assertEquals(12L, saved.getTransactionUniqueNo());

        ArgumentCaptor<PaymentService.ParticipationSnapshot> snapshotCaptor =
                ArgumentCaptor.forClass(PaymentService.ParticipationSnapshot.class);
        verify(paymentService).completePayment(eq(PAYMENT_ID), snapshotCaptor.capture());
        assertEquals("홍길동", snapshotCaptor.getValue().recipientName());
        verify(paymentService, never()).failPayment(any(), any());
        verify(ssafyApiLogService).record(eq("createCreditCardTransaction"), eq(INSTITUTION_TX_NO),
                eq("PAYMENT"), eq(PAYMENT_ID), any(), eq("H0000"), eq("정상처리 되었습니다."), any());
    }
}
