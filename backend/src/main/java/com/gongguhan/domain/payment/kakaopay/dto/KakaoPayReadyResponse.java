package com.gongguhan.domain.payment.kakaopay.dto;

public record KakaoPayReadyResponse(
        String paymentId,
        String tid,
        String nextRedirectPcUrl,
        String expiresAt
) {
}
