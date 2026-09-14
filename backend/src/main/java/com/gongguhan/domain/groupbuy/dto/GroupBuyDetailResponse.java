package com.gongguhan.domain.groupbuy.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record GroupBuyDetailResponse(
        String id,
        String productId,
        String productName,
        String thumbnailUrl,
        String sellerName,
        String categoryId,
        String categoryName,
        String status,
        long listPrice,
        long productPrice,
        long unitShareAmount,
        long shippingFee,
        long personalPaymentAmount,
        int targetCount,
        int paidCount,
        double participationRate,
        OffsetDateTime recruitEndAt,
        OffsetDateTime createdAt,
        String description,
        List<String> images,
        String brand,
        String modelName,
        String color,
        long roundingSurplus,
        OffsetDateTime recruitStartAt,
        boolean withdrawalRestricted,
        String withdrawalRestrictionReason,
        boolean sellerSuspended
) {

    public GroupBuyDetailResponse withImages(List<String> productImages) {
        return new GroupBuyDetailResponse(
                id,
                productId,
                productName,
                thumbnailUrl,
                sellerName,
                categoryId,
                categoryName,
                status,
                listPrice,
                productPrice,
                unitShareAmount,
                shippingFee,
                personalPaymentAmount,
                targetCount,
                paidCount,
                participationRate,
                recruitEndAt,
                createdAt,
                description,
                List.copyOf(productImages),
                brand,
                modelName,
                color,
                roundingSurplus,
                recruitStartAt,
                withdrawalRestricted,
                withdrawalRestrictionReason,
                sellerSuspended);
    }
}
