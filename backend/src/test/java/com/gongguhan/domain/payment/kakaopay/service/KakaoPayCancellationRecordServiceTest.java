package com.gongguhan.domain.payment.kakaopay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayCancellation;
import com.gongguhan.domain.payment.kakaopay.entity.KakaoPayPayment;
import com.gongguhan.domain.payment.kakaopay.repository.KakaoPayCancellationRepository;
import com.gongguhan.domain.payment.kakaopay.repository.KakaoPayPaymentRepository;
import com.gongguhan.domain.returnrequest.port.RefundPort.RefundCommand;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KakaoPayCancellationRecordServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 5, 0);
    private KakaoPayCancellationRepository cancellationRepository;
    private KakaoPayPaymentRepository paymentRepository;
    private KakaoPayCancellationRecordService service;

    @BeforeEach
    void setUp() {
        cancellationRepository = mock(KakaoPayCancellationRepository.class);
        paymentRepository = mock(KakaoPayPaymentRepository.class);
        service = new KakaoPayCancellationRecordService(
                cancellationRepository, paymentRepository,
                Clock.fixed(Instant.parse("2026-09-11T05:00:00Z"), ZoneOffset.UTC));
        when(paymentRepository.findById(50L)).thenReturn(Optional.of(approvedPayment()));
        when(cancellationRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newRequestPersistsPendingBeforeProviderCall() {
        when(cancellationRepository.findForUpdate("RETURN-40")).thenReturn(Optional.empty());

        var preparation = service.prepare(command());

        assertThat(preparation.outcome())
                .isEqualTo(KakaoPayCancellationRecordService.Preparation.Outcome.INVOKE);
        assertThat(preparation.cancellation().getTid()).isEqualTo("T123");
        assertThat(preparation.cancellation().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void successfulDuplicateReturnsCachedProviderAid() {
        KakaoPayCancellation existing = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000, NOW);
        existing.markSucceeded("A-CANCEL", 44_000, 44_000, 10_000, NOW.plusSeconds(1));
        when(cancellationRepository.findForUpdate("RETURN-40")).thenReturn(Optional.of(existing));

        var preparation = service.prepare(command());

        assertThat(preparation.outcome())
                .isEqualTo(KakaoPayCancellationRecordService.Preparation.Outcome.CACHED_SUCCESS);
        assertThat(preparation.cancellation().getCancelAid()).isEqualTo("A-CANCEL");
    }

    @Test
    void timeoutDuplicateDoesNotPermitAnotherProviderCall() {
        KakaoPayCancellation existing = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000, NOW);
        existing.markUnknown("timeout", NOW.plusSeconds(1));
        when(cancellationRepository.findForUpdate("RETURN-40")).thenReturn(Optional.of(existing));

        assertThat(service.prepare(command()).outcome())
                .isEqualTo(KakaoPayCancellationRecordService.Preparation.Outcome.OUTCOME_UNKNOWN);
    }

    @Test
    void cancellationCannotExceedApprovedPaymentAmount() {
        RefundCommand tooLarge = new RefundCommand(
                "RETURN-40", 40L, 50L, "KAKAOPAY", 54_001);

        assertThatThrownBy(() -> service.prepare(tooLarge))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    private RefundCommand command() {
        return new RefundCommand("RETURN-40", 40L, 50L, "KAKAOPAY", 44_000);
    }

    private KakaoPayPayment approvedPayment() {
        KakaoPayPayment payment = KakaoPayPayment.forPayment(50L);
        payment.recordReady("T123", "PAYMENT-50", "10", 54_000,
                "https://pay.example", "nonce", NOW.plusMinutes(5));
        payment.markApproved("A123", 4_909, "MONEY", NOW);
        return payment;
    }
}
