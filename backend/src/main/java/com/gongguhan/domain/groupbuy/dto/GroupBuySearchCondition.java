package com.gongguhan.domain.groupbuy.dto;

public record GroupBuySearchCondition(
        String keyword,
        Long categoryId,
        String status,
        GroupBuySort sort
) {
}
