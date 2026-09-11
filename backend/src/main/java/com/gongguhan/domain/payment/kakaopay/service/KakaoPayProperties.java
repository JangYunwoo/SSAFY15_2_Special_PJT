package com.gongguhan.domain.payment.kakaopay.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오페이 온라인 단건결제 연결 설정. 비밀키는 환경변수로만 주입한다. */
@ConfigurationProperties(prefix = "app.kakaopay")
public record KakaoPayProperties(
        String baseUrl,
        String cid,
        String secretKey,
        String authorizationScheme,
        String frontendBaseUrl,
        int timeoutMs
) {
    public String authorizationValue() {
        return authorizationScheme + " " + secretKey;
    }

    public boolean hasSecretKey() {
        return secretKey != null && !secretKey.isBlank();
    }
}
