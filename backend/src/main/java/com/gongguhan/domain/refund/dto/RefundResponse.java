package com.gongguhan.domain.refund.dto;

import com.gongguhan.domain.refund.repository.RefundQueryRepository.RefundRow;
import java.time.ZoneOffset;

/**
 * RFD-01~03 환불 응답 (API 명세서 11장).
 *
 * <p><b>계좌번호는 뒤 4자리만 내보냅니다.</b> 본인 조회라도 전체를 돌려줄 이유가 없고,
 * 응답이 로그나 브라우저 캐시에 남을 수 있습니다. 사용자는 "어느 계좌로 받았는지"만
 * 확인하면 되므로 마스킹으로 충분합니다.
 *
 * <p>코인 환불 필드({@code toAddress}, {@code assetAmount}, {@code txHash})는 마스킹하지
 * 않습니다. 온체인 데이터는 누구나 볼 수 있는 공개 정보이고, 구매자가 블록 탐색기에서
 * 직접 확인할 수 있어야 합니다.
 *
 * <p>{@code assetAmount} 는 문자열입니다. 소수 18자리를 JSON Number 로 보내면
 * 자바스크립트 {@code Number} 가 배정밀도라 값이 뭉개집니다(Quote 응답과 같은 이유).
 */
public record RefundResponse(
        String refundId,
        String participationId,
        String returnId,
        String productName,
        long amount,
        String reasonType,
        String refundMethod,
        String status,
        String toAccountNo,
        String toAddress,
        String assetAmount,
        String txHash,
        String institutionTxNo,
        String failReason,
        String createdAt,
        String processedAt
) {

    public static RefundResponse from(RefundRow row) {
        return new RefundResponse(
                String.valueOf(row.refundId()),
                row.participationId() == null ? null : String.valueOf(row.participationId()),
                row.returnId() == null ? null : String.valueOf(row.returnId()),
                row.productName(),
                row.amount(),
                row.reasonType(),
                row.refundMethod(),
                row.status(),
                mask(row.toAccountNo()),
                row.toAddress(),
                row.assetAmount() == null ? null : row.assetAmount().toPlainString(),
                row.txHash(),
                row.institutionTxNo(),
                row.failReason(),
                toIso(row.createdAt()),
                toIso(row.processedAt()));
    }

    private static String mask(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return null;
        }
        if (accountNo.length() <= 4) {
            return "****";
        }
        return "*".repeat(accountNo.length() - 4) + accountNo.substring(accountNo.length() - 4);
    }

    private static String toIso(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }
}
