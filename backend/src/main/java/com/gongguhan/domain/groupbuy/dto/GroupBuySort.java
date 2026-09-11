package com.gongguhan.domain.groupbuy.dto;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.Arrays;

public enum GroupBuySort {
    POPULAR("popular"),
    DEADLINE("deadline"),
    NEWEST("newest"),
    OLDEST("oldest"),
    PRICE_ASC("priceAsc"),
    PRICE_DESC("priceDesc");

    private final String value;

    GroupBuySort(String value) {
        this.value = value;
    }

    public static GroupBuySort from(String value) {
        if (value == null || value.isBlank()) {
            return POPULAR;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 정렬 기준입니다."));
    }
}
