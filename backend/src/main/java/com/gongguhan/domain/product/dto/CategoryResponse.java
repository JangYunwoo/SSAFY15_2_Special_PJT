package com.gongguhan.domain.product.dto;

import java.util.List;

public record CategoryResponse(
        String categoryId,
        String name,
        int depth,
        List<CategoryResponse> children
) {
}
