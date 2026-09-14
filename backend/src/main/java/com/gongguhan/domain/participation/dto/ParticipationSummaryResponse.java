package com.gongguhan.domain.participation.dto;

import java.time.OffsetDateTime;

/**
 * PAR-01 내 참여 목록 행. 필드명은 FE 계약(frontend/src/entities/participation/model.ts의
 * ParticipationSummary)과 1:1로 맞춘다.
 */
public record ParticipationSummaryResponse(
        Long id,
        Long groupBuyId,
        String productName,
        String sellerName,
        String status,
        String groupBuyStatus,
        String escrowStatus,
        String method,
        long shareAmount,
        long shippingFee,
        long totalAmount,
        OffsetDateTime paidAt,
        int targetCount,
        int paidCount,
        OffsetDateTime recruitEndAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime autoConfirmAt,
        OffsetDateTime withdrawalDeadlineAt,
        OffsetDateTime confirmedAt,
        boolean autoConfirmed
) {
}
