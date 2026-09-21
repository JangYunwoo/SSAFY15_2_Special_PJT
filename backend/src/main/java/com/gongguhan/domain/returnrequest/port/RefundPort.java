package com.gongguhan.domain.returnrequest.port;

/**
 * 반품 도메인이 결제수단별 취소 세부사항을 알지 않도록 하는 환불 경계.
 * 카드·카카오페이·코인 어댑터는 각자 소유 패키지에서 이 계약을 구현한다.
 */
public interface RefundPort {

    boolean supports(String paymentMethod);

    RefundResult refund(RefundCommand command);

    /**
     * @param returnId 반품 환불의 추적 ID. 일반 참여 취소·모집 실패 환불은 null이다.
     *                 결제수단 어댑터는 이를 모든 환불의 필수 조건으로 요구하지 않는다.
     */
    record RefundCommand(
            String idempotencyKey,
            Long returnId,
            Long paymentId,
            String paymentMethod,
            long amount
    ) {
    }

    record RefundResult(Status status, String providerRefundId, String failureReason) {

        public static RefundResult succeeded(String providerRefundId) {
            return new RefundResult(Status.SUCCEEDED, providerRefundId, null);
        }

        public static RefundResult failed(String failureReason) {
            return new RefundResult(Status.FAILED, null, failureReason);
        }

        public enum Status {
            SUCCEEDED,
            FAILED
        }
    }
}
