package com.gongguhan.domain.payment.kakaopay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KakaoPayStateSignerTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private KakaoPayStateSigner signer;

    @BeforeEach
    void setUp() {
        KakaoPayProperties properties = new KakaoPayProperties(
                "http://localhost", "TC0ONETIME", "test-secret-key",
                "SECRET_KEY", "http://localhost:5173", 1_000);
        signer = new KakaoPayStateSigner(
                properties, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
    }

    @Test
    void issuedStateRoundTripsClaims() {
        LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.plusSeconds(300), ZoneOffset.UTC);
        KakaoPayStateSigner.StateClaims issued = signer.issue(9001L, 100L, expiresAt);

        KakaoPayStateSigner.StateClaims verified = signer.verify(signer.sign(issued));

        assertThat(verified.paymentId()).isEqualTo(9001L);
        assertThat(verified.userId()).isEqualTo(100L);
        assertThat(verified.expiresAtEpochSecond()).isEqualTo(expiresAt.toEpochSecond(ZoneOffset.UTC));
        assertThat(verified.nonce()).isNotBlank();
    }

    @Test
    void rejectsTamperedState() {
        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                9001L, 100L, NOW.plusSeconds(300).getEpochSecond(), "fixed-nonce");
        String token = signer.sign(claims);
        String tampered = token.substring(0, token.length() - 2) + "AA";

        BusinessException error = assertThrows(BusinessException.class,
                () -> signer.verify(tampered));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void rejectsExpiredState() {
        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                9001L, 100L, NOW.getEpochSecond(), "fixed-nonce");

        BusinessException error = assertThrows(BusinessException.class,
                () -> signer.verify(signer.sign(claims)));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_SLOT_EXPIRED);
    }
}
