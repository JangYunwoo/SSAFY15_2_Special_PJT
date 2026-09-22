package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 승인 후 공구 종료로 고립된 결제의 환불을 <b>예약</b>한다.
 *
 * <p>{@link com.gongguhan.domain.refund.service.FailedRecruitmentRefundRecordService} 와 같은 패턴이다.
 * 결제 행을 잠그고 재검증한 뒤 환불 원장을 개시하고 결제의 재조정 상태를 {@code REFUND_RESERVED} 로
 * 전이하는 짧은 트랜잭션만 담당한다. <b>실제 송금은 하지 않는다</b> — 그건 오케스트레이션이
 * 트랜잭션 밖에서 실행한다.
 *
 * <p>카드는 카드 재입력이 필요해 자동 환불이 카드 취소를 부를 수 없으므로 계좌이체로 환불한다
 * (API 명세 11장 허용). 카카오페이는 원거래 취소 어댑터를 쓴다.
 */
@Service
public class StrandedPaymentRefundRecordService {

    private final PaymentRepository payments;
    private final RefundLedgerService ledger;

    public StrandedPaymentRefundRecordService(PaymentRepository payments, RefundLedgerService ledger) {
        this.payments = payments;
        this.ledger = ledger;
    }

    /**
     * 고립 결제의 환불 원장을 개시하고 예약 상태로 전이한다.
     *
     * @return 새로 개시한 환불 ID. 이미 환불이 있거나 대상이 아니면 {@code null}.
     */
    @Transactional
    public Long reserve(Long paymentId) {
        Payment payment = payments.findForUpdateByPaymentId(paymentId).orElse(null);
        if (payment == null || !payment.isRefundReconcileRequired()) {
            return null;
        }
        RefundMethod method = switch (payment.getMethod()) {
            case Payment.METHOD_CARD -> RefundMethod.ACCOUNT_TRANSFER;
            case Payment.METHOD_KAKAOPAY -> RefundMethod.KAKAOPAY;
            default -> throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "재조정 대상이 아닌 결제수단입니다. method=" + payment.getMethod());
        };
        RefundLedgerService.OpenResult opened = ledger.openForPayment(
                paymentId, payment.getKrwAmount(), RefundReason.GROUP_BUY_TERMINATED, method);
        payment.markRefundReserved();
        return opened.created() ? opened.refund().getRefundId() : null;
    }
}
