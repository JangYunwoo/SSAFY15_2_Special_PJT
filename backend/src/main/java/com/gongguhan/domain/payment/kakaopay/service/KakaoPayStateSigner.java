package com.gongguhan.domain.payment.kakaopay.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 결제 리다이렉트 변조와 재사용을 막는 HMAC 서명 상태 토큰. */
@Component
public class KakaoPayStateSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final KakaoPayProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public KakaoPayStateSigner(KakaoPayProperties properties, Clock clock) {
        this(properties, clock, new SecureRandom());
    }

    KakaoPayStateSigner(KakaoPayProperties properties, Clock clock, SecureRandom secureRandom) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public StateClaims issue(Long paymentId, Long userId, LocalDateTime expiresAt) {
        byte[] nonceBytes = new byte[18];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        return new StateClaims(paymentId, userId, expiresAt.toEpochSecond(ZoneOffset.UTC), nonce);
    }

    public String sign(StateClaims claims) {
        ensureConfigured();
        String payload = claims.paymentId() + ":" + claims.userId() + ":"
                + claims.expiresAtEpochSecond() + ":" + claims.nonce();
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(encodedPayload));
        return encodedPayload + "." + encodedSignature;
    }

    public StateClaims verify(String token) {
        ensureConfigured();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                throw invalidState();
            }
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[1]);
            if (!MessageDigest.isEqual(hmac(parts[0]), actualSignature)) {
                throw invalidState();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", -1);
            if (values.length != 4 || values[3].isBlank()) {
                throw invalidState();
            }
            StateClaims claims = new StateClaims(
                    Long.valueOf(values[0]), Long.valueOf(values[1]),
                    Long.parseLong(values[2]), values[3]);
            if (!Instant.now(clock).isBefore(Instant.ofEpochSecond(claims.expiresAtEpochSecond()))) {
                throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
            }
            return claims;
        } catch (IllegalArgumentException e) {
            throw invalidState();
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("카카오페이 state 서명을 초기화할 수 없습니다.", e);
        }
    }

    private void ensureConfigured() {
        if (!properties.hasSecretKey()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "카카오페이 개발용 비밀키가 설정되지 않았습니다.");
        }
    }

    private BusinessException invalidState() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR,
                "카카오페이 승인 정보가 유효하지 않습니다.");
    }

    public record StateClaims(
            Long paymentId,
            Long userId,
            long expiresAtEpochSecond,
            String nonce
    ) {
    }
}
