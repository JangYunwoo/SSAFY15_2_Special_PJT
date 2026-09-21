package com.gongguhan.domain.participation.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * PAR-02 참여 상세. FE ParticipationDetail 계약(ParticipationSummary 확장)과 맞추기 위해
 * 요약 필드를 평평하게 반복한다.
 */
public record ParticipationDetailResponse(
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
        boolean autoConfirmed,
        Long paymentId,
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2,
        String deliveryRequest,
        List<ParticipationHistoryEntry> history,
        boolean canCancel,
        boolean canConfirm,
        boolean canRequestReturn
) {
}
