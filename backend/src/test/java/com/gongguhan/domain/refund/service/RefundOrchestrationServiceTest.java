package com.gongguhan.domain.refund.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.crypto.service.CryptoRefundService;
import com.gongguhan.domain.admin.service.AuditLogService;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.entity.RefundStatus;
import com.gongguhan.domain.refund.service.RefundLedgerService.OpenResult;
import com.gongguhan.domain.payment.common.port.CardRefundPort;
import com.gongguhan.domain.returnrequest.port.RefundPort;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 환불 실행 오케스트레이션 검증.
 *
 * <p><b>이 테스트의 중심은 "돈이 두 번 나가지 않는다" 와 "조용히 실패하지 않는다"
 * 입니다.</b> 환불은 되돌릴 수 없으므로, 보내야 할 때 보내는 것보다 <b>보내면 안 될 때
 * 보내지 않는 것</b>이 더 중요합니다.
 */
class RefundOrchestrationServiceTest {

    private static final Long PARTICIPATION_ID = 501L;
    private static final Long PAYMENT_ID = 900L;
    private static final Long USER_ID = 1L;
    private static final Long REFUND_ID = 7001L;
    private static final long AMOUNT = 54_000L;
    private static final String PLATFORM_ACCOUNT = "0016174648358792";
    private static final String BUYER_ACCOUNT = "0204667768182760";
    private static final String USER_KEY = "2695628f-11a1-418e-b533-9ae19e0650ec";
    private static final String INSTITUTION_TX_NO = "20260914103500123456";
    private static final String BUYER_WALLET = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final String CRYPTO_TX_HASH =
            "0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134";

    private RefundLedgerService ledgerService;
    private PaymentRepository paymentRepository;
    private UserRepository userRepository;
    private SsafyTransferClient transferClient;
    private SsafyApiLogService ssafyApiLogService;
    private CryptoRefundService cryptoRefundService;
    private AuditLogService auditLogService;
    private PolicyService policyService;
    private RefundPort kakaoPort;
    private CardRefundPort cardRefundPort;
    private RefundFinalizationService finalizationService;

    private RefundOrchestrationService service;

    @BeforeEach
    void setUp() {
        ledgerService = mock(RefundLedgerService.class);
        paymentRepository = mock(PaymentRepository.class);
        userRepository = mock(UserRepository.class);
        transferClient = mock(SsafyTransferClient.class);
        ssafyApiLogService = mock(SsafyApiLogService.class);
        cryptoRefundService = mock(CryptoRefundService.class);
        auditLogService = mock(AuditLogService.class);
        policyService = mock(PolicyService.class);
        kakaoPort = mock(RefundPort.class);
        cardRefundPort = mock(CardRefundPort.class);
        finalizationService = mock(RefundFinalizationService.class);

        when(ssafyApiLogService.generateInstitutionTransactionUniqueNo(anyString()))
                .thenReturn(INSTITUTION_TX_NO);

        // 실제 원장은 갱신된 Refund 를 돌려줍니다. 스터빙하지 않으면 mock 이 null 을
        // 돌려줘 실제와 다르게 동작합니다 - 오케스트레이션이 그 반환값으로 후속 처리
        // (에스크로 정리)를 하므로 여기서도 같은 계약을 지켜야 합니다.
        Refund settled = mock(Refund.class);
        when(settled.getStatus()).thenReturn(RefundStatus.COMPLETED);
        when(settled.getParticipationId()).thenReturn(PARTICIPATION_ID);
        when(settled.getAmount()).thenReturn(AMOUNT);
        when(settled.getReasonType()).thenReturn(RefundReason.PARTICIPATION_CANCEL);
        when(ledgerService.completeByCardCancel(anyLong(), any())).thenReturn(settled);
        when(ledgerService.completeByAccountTransfer(anyLong(), anyString(), any()))
                .thenReturn(settled);
        when(ledgerService.completeByKakaoPay(anyLong(), anyString())).thenReturn(settled);
        when(ledgerService.completeByCrypto(anyLong(), anyString(), any())).thenReturn(settled);

        Refund failed = mock(Refund.class);
        when(failed.getStatus()).thenReturn(RefundStatus.FAILED);
        when(ledgerService.fail(anyLong(), anyString())).thenReturn(failed);

        service = new RefundOrchestrationService(ledgerService, paymentRepository, userRepository,
                transferClient, ssafyApiLogService, cryptoRefundService, auditLogService,
                policyService, List.of(kakaoPort), cardRefundPort, finalizationService);
    }

    // ------------------------------------------------------------------
    // 고정 객체
    // ------------------------------------------------------------------

    private Payment payment(String method) {
        Payment payment = mock(Payment.class);
        when(payment.getMethod()).thenReturn(method);
        when(payment.getUserId()).thenReturn(USER_ID);
        when(payment.getPaymentId()).thenReturn(PAYMENT_ID);
        return payment;
    }

    private Refund refund(RefundMethod method) {
        Refund refund = mock(Refund.class);
        when(refund.getRefundId()).thenReturn(REFUND_ID);
        when(refund.getRefundMethod()).thenReturn(method);
        when(refund.getAmount()).thenReturn(AMOUNT);
        return refund;
    }

    private User buyer(String accountNo, String userKey) {
        User user = mock(User.class);
        when(user.getSsafyAccountNo()).thenReturn(accountNo);
        when(user.getSsafyUserKey()).thenReturn(userKey);
        return user;
    }

    /**
     * 정상 계좌이체가 가능한 상태로 협력 객체를 세웁니다.
     *
     * <p>스텁 객체를 {@code when(...)} 호출 <b>밖에서</b> 먼저 완성합니다. 인자 안에서
     * 다른 mock 을 스터빙하면 스터빙이 중첩되어 {@code UnfinishedStubbingException} 이
     * 납니다.
     */
    private Refund givenTransferable() {
        Payment payment = payment("CARD");
        Refund refund = refund(RefundMethod.ACCOUNT_TRANSFER);
        User user = buyer(BUYER_ACCOUNT, USER_KEY);
        OpenResult opened = new OpenResult(refund, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);
        when(policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        return refund;
    }

    private SsafyTransferClient.TransferResponse transferResponse(String code) {
        return new SsafyTransferClient.TransferResponse(
                new SsafyTransferClient.TransferResponse.Header(code, "메시지", INSTITUTION_TX_NO),
                List.of(new SsafyTransferClient.TransferResponse.Record(
                        "59", "20260914", "1", PLATFORM_ACCOUNT)));
    }

    // ------------------------------------------------------------------
    // 이중 송금 방지
    // ------------------------------------------------------------------

    /**
     * 개시가 멱등이라 같은 요청이 두 번 오면 기존 행이 돌아옵니다. 그걸 구분하지 않고
     * 송금하면 원장은 한 건인데 돈이 두 번 나갑니다.
     */
    @Test
    @DisplayName("이미 존재하던 환불이면 송금하지 않는다 - 이중 지급 차단")
    void doesNotTransferWhenRefundAlreadyExisted() {
        Payment payment = payment("CARD");
        Refund existing = refund(RefundMethod.ACCOUNT_TRANSFER);
        OpenResult opened = new OpenResult(existing, false);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);

        Refund result = service.refundParticipation(
                PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        assertEquals(existing, result);
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(kakaoPort, never()).refund(any());
    }

    // ------------------------------------------------------------------
    // 계좌이체 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("카드 결제는 계좌이체로 환불한다 - 카드 취소는 카드 재입력이 필요하다")
    void cardPaymentRefundsByAccountTransfer() {
        givenTransferable();
        SsafyTransferClient.TransferResponse response = transferResponse("H0000");
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(response);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(transferClient).transfer(INSTITUTION_TX_NO, "00100", "001", USER_KEY,
                PLATFORM_ACCOUNT, BUYER_ACCOUNT, AMOUNT, "공구한 환불 출금", "공구한 환불 입금");
        verify(ledgerService).completeByAccountTransfer(REFUND_ID, INSTITUTION_TX_NO, 59L);
    }

    /**
     * 목적지를 송금 직전이 아니라 <b>송금 전에</b> 원장에 남겨야, 송금 후 장애가 나도
     * 어디로 보냈는지 추적할 수 있습니다.
     */
    @Test
    @DisplayName("송금 전에 수취 계좌를 원장에 기록한다")
    void recordsAccountBeforeTransfer() {
        givenTransferable();
        SsafyTransferClient.TransferResponse response = transferResponse("H0000");
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(response);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).assignAccount(REFUND_ID, BUYER_ACCOUNT);
    }

    @Test
    @DisplayName("금융망이 실패 코드를 주면 실패로 남기고 완료시키지 않는다")
    void failsOnNonSuccessResponseCode() {
        givenTransferable();
        SsafyTransferClient.TransferResponse response = transferResponse("A1003");
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(response);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).fail(eq(REFUND_ID), anyString());
        verify(ledgerService, never()).completeByAccountTransfer(anyLong(), anyString(), any());
    }

    /**
     * 호출 성공·실패와 무관하게 {@code ssafy_api_logs} 에 남아야 금융망 거래내역과
     * 대조할 수 있습니다.
     */
    @Test
    @DisplayName("이체 호출은 성공·실패 모두 감사 로그에 남는다")
    void logsEveryTransferAttempt() {
        givenTransferable();
        SsafyTransferClient.TransferResponse response = transferResponse("A1003");
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(response);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ssafyApiLogService).record(eq(SsafyTransferClient.API_SERVICE_CODE),
                eq(INSTITUTION_TX_NO), eq("REFUND"), eq(REFUND_ID), any(),
                eq("A1003"), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 실행 전 차단 (조용히 실패하지 않기)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("구매자 환불 계좌가 없으면 송금하지 않고 사유를 남긴다")
    void failsWhenBuyerHasNoAccount() {
        Payment payment = payment("CARD");
        Refund refundRow = refund(RefundMethod.ACCOUNT_TRANSFER);
        User user = buyer(null, USER_KEY);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO))
                .thenReturn(PLATFORM_ACCOUNT);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(ledgerService).fail(eq(REFUND_ID), anyString());
    }

    @Test
    @DisplayName("플랫폼 출금 계좌가 설정되지 않았으면 송금하지 않는다")
    void failsWhenPlatformAccountMissing() {
        Payment payment = payment("CARD");
        Refund refundRow = refund(RefundMethod.ACCOUNT_TRANSFER);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO)).thenReturn("");

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
        verify(ledgerService).fail(eq(REFUND_ID), anyString());
    }

    /**
     * 응답을 받지 못하면 이체가 일어났는지 알 수 없습니다. 자동 재시도하면 이중 지급이
     * 될 수 있으므로 실패로 남겨 사람이 확인하게 합니다.
     */
    @Test
    @DisplayName("금융망 연동이 끊기면 실패로 남긴다 - 결과를 모르므로 자동 재시도하지 않는다")
    void failsWhenNetworkUnavailable() {
        givenTransferable();
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 연동이 일시적으로 불가능합니다."));

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).fail(eq(REFUND_ID), anyString());
        verify(ledgerService, never()).completeByAccountTransfer(anyLong(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 카카오페이 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("카카오페이는 제공사 취소 어댑터로 환불한다")
    void kakaoPaymentRefundsByPort() {
        Payment payment = payment("KAKAOPAY");
        Refund refundRow = refund(RefundMethod.KAKAOPAY);
        OpenResult opened = new OpenResult(refundRow, true);
        RefundPort.RefundResult success = RefundPort.RefundResult.succeeded("cancel-aid-1");

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(kakaoPort.supports("KAKAOPAY")).thenReturn(true);
        when(kakaoPort.refund(any())).thenReturn(success);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).completeByKakaoPay(REFUND_ID, "cancel-aid-1");
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("지원 어댑터가 없으면 실패로 남긴다")
    void failsWhenNoAdapterSupportsMethod() {
        Payment payment = payment("KAKAOPAY");
        Refund refundRow = refund(RefundMethod.KAKAOPAY);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(kakaoPort.supports("KAKAOPAY")).thenReturn(false);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).fail(eq(REFUND_ID), anyString());
    }

    // ------------------------------------------------------------------
    // 코인 결제
    // ------------------------------------------------------------------

    /**
     * 코인으로 낸 사람에게는 코인으로 돌려줍니다. 원화 계좌이체로 조용히 바꾸지
     * 않습니다 — 구매자가 기대한 것과 다른 통화를 받게 되기 때문입니다.
     */
    @Test
    @DisplayName("코인 결제는 코인으로 환불한다 - 원화 계좌이체로 바꾸지 않는다")
    void cryptoPaymentRefundsInCrypto() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                CRYPTO_TX_HASH, new java.math.BigDecimal("39.010002"),
                new java.math.BigDecimal("1384.26036"), BUYER_WALLET);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(true);
        when(cryptoRefundService.findBuyerWallet(PAYMENT_ID)).thenReturn(BUYER_WALLET);
        when(cryptoRefundService.refundByPayment(eq(PAYMENT_ID), anyLong())).thenReturn(transfer);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).completeByCrypto(eq(REFUND_ID), eq(CRYPTO_TX_HASH), any());
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    /**
     * 돈이 나갔으면 장부도 닫혀야 합니다. 이 호출이 없으면 에스크로가 보관 중으로 남아
     * 보관 총액 불변식(FR-ESC-08)이 깨지고, 참여는 '환불 중' 에서 멈춥니다.
     */
    @Test
    @DisplayName("송금이 끝나면 에스크로와 참여를 종결한다")
    void finalizesParticipationAfterSuccessfulRefund() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                CRYPTO_TX_HASH, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, BUYER_WALLET);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(true);
        when(cryptoRefundService.findBuyerWallet(PAYMENT_ID)).thenReturn(BUYER_WALLET);
        when(cryptoRefundService.refundByPayment(eq(PAYMENT_ID), anyLong())).thenReturn(transfer);
        Refund completed = completedParticipationRefund();
        when(ledgerService.completeByCrypto(anyLong(), anyString(), any())).thenReturn(completed);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.PARTICIPATION_CANCEL);

        verify(finalizationService).finalizeParticipationRefund(eq(PARTICIPATION_ID), anyString());
    }

    /**
     * 실패한 환불은 돈이 나가지 않았으므로 장부도 그대로 둡니다. 여기서 닫으면
     * 보관액이 실제보다 적어져 판매자가 받을 돈이 사라지고, 재처리(RFD-04) 대상도
     * 사라집니다.
     */
    @Test
    @DisplayName("환불이 실패하면 에스크로와 참여를 건드리지 않는다")
    void keepsStateWhenRefundFails() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(false);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.PARTICIPATION_CANCEL);

        verify(finalizationService, never()).finalizeParticipationRefund(anyLong(), anyString());
    }

    /**
     * 송금 직후 장애가 나도 어디로 보냈는지 원장에 남아야 체인에서 대조할 수 있습니다.
     */
    @Test
    @DisplayName("송금 전에 수취 지갑을 원장에 기록한다")
    void recordsWalletBeforeSending() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                CRYPTO_TX_HASH, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, BUYER_WALLET);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(true);
        when(cryptoRefundService.findBuyerWallet(PAYMENT_ID)).thenReturn(BUYER_WALLET);
        when(cryptoRefundService.refundByPayment(eq(PAYMENT_ID), anyLong())).thenReturn(transfer);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).assignWalletAddress(REFUND_ID, BUYER_WALLET);
    }

    /**
     * 송금이 이미 나갔을 수 있어 자동 재시도하면 두 번 지급됩니다. 재처리 전에 체인
     * 확인이 필요하다는 것을 사유에 남깁니다.
     */
    @Test
    @DisplayName("송금 결과를 모르면 확인이 필요하다는 사유로 실패시킨다")
    void marksUnknownOutcomeWithVerificationHint() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(true);
        when(cryptoRefundService.findBuyerWallet(PAYMENT_ID)).thenReturn(BUYER_WALLET);
        when(cryptoRefundService.refundByPayment(eq(PAYMENT_ID), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "송금 결과를 확인하지 못했습니다"));

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).fail(eq(REFUND_ID), contains("체인에서 확인"));
        verify(ledgerService, never()).completeByCrypto(anyLong(), anyString(), any());
    }

    /** 개인키가 없으면 송금 자체를 시도하지 않고 사유를 남깁니다. */
    @Test
    @DisplayName("환불 지갑 개인키가 없으면 송금하지 않는다")
    void failsWhenRefundWalletDisabled() {
        Payment payment = payment("CRYPTO");
        Refund refundRow = refund(RefundMethod.CRYPTO);
        OpenResult opened = new OpenResult(refundRow, true);

        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        when(cryptoRefundService.isEnabled()).thenReturn(false);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.RECRUIT_FAILED);

        verify(ledgerService).fail(eq(REFUND_ID), anyString());
        verify(cryptoRefundService, never()).refundByPayment(anyLong(), anyLong());
    }

    // ------------------------------------------------------------------
    // 기타
    // ------------------------------------------------------------------

    @Test
    @DisplayName("결제 건을 찾지 못하면 환불을 개시하지 않는다")
    void rejectsWhenPaymentMissing() {
        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () ->
                service.refundParticipation(PARTICIPATION_ID, AMOUNT,
                        RefundReason.RECRUIT_FAILED));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(ledgerService, never()).openForParticipation(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("알 수 없는 결제수단은 개시 전에 거부한다")
    void rejectsUnknownPaymentMethod() {
        Payment payment = payment("BITCOIN_CASH");
        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));

        assertThrows(BusinessException.class, () ->
                service.refundParticipation(PARTICIPATION_ID, AMOUNT,
                        RefundReason.RECRUIT_FAILED));

        verify(ledgerService, never()).openForParticipation(anyLong(), anyLong(), any(), any());
    }

    // ------------------------------------------------------------------
    // 카드 승인 취소 경로 (refundParticipationByCard, S15P21B205-108)
    // ------------------------------------------------------------------

    private static final String CARD_NUMBER = "1234567812345678";
    private static final String CVC = "123";

    /** 카드 취소용으로 CARD_CANCEL 원장을 개시한 상태를 세운다. */
    private Refund givenCardOpened() {
        Payment payment = payment("CARD");
        Refund refund = refund(RefundMethod.CARD_CANCEL);
        OpenResult opened = new OpenResult(refund, true);
        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));
        when(ledgerService.openForParticipation(eq(PARTICIPATION_ID), anyLong(), any(), any()))
                .thenReturn(opened);
        return refund;
    }

    @Test
    @DisplayName("카드 취소가 성공하면 카드 취소로 완료하고 이체하지 않는다")
    void cardCancelCompletesByCardCancel() {
        givenCardOpened();
        when(cardRefundPort.cancel(any()))
                .thenReturn(CardRefundPort.CardRefundResult.canceled(AMOUNT, "H0000", "정상"));

        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(ledgerService).completeByCardCancel(REFUND_ID, null);
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("카드 취소가 불가하면(부분·청구서 발행) 계좌이체로 폴백한다")
    void cardCancelFallsBackToTransfer() {
        givenCardOpened();
        when(cardRefundPort.cancel(any())).thenReturn(
                CardRefundPort.CardRefundResult.requiresTransferFallback("A1058", "청구서 발행"));
        // 폴백은 수단을 ACCOUNT_TRANSFER 로 바꾼 엔티티를 돌려주고, 이후 이체 경로가 이어진다.
        Refund switched = refund(RefundMethod.ACCOUNT_TRANSFER);
        User user = buyer(BUYER_ACCOUNT, USER_KEY);
        when(ledgerService.fallbackToAccountTransfer(REFUND_ID)).thenReturn(switched);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(policyService.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO)).thenReturn(PLATFORM_ACCOUNT);
        when(policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(transferResponse("H0000"));

        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(ledgerService).fallbackToAccountTransfer(REFUND_ID);
        verify(transferClient).transfer(INSTITUTION_TX_NO, "00100", "001", USER_KEY,
                PLATFORM_ACCOUNT, BUYER_ACCOUNT, AMOUNT, "공구한 환불 출금", "공구한 환불 입금");
        verify(ledgerService).completeByAccountTransfer(REFUND_ID, INSTITUTION_TX_NO, 59L);
    }

    @Test
    @DisplayName("카드 취소가 거절되면 실패로 남기고 이체하지 않는다")
    void cardCancelFailsMarksFailed() {
        givenCardOpened();
        when(cardRefundPort.cancel(any()))
                .thenReturn(CardRefundPort.CardRefundResult.failed("A1003", "승인 거절"));

        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(ledgerService).fail(eq(REFUND_ID), anyString());
        verify(ledgerService, never()).completeByCardCancel(anyLong(), any());
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("카드가 아닌 결제를 카드 취소로 부르면 개시 전에 거부한다")
    void cardCancelRejectsNonCardPayment() {
        Payment payment = payment("KAKAOPAY");
        when(paymentRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(payment));

        assertThrows(BusinessException.class, () ->
                service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                        RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC));

        verify(ledgerService, never()).openForParticipation(anyLong(), anyLong(), any(), any());
    }

    // ------------------------------------------------------------------
    // 환불 완료 후 종결 (에스크로 REFUNDED · 참여 REFUNDED)
    // ------------------------------------------------------------------

    /** 참여 환불 완료를 나타내는 원장 반환값. 종결 대상 판정에 쓰는 필드만 세운다. */
    private Refund completedParticipationRefund() {
        Refund refund = mock(Refund.class);
        when(refund.getRefundId()).thenReturn(REFUND_ID);
        when(refund.getStatus()).thenReturn(RefundStatus.COMPLETED);
        when(refund.getParticipationId()).thenReturn(PARTICIPATION_ID);
        when(refund.getReturnId()).thenReturn(null);
        return refund;
    }

    @Test
    @DisplayName("계좌이체 환불이 완료되면 에스크로/참여를 종결한다")
    void accountTransferCompletionFinalizes() {
        givenTransferable();
        Refund completed = completedParticipationRefund();
        when(transferClient.transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(transferResponse("H0000"));
        when(ledgerService.completeByAccountTransfer(anyLong(), anyString(), any()))
                .thenReturn(completed);

        service.refundParticipation(PARTICIPATION_ID, AMOUNT, RefundReason.PARTICIPATION_CANCEL);

        verify(finalizationService).finalizeParticipationRefund(eq(PARTICIPATION_ID), anyString());
    }

    @Test
    @DisplayName("카드 취소가 완료되면 에스크로/참여를 종결한다")
    void cardCancelCompletionFinalizes() {
        givenCardOpened();
        Refund completed = completedParticipationRefund();
        when(cardRefundPort.cancel(any()))
                .thenReturn(CardRefundPort.CardRefundResult.canceled(AMOUNT, "H0000", "정상"));
        when(ledgerService.completeByCardCancel(anyLong(), any())).thenReturn(completed);

        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(finalizationService).finalizeParticipationRefund(eq(PARTICIPATION_ID), anyString());
    }

    @Test
    @DisplayName("환불이 실패로 끝나면 에스크로/참여를 종결하지 않는다")
    void failedRefundDoesNotFinalize() {
        givenCardOpened();
        when(cardRefundPort.cancel(any()))
                .thenReturn(CardRefundPort.CardRefundResult.failed("A1003", "승인 거절"));

        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(finalizationService, never()).finalizeParticipationRefund(anyLong(), anyString());
    }

    @Test
    @DisplayName("종결이 실패해도 환불 자체는 되돌리지 않고 예외를 삼킨다")
    void finalizationFailureDoesNotBreakRefund() {
        givenCardOpened();
        Refund completed = completedParticipationRefund();
        when(cardRefundPort.cancel(any()))
                .thenReturn(CardRefundPort.CardRefundResult.canceled(AMOUNT, "H0000", "정상"));
        when(ledgerService.completeByCardCancel(anyLong(), any())).thenReturn(completed);
        // 에스크로가 이미 종료된 경우 등 - 종결이 예외를 던져도 호출자는 실패하지 않아야 한다.
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 종료됨"))
                .when(finalizationService).finalizeParticipationRefund(anyLong(), anyString());

        // 예외가 밖으로 새지 않는다.
        service.refundParticipationByCard(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, CARD_NUMBER, CVC);

        verify(finalizationService).finalizeParticipationRefund(eq(PARTICIPATION_ID), anyString());
    }
}
