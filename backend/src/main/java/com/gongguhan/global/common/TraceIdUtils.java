package com.gongguhan.global.common;

import jakarta.servlet.http.HttpServletRequest;

public final class TraceIdUtils {

    private TraceIdUtils() {
    }

    public static String from(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId != null ? traceId.toString() : null;
    }
}
