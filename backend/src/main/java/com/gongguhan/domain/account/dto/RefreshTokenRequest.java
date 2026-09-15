package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.NotBlank;

/** AUTH-03 재발급·AUTH-04 로그아웃 공통 요청. */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
