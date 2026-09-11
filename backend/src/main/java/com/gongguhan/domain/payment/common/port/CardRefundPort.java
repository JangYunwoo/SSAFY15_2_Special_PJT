package com.gongguhan.domain.payment.common.port;

/**
 * 카드 결제 취소 포트 (S15P21B205-108). 반품·환불공통(S15P21B205-79, 109) 유스케이스가
 * 카드사 세부(SSAFY deleteTransaction 제약)를 모른 채 취소를 요청하는 계약.
 *
 * <p>SSAFY 카드 취소는 거래고유번호 단위 <b>전액 취소만</b> 가능하다(S15P21B205-34).
 * 부분 환불·청구서 발행 후 환불은 {@link CardRefundResult.Status#REQUIRES_TRANSFER_FALLBACK}로
 * 알리고, 계좌이체 폴백 여부는 호출자(환불 오케스트레이션)가 결정한다.
 */
public interface CardRefundPort {

    CardRefundResult cancel(CardRefundCommand command);

    /**
     * @param cardNumber 카드번호. 원문은 어디에도 저장되지 않으므로 매 취소 요청 시 다시 받는다.
     * @param requestedAmount 환불 요청 금액. 원결제 금액과 다르면(부분 환불) 카드 취소로 처리할 수 없다.
     */
    record CardRefundCommand(
            Long paymentId,
            String cardNumber,
            String cvc,
            long requestedAmount
    ) {
    }

    record CardRefundResult(
            Status status,
            long canceledAmount,
            String responseCode,
            String responseMessage
    ) {

        public enum Status {
            /** SSAFY 전액 취소 완료 (멱등 - 이미 취소된 건의 재요청도 이 값). */
            CANCELED,
            /** 카드 취소 불가(부분 환불·청구서 발행) - 계좌이체 환불로 전환해야 한다. */
            REQUIRES_TRANSFER_FALLBACK,
            /** 외부 승인 거절 등 실패. 재시도 가능. */
            FAILED
        }

        public static CardRefundResult canceled(long amount, String responseCode, String responseMessage) {
            return new CardRefundResult(Status.CANCELED, amount, responseCode, responseMessage);
        }

        public static CardRefundResult requiresTransferFallback(String responseCode, String responseMessage) {
            return new CardRefundResult(Status.REQUIRES_TRANSFER_FALLBACK, 0L, responseCode, responseMessage);
        }

        public static CardRefundResult failed(String responseCode, String responseMessage) {
            return new CardRefundResult(Status.FAILED, 0L, responseCode, responseMessage);
        }
    }
}
