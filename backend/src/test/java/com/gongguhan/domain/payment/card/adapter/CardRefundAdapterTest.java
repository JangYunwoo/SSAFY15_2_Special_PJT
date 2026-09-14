package com.gongguhan.domain.payment.card.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.card.entity.CardPayment;
import com.gongguhan.domain.payment.card.repository.CardPaymentRepository;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient.SsafyCardTransactionResponse;
import com.gongguhan.domain.payment.common.port.CardRefundPort.CardRefundCommand;
import com.gongguhan.domain.payment.common.port.CardRefundPort.CardRefundResult;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardRefundAdapterTest {

    private static final Long PAYMENT_ID = 200L;
    private static final long PAID_AMOUNT = 54000L;
    private static final String CANCEL_TX_NO = "20260110000000000001";

    private CardPaymentRepository cardPaymentRepository;
    private SsafyCardApiClient ssafyCardApiClient;
    private SsafyApiLogService ssafyApiLogService;
    private PolicyService policyService;
    private CardRefundAdapter adapter;

    @BeforeEach
    void setUp() {
        cardPaymentRepository = mock(CardPaymentRepository.class);
        ssafyCardApiClient = mock(SsafyCardApiClient.class);
        ssafyApiLogService = mock(SsafyApiLogService.class);
        policyService = mock(PolicyService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC);
        adapter = new CardRefundAdapter(
                cardPaymentRepository, ssafyCardApiClient, ssafyApiLogService, policyService, clock);

        when(policyService.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policyService.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(policyService.getInt(PolicyKey.CARD_BILLING_CUTOFF_DAYS)).thenReturn(8);
        when(ssafyApiLogService.generateInstitutionTransactionUniqueNo(anyString()))
                .thenReturn(CANCEL_TX_NO);
    }

    /** 결제 다음 날 취소를 시도하는, 청구서 미발행 상태의 카드 결제. */
    private CardPayment recentCardPayment() {
        CardPayment cardPayment = CardPayment.create(
                PAYMENT_ID, "user-key", "************1234", 40861L, "공구한", "CG-9ca85f66311a23d",
                "20260109000000000009", 777L, "20260109", "120000", PAID_AMOUNT, null);
        when(cardPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(cardPayment));
        return cardPayment;
    }

    private CardRefundCommand fullCancelCommand() {
        return new CardRefundCommand(PAYMENT_ID, "1234567890123456", "123", PAID_AMOUNT);
    }

    private SsafyCardTransactionResponse response(String responseCode, String responseMessage) {
        return new SsafyCardTransactionResponse(
                new SsafyCardTransactionResponse.Header(responseCode, responseMessage), null);
    }

    @Test
    void cancelSucceedsMarksCanceledAndReturnsFullAmount() {
        CardPayment cardPayment = recentCardPayment();
        when(ssafyCardApiClient.deleteTransaction(
                eq(CANCEL_TX_NO), eq("00100"), eq("001"), eq("user-key"),
                eq("1234567890123456"), eq("123"), eq(777L)))
                .thenReturn(response("H0000", "정상처리"));

        CardRefundResult result = adapter.cancel(fullCancelCommand());

        assertEquals(CardRefundResult.Status.CANCELED, result.status());
        assertEquals(PAID_AMOUNT, result.canceledAmount());
        assertEquals(CardPayment.CANCEL_STATUS_SUCCESS, cardPayment.getCancelStatus());
        assertEquals(CANCEL_TX_NO, cardPayment.getCancelInstitutionTxNo());
        assertEquals(LocalDateTime.of(2026, 1, 10, 0, 0), cardPayment.getCanceledAt());
        verify(cardPaymentRepository).save(cardPayment);
        verify(ssafyApiLogService).record(
                eq("deleteTransaction"), eq(CANCEL_TX_NO), eq("PAYMENT"), eq(PAYMENT_ID),
                any(), eq("H0000"), eq("정상처리"), any());
    }

    @Test
    void cancelIsIdempotentForAlreadyCanceledPayment() {
        CardPayment cardPayment = recentCardPayment();
        cardPayment.markCanceled(CANCEL_TX_NO, LocalDateTime.of(2026, 1, 9, 0, 0));

        CardRefundResult result = adapter.cancel(fullCancelCommand());

        assertEquals(CardRefundResult.Status.CANCELED, result.status());
        assertEquals(PAID_AMOUNT, result.canceledAmount());
        verify(ssafyCardApiClient, never()).deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void partialAmountRequiresTransferFallbackWithoutExternalCall() {
        recentCardPayment();

        CardRefundResult result = adapter.cancel(
                new CardRefundCommand(PAYMENT_ID, "1234567890123456", "123", PAID_AMOUNT - 1000));

        assertEquals(CardRefundResult.Status.REQUIRES_TRANSFER_FALLBACK, result.status());
        verify(ssafyCardApiClient, never()).deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void billingCutoffPassedRequiresTransferFallbackWithoutExternalCall() {
        CardPayment oldPayment = CardPayment.create(
                PAYMENT_ID, "user-key", "************1234", 40861L, "공구한", "CG-9ca85f66311a23d",
                "20260101000000000009", 777L, "20260101", "120000", PAID_AMOUNT, null);
        when(cardPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(oldPayment));

        CardRefundResult result = adapter.cancel(fullCancelCommand());

        assertEquals(CardRefundResult.Status.REQUIRES_TRANSFER_FALLBACK, result.status());
        verify(ssafyCardApiClient, never()).deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void billingIssuedResponseMarksFlagAndRequiresTransferFallback() {
        CardPayment cardPayment = recentCardPayment();
        when(ssafyCardApiClient.deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(response("A1058", "청구서 발행 거래"));

        CardRefundResult result = adapter.cancel(fullCancelCommand());

        assertEquals(CardRefundResult.Status.REQUIRES_TRANSFER_FALLBACK, result.status());
        assertEquals(true, cardPayment.isBillingIssued());
        verify(cardPaymentRepository).save(cardPayment);
    }

    @Test
    void failureResponseMarksCancelFailedAndReturnsFailed() {
        CardPayment cardPayment = recentCardPayment();
        when(ssafyCardApiClient.deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(response("A1003", "거래 오류"));

        CardRefundResult result = adapter.cancel(fullCancelCommand());

        assertEquals(CardRefundResult.Status.FAILED, result.status());
        assertEquals("A1003", result.responseCode());
        assertEquals(CardPayment.CANCEL_STATUS_FAIL, cardPayment.getCancelStatus());
        verify(cardPaymentRepository).save(cardPayment);
    }

    @Test
    void timeoutPropagatesExternalServiceUnavailableWithoutStateChange() {
        CardPayment cardPayment = recentCardPayment();
        when(ssafyCardApiClient.deleteTransaction(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "SSAFY 금융망 연동에 실패했습니다."));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter.cancel(fullCancelCommand()));

        assertEquals(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertEquals(null, cardPayment.getCancelStatus());
        verify(cardPaymentRepository, never()).save(any());
    }
}
