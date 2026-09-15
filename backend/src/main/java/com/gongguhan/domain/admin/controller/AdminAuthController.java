package com.gongguhan.domain.admin.controller;

import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.admin.dto.AdminLoginRequest;
import com.gongguhan.domain.admin.service.AdminAuthService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 로그인 (AUTH-05). {@code /auth/admins/login}은 SecurityConfig에서 permitAll이다. */
@RestController
@RequestMapping("/auth/admins")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                adminAuthService.login(request), TraceIdUtils.from(httpRequest)));
    }
}
