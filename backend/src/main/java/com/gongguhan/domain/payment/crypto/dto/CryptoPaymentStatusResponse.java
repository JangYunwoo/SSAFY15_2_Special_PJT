package com.gongguhan.domain.payment.crypto.dto;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import java.time.ZoneOffset;

/**
 * CRYP-04 입금·컨펌·매칭 상태 응답.
 *
 * <p>프론트가 이 값으로 진행률을 보여준다(FR-PAY-25). 컨펌은 몇 초 단위로 올라가므로
 * {@code confirmations / requiredConfirmations}를 그대로 노출한다.
 *
 * <p>{@code phase}는 프론트가 분기하기 쉽게 서버가 계산해서 내려주는 요약 상태다.
 * 결제 상태·영수증 상태·컨펌 수 세 가지를 프론트가 조합하게 하면 화면마다 판정이
 * 갈린다.
 */
public record CryptoPaymentStatusResponse(
        String paymentId,
        String phase,
        String paymentStatus,
        String txHash,
        String receiptStatus,
        Long blockNumber,
        int confirmations,
        int requiredConfirmations,
        String assetAmount,
        String rawAmount,
        String depositAddress,
        String confirmedAt,
        String failReason
) {

    /** 트랜잭션 미제출 — 아직 송금하지 않았거나 해시를 보내지 않았다. */
    public static final String PHASE_AWAITING_TX = "AWAITING_TX";
    /** 해시는 받았으나 노드가 아직 트랜잭션을 모른다. */
    public static final String PHASE_AWAITING_RECEIPT = "AWAITING_RECEIPT";
    /** 영수증 확인됨. 컨펌 대기 중. */
    public static final String PHASE_CONFIRMING = "CONFIRMING";
    /** 컨펌 충족 + 결제 확정 완료. */
    public static final String PHASE_COMPLETED = "COMPLETED";
    /** revert·금액 불일치·슬롯 만료 등으로 종료. */
    public static final String PHASE_FAILED = "FAILED";

    public static CryptoPaymentStatusResponse of(Payment payment, CryptoPayment cryptoPayment) {
        String phase = resolvePhase(payment, cryptoPayment);

        if (cryptoPayment == null) {
            return new CryptoPaymentStatusResponse(
                    String.valueOf(payment.getPaymentId()),
                    phase,
                    payment.getStatus(),
                    null, null, null, 0, 0, null, null, null, null,
                    payment.getFailReason());
        }

        return new CryptoPaymentStatusResponse(
                String.valueOf(payment.getPaymentId()),
                phase,
                payment.getStatus(),
                cryptoPayment.getTxHash(),
                cryptoPayment.getReceiptStatus(),
                cryptoPayment.getBlockNumber(),
                cryptoPayment.getConfirmations(),
                cryptoPayment.getRequiredConfirmations(),
                cryptoPayment.getAssetAmount().toPlainString(),
                cryptoPayment.getRawAmount(),
                cryptoPayment.getToAddress(),
                cryptoPayment.getConfirmedAt() == null
                        ? null
                        : cryptoPayment.getConfirmedAt().atOffset(ZoneOffset.UTC).toString(),
                payment.getFailReason());
    }

    private static String resolvePhase(Payment payment, CryptoPayment cryptoPayment) {
        if (Payment.STATUS_PAID.equals(payment.getStatus())) {
            return PHASE_COMPLETED;
        }
        if (!payment.isProcessing()) {
            // FAILED / EXPIRED / REFUNDED 는 모두 사용자 입장에서 "진행 불가"다.
            return PHASE_FAILED;
        }
        if (cryptoPayment == null || cryptoPayment.getTxHash() == null) {
            return PHASE_AWAITING_TX;
        }
        if (cryptoPayment.isReverted()) {
            return PHASE_FAILED;
        }
        if (cryptoPayment.getReceiptStatus() == null) {
            return PHASE_AWAITING_RECEIPT;
        }
        return PHASE_CONFIRMING;
    }
}
