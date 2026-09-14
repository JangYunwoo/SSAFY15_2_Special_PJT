package com.gongguhan.domain.returnrequest.event;

import java.time.LocalDateTime;

/** 판매자가 승인 후 7일 동안 수거를 확인하지 않아 자동 환불된 위반 사건. */
public record ReturnNeglectEvent(
        Long returnId,
        Long sellerId,
        Long participationId,
        LocalDateTime occurredAt,
        String violationType
) {
    public static final String TYPE = "RETURN_NEGLECT";
}
