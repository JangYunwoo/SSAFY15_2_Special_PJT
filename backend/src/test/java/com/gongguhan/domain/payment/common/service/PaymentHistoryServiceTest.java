package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.repository.RefundRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

class PaymentHistoryServiceTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 21, 0, 0);
    private Refund refund(long participation, long amount) {
        return Refund.forParticipation(participation, amount, RefundReason.PARTICIPATION_CANCEL, RefundMethod.KAKAOPAY, now);
    }
    private PaymentResponse payment(String paymentId, String participation) {
        return new PaymentResponse(paymentId, "10", "PAID", "KAKAOPAY",
                new PaymentResponse.AmountResponse(1000, 0, 1000, "KRW"), "2026-09-21T01:00:00Z", 0, participation, null);
    }

    @Test void returnsApprovalAndRefundTogetherUsingSingleBatch() {
        var paymentService = mock(PaymentService.class);
        var repository = mock(RefundRepository.class);
        var page = PageRequest.of(0, 20);
        when(paymentService.listHistory(1L, page)).thenReturn(new PageImpl<>(List.of(payment("1", "2"), payment("3", "4")), page, 2));
        Refund completed = refund(2, 700);
        completed.completeByKakaoPay("test-result", now);
        when(repository.findByParticipationIdIn(Set.of(2L, 4L))).thenReturn(List.of(completed));
        var result = new PaymentHistoryService(paymentService, repository).listHistory(1L, page);
        assertEquals("PAID", result.getContent().getFirst().status());
        assertEquals(700, result.getContent().getFirst().refund().completedAmount());
        assertEquals("COMPLETED", result.getContent().getFirst().refund().status());
        assertEquals("NONE", result.getContent().get(1).refund().status());
        verify(repository, times(1)).findByParticipationIdIn(Set.of(2L, 4L));
        var json = JsonMapper.builder().build().readTree(JsonMapper.builder().build().writeValueAsString(result.getContent().getFirst()));
        assertEquals("1", json.get("paymentId").asText());
        assertEquals(700, json.get("refund").get("completedAmount").asLong());
    }
    @Test void noParticipationSkipsRefundQuery() {
        var paymentService = mock(PaymentService.class);
        var repository = mock(RefundRepository.class);
        var page = PageRequest.of(0, 20);
        when(paymentService.listHistory(1L, page)).thenReturn(new PageImpl<>(List.of(payment("1", null))));
        assertEquals("NONE", new PaymentHistoryService(paymentService, repository).listHistory(1L, page).getContent().getFirst().refund().status());
        verifyNoInteractions(repository);
    }
    @Test void unknownExecutionDoesNotCountAsCompleted() {
        Refund unknown = refund(2, 1000);
        unknown.claimExecution("test-correlation");
        unknown.markFailed("response lost", now);
        var result = PaymentHistoryService.summarize(List.of(unknown));
        assertEquals("UNKNOWN", result.status());
        assertEquals(0, result.completedAmount());
    }
    @Test void claimedProcessingRemainsProcessing() {
        Refund active = refund(2, 1000);
        active.claimExecution("test-correlation");
        assertEquals("PROCESSING", PaymentHistoryService.summarize(List.of(active)).status());
    }
    @Test void failedAttemptsAreNotAddedToCompletedAmount() {
        Refund failed = refund(2, 1000);
        failed.markFailed("invalid request", now);
        Refund completed = refund(2, 700);
        completed.completeByKakaoPay("test-result", now);
        var result = PaymentHistoryService.summarize(List.of(failed, completed));
        assertEquals("COMPLETED", result.status());
        assertEquals(700, result.completedAmount());
    }
    @Test void unresolvedFailureRemainsFailed() {
        Refund failed = refund(2, 1000);
        failed.markFailed("invalid request", now);
        assertEquals("FAILED", PaymentHistoryService.summarize(List.of(failed)).status());
    }
    @Test void emptyPageHasNoRefunds() {
        assertEquals("NONE", PaymentHistoryService.summarize(List.of()).status());
    }
    @Test void completedZeroAmountLedgerRemainsCompleted() {
        Refund completed = mock(Refund.class);
        when(completed.isCompleted()).thenReturn(true);
        when(completed.getAmount()).thenReturn(0L);
        var summary = PaymentHistoryService.summarize(List.of(completed));
        assertEquals("COMPLETED", summary.status());
        assertEquals(0, summary.completedAmount());
    }
}
