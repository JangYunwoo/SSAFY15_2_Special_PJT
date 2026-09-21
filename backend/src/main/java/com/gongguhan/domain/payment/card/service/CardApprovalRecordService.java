package com.gongguhan.domain.payment.card.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable single execution claim. Never holds a DB transaction across a bank request. */
@Service
public class CardApprovalRecordService {
    private final PaymentRepository payments;
    private final Clock clock;

    public CardApprovalRecordService(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    @Transactional
    public void claim(Long userId, Long paymentId, String institutionTxNo) {
        Payment payment = payments.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        if (!Payment.METHOD_CARD.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (!payment.isProcessing() || payment.getCardApprovalStatus() != null) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "이미 승인 중이거나 처리된 결제입니다. 결과 불명 건은 거래내역 확인이 필요합니다.");
        }
        if (payment.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }
        payment.beginCardApproval(institutionTxNo);
    }

    @Transactional
    public void recordOutcome(Long paymentId, String outcome) {
        Payment payment = payments.findForUpdateByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // A post-commit error must not turn an already completed payment into UNKNOWN.
        if (payment.isCardApprovalInFlight()) { payment.recordCardApprovalOutcome(outcome); }
    }
}
