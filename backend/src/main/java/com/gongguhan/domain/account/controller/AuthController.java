package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.EmailAvailabilityRequest;
import com.gongguhan.domain.account.dto.EmailAvailabilityResponse;
import com.gongguhan.domain.account.dto.EmailAvailabilityStatus;
import com.gongguhan.domain.account.dto.LoginRequest;
import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.dto.RefreshTokenRequest;
import com.gongguhan.domain.account.dto.SignupRequest;
import com.gongguhan.domain.account.dto.SignupResponse;
import com.gongguhan.domain.account.service.AuthService;
import com.gongguhan.domain.account.service.EmailCheckRateLimiter;
import com.gongguhan.domain.account.service.TokenService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Validated
public class AuthController {

    private final AuthService authService;
    private final EmailCheckRateLimiter emailCheckRateLimiter;
    private final TokenService tokenService;

    public AuthController(AuthService authService, EmailCheckRateLimiter emailCheckRateLimiter,
                          TokenService tokenService) {
        this.authService = authService;
        this.emailCheckRateLimiter = emailCheckRateLimiter;
        this.tokenService = tokenService;
    }

    /** AUTH-03 Access Token 재발급(리프레시 토큰 로테이션). */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                tokenService.refresh(request.refreshToken()), TraceIdUtils.from(httpRequest)));
    }

    /** AUTH-04 로그아웃(리프레시 토큰 폐기). */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        tokenService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        // signup이 SSAFY 연동을 동기로 처리한다(선점 충돌이면 거절, 장애면 userKey 없이 가입).
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    /**
     * 이메일 사용 가능 여부 확인·예약(C안). DB 중복과 SSAFY 금융망 선점을 가입 전에 판별한다.
     *
     * <p>확인이 SSAFY 회원 생성(쓰기)을 유발할 수 있어 조회가 아닌 POST이며, 남용을 막기
     * 위해 호출 빈도를 제한한다.
     */
    @PostMapping("/email/availability")
    public ResponseEntity<ApiResponse<EmailAvailabilityResponse>> checkEmailAvailability(
            @Valid @RequestBody EmailAvailabilityRequest request, HttpServletRequest httpRequest) {
        emailCheckRateLimiter.check(clientKey(httpRequest));
        EmailAvailabilityStatus status = authService.checkEmailAvailability(request.email());
        return ResponseEntity.ok(ApiResponse.success(
                new EmailAvailabilityResponse(status.canProceed(), status), TraceIdUtils.from(httpRequest)));
    }

    /** 레이트리밋 버킷 키. 프록시 뒤면 X-Forwarded-For의 첫 IP, 아니면 원격 주소를 쓴다. */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
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
