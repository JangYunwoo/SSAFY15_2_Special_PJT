package com.gongguhan.domain.account.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        ActorResponse actor
) {
}
