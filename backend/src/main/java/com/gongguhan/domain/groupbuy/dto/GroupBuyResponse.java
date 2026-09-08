package com.gongguhan.domain.groupbuy.dto;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GroupBuyResponse(
        Long groupBuyId,
        Long productId,
        Long sellerId,
        long productPrice,
        int targetCount,
        long unitShareAmount,
        long roundingSurplus,
        long shippingFee,
        BigDecimal platformFeeRate,
        long expectedPlatformFee,
        long expectedSettlementAmount,
        LocalDateTime recruitStartAt,
        LocalDateTime recruitEndAt,
        int currentParticipants,
        int reservedSlots,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static GroupBuyResponse from(GroupBuy groupBuy) {
        return new GroupBuyResponse(
                groupBuy.getGroupBuyId(),
                groupBuy.getProduct().getProductId(),
                groupBuy.getSellerId(),
                groupBuy.getProductPrice(),
                groupBuy.getTargetParticipants(),
                groupBuy.getUnitShareAmount(),
                groupBuy.getRoundingDiff(),
                groupBuy.getShippingFee(),
                groupBuy.getPlatformFeeRate(),
                groupBuy.getExpectedPlatformFee(),
                groupBuy.getExpectedSettlementAmount(),
                groupBuy.getRecruitStartAt(),
                groupBuy.getRecruitEndAt(),
                groupBuy.getCurrentParticipants(),
                groupBuy.getReservedSlots(),
                groupBuy.getStatus(),
                groupBuy.getCreatedAt(),
                groupBuy.getUpdatedAt());
    }
}
