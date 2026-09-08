package com.gongguhan.domain.product.dto;

import com.gongguhan.domain.product.entity.ProductImage;

public record ProductImageResponse(
        String imageId,
        String imageUrl,
        int sortOrder,
        boolean thumbnail
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(
                image.getImageId() == null ? null : String.valueOf(image.getImageId()),
                image.getImageUrl(),
                image.getSortOrder(),
                image.isThumbnail());
    }
}
