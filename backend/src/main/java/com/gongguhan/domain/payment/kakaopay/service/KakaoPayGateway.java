package com.gongguhan.domain.payment.kakaopay.service;

/** 카카오페이 외부 API 경계. 서비스 테스트에서는 이 계약을 대역으로 교체한다. */
public interface KakaoPayGateway {

    ReadyResult ready(ReadyCommand command);

    ApproveResult approve(ApproveCommand command);

    CancelResult cancel(CancelCommand command);

    record ReadyCommand(
            String partnerOrderId,
            String partnerUserId,
            String itemName,
            long totalAmount,
            String approvalUrl,
            String cancelUrl,
            String failUrl
    ) {
    }

    record ReadyResult(String tid, String nextRedirectPcUrl) {
    }

    record ApproveCommand(
            String tid,
            String partnerOrderId,
            String partnerUserId,
            String pgToken
    ) {
    }

    record ApproveResult(
            String aid,
            String tid,
            String partnerOrderId,
            String partnerUserId,
            long totalAmount,
            long vatAmount,
            String paymentMethodType
    ) {
    }

    record CancelCommand(String tid, long cancelAmount, long cancelTaxFreeAmount) {
    }

    record CancelResult(
            String aid,
            String tid,
            String status,
            long approvedCancelAmount,
            long canceledAmount,
            long cancelAvailableAmount
    ) {
    }
}
