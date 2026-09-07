package com.gongguhan.global.common;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String reason) {
    }
}
