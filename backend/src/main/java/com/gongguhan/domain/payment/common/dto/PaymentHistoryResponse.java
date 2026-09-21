package com.gongguhan.domain.payment.common.dto;

/** 원승인 상태는 보존하고 참여 환불 원장 요약을 함께 반환한다. */
public record PaymentHistoryResponse(
        String paymentId, String groupBuyId, String status, String method,
        PaymentResponse.AmountResponse amount, String slotExpiresAt, long remainingSeconds,
        String participationId, RefundSummary refund
) {
    public record RefundSummary(String status, long completedAmount) { }

    public static PaymentHistoryResponse from(PaymentResponse payment, RefundSummary refund) {
        return new PaymentHistoryResponse(payment.paymentId(), payment.groupBuyId(), payment.status(),
                payment.method(), payment.amount(), payment.slotExpiresAt(), payment.remainingSeconds(),
                payment.participationId(), refund);
    }
}
