package com.gongguhan.domain.account.dto;

public record ActorResponse(
        String id,
        String role,
        boolean isSeller,
        String name
) {
}
