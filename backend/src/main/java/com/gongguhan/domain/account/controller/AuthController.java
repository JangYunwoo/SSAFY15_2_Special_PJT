package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.LoginRequest;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.dto.SignupRequest;
import com.gongguhan.domain.account.dto.SignupResponse;
import com.gongguhan.domain.account.service.AuthService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        SignupResponse response = authService.signup(request);
        // 가입 트랜잭션 커밋 후 연동 - 실패해도 가입은 유지된다(후속 등록 방침, S15P21B205-63).
        authService.linkSsafyMemberIfMissing(Long.valueOf(response.id()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);
        // 가입 시점에 SSAFY 장애였던 계정을 로그인 때마다 재시도해 백필한다.
        authService.linkSsafyMemberIfMissing(Long.valueOf(response.actor().id()));
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
