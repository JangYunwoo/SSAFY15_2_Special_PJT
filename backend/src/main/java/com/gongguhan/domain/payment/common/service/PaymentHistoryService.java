package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.payment.common.dto.PaymentHistoryResponse;
import com.gongguhan.domain.payment.common.dto.PaymentHistoryResponse.RefundSummary;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.repository.RefundRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentHistoryService {
    private final PaymentService paymentService;
    private final RefundRepository refundRepository;

    public PaymentHistoryService(PaymentService paymentService, RefundRepository refundRepository) {
        this.paymentService = paymentService;
        this.refundRepository = refundRepository;
    }

    public Page<PaymentHistoryResponse> listHistory(Long userId, Pageable pageable) {
        // 인증된 사용자의 결제 페이지에서만 참여 ID를 얻는다. 클라이언트 임의 ID를 받지 않는다.
        Page<PaymentResponse> payments = paymentService.listHistory(userId, pageable);
        Set<Long> ids = payments.stream().map(PaymentResponse::participationId)
                .filter(Objects::nonNull).map(Long::valueOf).collect(Collectors.toSet());
        Map<Long, List<Refund>> refunds = ids.isEmpty() ? Map.of()
                : refundRepository.findByParticipationIdIn(ids).stream()
                    .collect(Collectors.groupingBy(Refund::getParticipationId));
        return payments.map(payment -> PaymentHistoryResponse.from(payment, summarize(
                payment.participationId() == null ? List.of()
                        : refunds.getOrDefault(Long.valueOf(payment.participationId()), List.of()))));
    }

    static RefundSummary summarize(List<Refund> refunds) {
        long completed = refunds.stream().filter(Refund::isCompleted).mapToLong(Refund::getAmount).sum();
        // 실패처럼 보이더라도 외부 실행 결과가 불명확하면 완료/단순 실패로 단정하지 않는다.
        String status = refunds.stream().anyMatch(refund -> refund.isFailed() && refund.isExecutionClaimed()) ? "UNKNOWN"
                : refunds.stream().anyMatch(Refund::isProcessing) ? "PROCESSING"
                : refunds.stream().anyMatch(Refund::isCompleted) ? "COMPLETED"
                : refunds.stream().anyMatch(Refund::isFailed) ? "FAILED" : "NONE";
        return new RefundSummary(status, completed);
    }
}
