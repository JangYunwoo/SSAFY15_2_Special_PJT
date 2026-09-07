package com.gongguhan.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세서 1.3~1.4의 공통 응답 포맷.
 * 성공 시 data/meta만, 실패 시 error만 채워지고 나머지는 직렬화에서 생략된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        PageResponse meta,
        ApiError error,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(true, data, null, null, traceId);
    }

    public static <T> ApiResponse<T> success(T data, PageResponse meta, String traceId) {
        return new ApiResponse<>(true, data, meta, null, traceId);
    }

    public static <T> ApiResponse<T> error(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, null, error, traceId);
    }
}
