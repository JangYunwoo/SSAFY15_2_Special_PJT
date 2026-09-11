package com.gongguhan.domain.returnrequest.service;

import com.gongguhan.domain.returnrequest.port.RefundPort.RefundCommand;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.RefundContext;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 외부 환불 호출 전후의 짧은 DB 트랜잭션만 담당한다. */
@Service
public class ReturnRefundRecordService {

    private final ReturnRequestRepository repository;
    private final Clock clock;

    public ReturnRefundRecordService(ReturnRequestRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepareSeller(Long sellerId, Long returnId) {
        RefundContext context = repository.findSellerRefundContext(sellerId, returnId)
                .orElseThrow(this::notFound);
        return prepare(context, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepareAutomatic(Long returnId) {
        RefundContext context = repository.findAutoRefundContext(returnId)
                .orElseThrow(this::notFound);
        LocalDateTime current = now();
        if (context.autoRefundDueAt() == null || current.isBefore(context.autoRefundDueAt())) {
            return Preparation.skipped(context);
        }
        return prepare(context, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long returnId, String providerRefundId) {
        LocalDateTime completedAt = now();
        if (repository.markRefunded(returnId, completedAt)) {
            repository.addHistory(returnId, "REFUNDED",
                    providerRefundId == null ? "환불이 완료되었습니다."
                            : "환불 완료: " + providerRefundId,
                    completedAt);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long returnId, String reason) {
        LocalDateTime failedAt = now();
        if (repository.markRefundFailed(returnId)) {
            repository.addHistory(returnId, "REFUND_FAILED", safeReason(reason), failedAt);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findAutomaticCandidates(int limit) {
        return repository.findAutoRefundCandidates(now(), limit);
    }

    private Preparation prepare(RefundContext context, boolean automatic) {
        if ("REFUNDED".equals(context.status())
                || "REFUND_REQUESTED".equals(context.status())
                || "REFUND_FAILED".equals(context.status())) {
            return Preparation.skipped(context);
        }
        if (!"APPROVED".equals(context.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "승인된 반품만 환불을 시작할 수 있습니다.");
        }
        LocalDateTime requestedAt = now();
        if (!repository.markRefundRequested(context.returnId(), automatic, requestedAt)) {
            return Preparation.skipped(context);
        }
        String note = automatic
                ? "수거 확인 기한 7일 경과로 자동 환불을 요청했습니다."
                : "판매자 수거 완료 확인으로 환불을 요청했습니다.";
        if (!automatic) {
            repository.addHistory(context.returnId(), "PICKED_UP",
                    "판매자가 수거 완료를 확인했습니다.", requestedAt);
        }
        repository.addHistory(context.returnId(), "REFUND_REQUESTED", note, requestedAt);
        return Preparation.invoke(context, new RefundCommand(
                "RETURN-" + context.returnId(), context.returnId(), context.paymentId(),
                context.paymentMethod(), context.refundAmount()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "환불 처리에 실패했습니다.";
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "반품 정보를 찾을 수 없습니다.");
    }

    public record Preparation(RefundContext context, RefundCommand command, boolean shouldInvoke) {
        static Preparation invoke(RefundContext context, RefundCommand command) {
            return new Preparation(context, command, true);
        }

        static Preparation skipped(RefundContext context) {
            return new Preparation(context, null, false);
        }
    }
}
