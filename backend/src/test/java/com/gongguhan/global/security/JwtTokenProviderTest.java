package com.gongguhan.global.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-must-be-at-least-thirty-two-bytes-long",
                1800,
                1209600);
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void accessTokenAndRefreshTokenAreNotInterchangeable() {
        String accessToken = jwtTokenProvider.createAccessToken(1L, "USER", false);
        String refreshToken = jwtTokenProvider.createRefreshToken(1L, "USER", false);

        assertTrue(jwtTokenProvider.isValidAccessToken(accessToken));
        assertFalse(jwtTokenProvider.isValidAccessToken(refreshToken));
        assertTrue(jwtTokenProvider.isValidRefreshToken(refreshToken));
        assertFalse(jwtTokenProvider.isValidRefreshToken(accessToken));
    }

    @Test
    void everyIssuanceHasItsOwnIdentifierWithoutChangingTheActor() {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            var claims = jwtTokenProvider.parseClaims(jwtTokenProvider.createRefreshToken(1L, "USER", false));
            org.assertj.core.api.Assertions.assertThat(claims.getId()).isNotBlank();
            assertTrue(ids.add(claims.getId()));
            org.assertj.core.api.Assertions.assertThat(claims.getSubject()).isEqualTo("1");
            org.assertj.core.api.Assertions.assertThat(claims.get("role", String.class)).isEqualTo("USER");
        }
    }
}
