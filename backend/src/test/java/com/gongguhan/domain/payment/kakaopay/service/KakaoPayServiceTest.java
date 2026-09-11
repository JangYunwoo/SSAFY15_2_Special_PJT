package com.gongguhan.domain.payment.kakaopay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayReadyResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KakaoPayServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long PAYMENT_ID = 9001L;
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.of(2026, 9, 11, 0, 5);

    private PaymentService paymentService;
    private KakaoPayRecordService recordService;
    private KakaoPayGateway gateway;
    private KakaoPayStateSigner signer;
    private KakaoPayService service;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        recordService = mock(KakaoPayRecordService.class);
        gateway = mock(KakaoPayGateway.class);
        KakaoPayProperties properties = new KakaoPayProperties(
                "https://open-api.kakaopay.com", "TC0ONETIME", "test-secret-key",
                "SECRET_KEY", "http://localhost:5173", 1_000);
        signer = new KakaoPayStateSigner(
                properties, Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC));
        service = new KakaoPayService(paymentService, recordService, gateway, signer, properties);
        when(paymentService.getApprovalContext(USER_ID, PAYMENT_ID, "KAKAOPAY"))
                .thenReturn(new PaymentService.ApprovalContext(
                        PAYMENT_ID, 701L, USER_ID, 54_000, EXPIRES_AT));
    }

    @Test
    void readyBuildsSignedCallbacksAndRecordsProviderResult() {
        when(gateway.ready(any())).thenReturn(
                new KakaoPayGateway.ReadyResult("T123", "https://pay.example/bridge"));

        KakaoPayReadyResponse response = service.ready(USER_ID, PAYMENT_ID);

        ArgumentCaptor<KakaoPayGateway.ReadyCommand> command =
                ArgumentCaptor.forClass(KakaoPayGateway.ReadyCommand.class);
        verify(gateway).ready(command.capture());
        assertThat(command.getValue().approvalUrl())
                .startsWith("http://localhost:5173/checkout/kakaopay/success")
                .contains("paymentId=9001")
                .contains("state=");
        assertThat(command.getValue().totalAmount()).isEqualTo(54_000);
        verify(recordService).recordReady(
                org.mockito.ArgumentMatchers.eq(PAYMENT_ID),
                org.mockito.ArgumentMatchers.eq("T123"),
                org.mockito.ArgumentMatchers.eq("PAYMENT-9001"),
                org.mockito.ArgumentMatchers.eq("100"),
                org.mockito.ArgumentMatchers.eq(54_000L),
                org.mockito.ArgumentMatchers.eq("https://pay.example/bridge"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(EXPIRES_AT));
        assertThat(response.paymentId()).isEqualTo("9001");
    }

    @Test
    void approveRejectsStateBelongingToAnotherUserBeforeProviderCall() {
        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                PAYMENT_ID, 999L, EXPIRES_AT.toEpochSecond(ZoneOffset.UTC), "nonce");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(USER_ID, new KakaoPayApproveRequest(
                        PAYMENT_ID, "pg-token", signer.sign(claims))));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_ACCESS_DENIED);
    }

    @Test
    void approveUsesStoredReadyValuesAndCommitsVerifiedResult() {
        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                PAYMENT_ID, USER_ID, EXPIRES_AT.toEpochSecond(ZoneOffset.UTC), "nonce");
        when(recordService.getApprovalRecord(PAYMENT_ID, "nonce"))
                .thenReturn(new KakaoPayRecordService.ApprovalRecord(
                        "T123", "PAYMENT-9001", "100", 54_000));
        KakaoPayGateway.ApproveResult approved = new KakaoPayGateway.ApproveResult(
                "A123", "T123", "PAYMENT-9001", "100", 54_000, 4_909, "MONEY");
        when(gateway.approve(any())).thenReturn(approved);
        PaymentResponse completed = new PaymentResponse(
                "9001", "701", "PAID", "KAKAOPAY",
                new PaymentResponse.AmountResponse(50_000, 4_000, 54_000, "KRW"),
                "2026-09-11T00:05:00Z", 0, "8001");
        when(recordService.complete(USER_ID, claims, approved)).thenReturn(completed);

        PaymentResponse response = service.approve(USER_ID, new KakaoPayApproveRequest(
                PAYMENT_ID, "pg-token", signer.sign(claims)));

        ArgumentCaptor<KakaoPayGateway.ApproveCommand> command =
                ArgumentCaptor.forClass(KakaoPayGateway.ApproveCommand.class);
        verify(gateway).approve(command.capture());
        assertThat(command.getValue().tid()).isEqualTo("T123");
        assertThat(command.getValue().pgToken()).isEqualTo("pg-token");
        assertThat(response.participationId()).isEqualTo("8001");
    }

    @Test
    void approveRetryReturnsCommittedPaymentWithoutCallingProviderAgain() {
        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                PAYMENT_ID, USER_ID, EXPIRES_AT.toEpochSecond(ZoneOffset.UTC), "nonce");
        PaymentResponse completed = new PaymentResponse(
                "9001", "701", "PAID", "KAKAOPAY",
                new PaymentResponse.AmountResponse(50_000, 4_000, 54_000, "KRW"),
                "2026-09-11T00:05:00Z", 0, "8001");
        when(recordService.isCompleted(PAYMENT_ID, "nonce")).thenReturn(true);
        when(paymentService.getCompletedPayment(USER_ID, PAYMENT_ID, "KAKAOPAY"))
                .thenReturn(completed);

        PaymentResponse response = service.approve(USER_ID, new KakaoPayApproveRequest(
                PAYMENT_ID, "same-pg-token", signer.sign(claims)));

        assertThat(response).isSameAs(completed);
        verify(gateway, never()).approve(any());
    }
}
