package com.gongguhan.domain.settlement.dto;

import com.gongguhan.domain.settlement.entity.Settlement;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * STL-01·03 정산 목록 항목 (API 명세서 12장).
 *
 * <p>금액 구성을 모두 내보냅니다. 지급액만 주면 판매자가 "왜 이 금액인지" 를 알 수
 * 없고, 수수료가 얼마 떼였는지 확인할 방법이 없습니다.
 *
 * <p>{@code transferStatus} 는 {@code status} 와 별개입니다 — 정산은 유효한데
 * 이체만 실패한 상태({@code PENDING} + {@code FAILED})가 재처리 대상입니다.
 */
public record SettlementResponse(
        String settlementId,
        String groupBuyId,
        String sellerId,
        int roundNo,
        String status,
        String transferStatus,
        int confirmedCount,
        long shareAmountTotal,
        long shippingFeeTotal,
        long roundingSurplus,
        BigDecimal platformFeeRate,
        long platformFee,
        long netAmount,
        String transferFailReason,
        String transferredAt,
        String createdAt
) {

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                String.valueOf(settlement.getSettlementId()),
                String.valueOf(settlement.getGroupBuyId()),
                String.valueOf(settlement.getSellerId()),
                settlement.getRoundNo(),
                settlement.getStatus(),
                settlement.getTransferStatus(),
                settlement.getConfirmedCount(),
                settlement.getShareTotal(),
                settlement.getShippingTotal(),
                settlement.getRoundingDiff(),
                settlement.getPlatformFeeRate(),
                settlement.getPlatformFee(),
                settlement.getSettlementAmount(),
                settlement.getTransferFailReason(),
                toIso(settlement.getTransferredAt()),
                toIso(settlement.getCreatedAt()));
    }

    private static String toIso(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }
}
