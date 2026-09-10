package com.gongguhan.domain.product.dto;

import com.gongguhan.domain.product.entity.Product;
import java.util.List;

public record ProductResponse(
        String productId,
        String sellerId,
        String categoryId,
        String categoryName,
        String name,
        String brand,
        String modelName,
        String color,
        String description,
        long price,
        boolean aiExtracted,
        boolean withdrawalRestricted,
        String withdrawalRestrictionReasonId,
        String withdrawalRestrictionReason,
        String restrictionEvidenceUrl,
        String restrictionReviewStatus,
        String status,
        List<ProductImageResponse> images
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getProductId() == null ? null : String.valueOf(product.getProductId()),
                String.valueOf(product.getSellerId()),
                String.valueOf(product.getCategory().getCategoryId()),
                product.getCategory().getName(),
                product.getName(),
                product.getBrand(),
                product.getModelName(),
                product.getColor(),
                product.getDescription(),
                product.getPrice(),
                product.isAiExtracted(),
                product.isWithdrawalRestricted(),
                product.getRestrictionReasonId() == null ? null : String.valueOf(product.getRestrictionReasonId()),
                product.getRestrictionReasonName(),
                product.getRestrictionEvidenceUrl(),
                product.getRestrictionReviewStatus().name(),
                product.getStatus(),
                product.getImages().stream().map(ProductImageResponse::from).toList());
    }
}
