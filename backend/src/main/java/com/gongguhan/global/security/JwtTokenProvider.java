package com.gongguhan.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final SecretKey key;
    private final long accessTokenExpireSeconds;
    private final long refreshTokenExpireSeconds;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpireSeconds = jwtProperties.accessTokenExpireSeconds();
        this.refreshTokenExpireSeconds = jwtProperties.refreshTokenExpireSeconds();
    }

    public String createAccessToken(Long userId, String role, boolean isSeller) {
        return createToken(userId, role, isSeller, ACCESS_TOKEN_TYPE, accessTokenExpireSeconds);
    }

    public String createRefreshToken(Long userId, String role, boolean isSeller) {
        return createToken(userId, role, isSeller, REFRESH_TOKEN_TYPE, refreshTokenExpireSeconds);
    }

    public long getAccessTokenExpireSeconds() {
        return accessTokenExpireSeconds;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidAccessToken(String token) {
        return isValidTokenType(token, ACCESS_TOKEN_TYPE);
    }

    public boolean isValidRefreshToken(String token) {
        return isValidTokenType(token, REFRESH_TOKEN_TYPE);
    }

    private boolean isValidTokenType(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private String createToken(
            Long userId, String role, boolean isSeller, String tokenType, long expireSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                // JWT 날짜 정밀도는 초 단위다. 같은 초에 발급해도 폐기 대상과 새 토큰을 구별한다.
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("isSeller", isSeller)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key)
                .compact();
    }
}
