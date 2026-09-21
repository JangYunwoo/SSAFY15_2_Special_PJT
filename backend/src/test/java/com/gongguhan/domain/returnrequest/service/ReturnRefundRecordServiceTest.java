package com.gongguhan.domain.returnrequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.RefundContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ReturnRefundRecordServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 5, 0);
    private ReturnRequestRepository repository;
    private ReturnRefundRecordService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ReturnRequestRepository.class);
        service = new ReturnRefundRecordService(repository,
                Clock.fixed(Instant.parse("2026-09-11T05:00:00Z"), ZoneOffset.UTC), Mockito.mock(com.gongguhan.domain.refund.service.RefundLedgerService.class));
    }

    @Test
    void sellerPickupCreatesOneIdempotentRefundCommandAndHistories() {
        RefundContext context = context("APPROVED", NOW.plusDays(7));
        when(repository.findSellerRefundContext(20L, 40L)).thenReturn(Optional.of(context));
        when(repository.markRefundRequested(40L, false, NOW)).thenReturn(true);

        var result = service.prepareSeller(20L, 40L);

        assertThat(result.shouldInvoke()).isTrue();
        assertThat(result.command().idempotencyKey()).isEqualTo("RETURN-40");
        assertThat(result.command().paymentMethod()).isEqualTo("KAKAOPAY");
        verify(repository).addHistory(40L, "PICKED_UP",
                "판매자가 수거 완료를 확인했습니다.", NOW);
        verify(repository).addHistory(40L, "REFUND_REQUESTED",
                "판매자 수거 완료 확인으로 환불을 요청했습니다.", NOW);
    }

    @Test
    void duplicateRefundRequestDoesNotInvokePortAgain() {
        when(repository.findSellerRefundContext(20L, 40L))
                .thenReturn(Optional.of(context("REFUND_REQUESTED", NOW.minusDays(1))));

        assertThat(service.prepareSeller(20L, 40L).shouldInvoke()).isFalse();
        verify(repository, never()).markRefundRequested(40L, false, NOW);
    }

    @Test
    void automaticRefundWaitsUntilSevenDayDeadline() {
        when(repository.findAutoRefundContext(40L))
                .thenReturn(Optional.of(context("APPROVED", NOW.plusSeconds(1))));

        assertThat(service.prepareAutomatic(40L).shouldInvoke()).isFalse();
        verify(repository, never()).markRefundRequested(40L, true, NOW);
    }

    @Test
    void completionAndFailureAreConditionalAndWriteHistory() {
        when(repository.markRefunded(40L, NOW)).thenReturn(true);
        when(repository.markRefundFailed(41L)).thenReturn(true);

        service.complete(40L, "cancel-aid");
        service.fail(41L, "provider rejected");

        verify(repository).addHistory(40L, "REFUNDED", "환불 완료: cancel-aid", NOW);
        verify(repository).addHistory(41L, "REFUND_FAILED", "provider rejected", NOW);
    }

    private RefundContext context(String status, LocalDateTime dueAt) {
        return new RefundContext(40L, 30L, 20L, 50L,
                "KAKAOPAY", 44_000, status, dueAt);
    }
}
