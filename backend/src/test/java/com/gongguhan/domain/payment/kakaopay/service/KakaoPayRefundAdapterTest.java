package com.gongguhan.domain.payment.kakaopay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayCancellation;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayCancellationRecordService.Preparation;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundCommand;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KakaoPayRefundAdapterTest {

    private KakaoPayCancellationRecordService recordService;
    private KakaoPayGateway gateway;
    private KakaoPayRefundAdapter adapter;
    private RefundCommand command;
    private KakaoPayCancellation cancellation;

    @BeforeEach
    void setUp() {
        recordService = mock(KakaoPayCancellationRecordService.class);
        gateway = mock(KakaoPayGateway.class);
        adapter = new KakaoPayRefundAdapter(recordService, gateway);
        command = new RefundCommand("RETURN-40", 40L, 50L, "KAKAOPAY", 44_000);
        cancellation = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000,
                LocalDateTime.of(2026, 9, 11, 5, 0));
    }

    @Test
    void successCallsProviderAndPersistsVerifiedResult() {
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.INVOKE));
        KakaoPayGateway.CancelResult result = new KakaoPayGateway.CancelResult(
                "A-CANCEL", "T123", "PART_CANCEL_PAYMENT", 44_000, 44_000, 10_000);
        when(gateway.cancel(any())).thenReturn(result);

        var response = adapter.refund(command);

        assertThat(response.providerRefundId()).isEqualTo("A-CANCEL");
        verify(recordService).succeed("RETURN-40", result);
    }

    @Test
    void duplicateSuccessReturnsCachedAidWithoutProviderCall() {
        cancellation.markSucceeded("A-CANCEL", 44_000, 44_000, 10_000,
                LocalDateTime.of(2026, 9, 11, 5, 0, 1));
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.CACHED_SUCCESS));

        var response = adapter.refund(command);

        assertThat(response.providerRefundId()).isEqualTo("A-CANCEL");
        verify(gateway, never()).cancel(any());
    }

    @Test
    void timeoutIsMarkedUnknownAndPropagated() {
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.INVOKE));
        BusinessException timeout = new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE);
        when(gateway.cancel(any())).thenThrow(timeout);

        assertThatThrownBy(() -> adapter.refund(command)).isSameAs(timeout);
        verify(recordService).fail("RETURN-40", timeout.getMessage(), true);
    }

    @Test
    void providerRejectionIsFailedAndCanBeRetriedSafely() {
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.INVOKE));
        BusinessException rejected = new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR);
        when(gateway.cancel(any())).thenThrow(rejected);

        assertThatThrownBy(() -> adapter.refund(command)).isSameAs(rejected);
        verify(recordService).fail("RETURN-40", rejected.getMessage(), false);
    }

    @Test
    void uncertainDuplicateNeverCallsProviderAgain() {
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.OUTCOME_UNKNOWN));

        var response = adapter.refund(command);

        assertThat(response.status().name()).isEqualTo("FAILED");
        assertThat(response.failureReason()).contains("결과를 확인");
        verify(gateway, never()).cancel(any());
    }

    @Test
    void providerSuccessWithInvalidResponseIsMarkedUnknownBeforePropagation() {
        when(recordService.prepare(command)).thenReturn(new Preparation(
                cancellation, Preparation.Outcome.INVOKE));
        KakaoPayGateway.CancelResult result = new KakaoPayGateway.CancelResult(
                "A-CANCEL", "T123", "CANCEL_PAYMENT", 44_000, 44_000, 0);
        when(gateway.cancel(any())).thenReturn(result);
        BusinessException mismatch = new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR);
        org.mockito.Mockito.doThrow(mismatch).when(recordService).succeed("RETURN-40", result);

        assertThatThrownBy(() -> adapter.refund(command)).isSameAs(mismatch);
        verify(recordService).fail("RETURN-40", mismatch.getMessage(), true);
    }
}
