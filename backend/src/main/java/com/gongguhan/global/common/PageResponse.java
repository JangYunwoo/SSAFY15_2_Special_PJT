package com.gongguhan.global.common;

import org.springframework.data.domain.Page;

public record PageResponse(
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static PageResponse from(Page<?> page) {
        return new PageResponse(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
