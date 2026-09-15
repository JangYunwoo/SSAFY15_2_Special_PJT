package com.gongguhan.domain.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 관리자 로그인 요청(AUTH-05, {@code POST /auth/admins/login}). */
public record AdminLoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {
}
