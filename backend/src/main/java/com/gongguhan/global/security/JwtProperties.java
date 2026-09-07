package com.gongguhan.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpireSeconds,
        long refreshTokenExpireSeconds
) {
}
