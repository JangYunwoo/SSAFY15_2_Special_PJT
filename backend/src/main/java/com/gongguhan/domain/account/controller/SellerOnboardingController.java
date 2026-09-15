package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.LoginResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.AccountConfirmRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.AccountRequestResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneConfirmRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneRequest;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.PhoneRequestResponse;
import com.gongguhan.domain.account.dto.SellerVerificationDtos.SellerProfileCreateRequest;
import com.gongguhan.domain.account.service.SellerOnboardingService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 전환 API (SEL-01~05). {@code /users/me/**}라 인증된 일반 사용자가 호출한다
 * (판매자 전환 전이므로 SELLER 역할이 아니어도 접근 가능).
 */
@RestController
@RequestMapping("/users/me")
public class SellerOnboardingController {

    private final SellerOnboardingService onboardingService;

    public SellerOnboardingController(SellerOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    /** SEL-01 휴대폰 인증 요청(목업). */
    @PostMapping("/seller-verifications/phone/request")
    public ResponseEntity<ApiResponse<PhoneRequestResponse>> requestPhone(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PhoneRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.requestPhone(userId, request), TraceIdUtils.from(httpRequest)));
    }

    /** SEL-02 휴대폰 인증 확인. */
    @PostMapping("/seller-verifications/phone/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmPhone(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody PhoneConfirmRequest request, HttpServletRequest httpRequest) {
        onboardingService.confirmPhone(userId, request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }

    /** SEL-03 정산계좌 1원 인증 요청(실제 SSAFY). 계좌가 없으면 자동 개설. */
    @PostMapping("/seller-verifications/account/request")
    public ResponseEntity<ApiResponse<AccountRequestResponse>> requestAccount(
            @AuthenticationPrincipal Long userId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.requestAccount(userId), TraceIdUtils.from(httpRequest)));
    }

    /** SEL-04 정산계좌 1원 인증 확인(실제 SSAFY). */
    @PostMapping("/seller-verifications/account/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmAccount(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody AccountConfirmRequest request, HttpServletRequest httpRequest) {
        onboardingService.confirmAccount(userId, request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, TraceIdUtils.from(httpRequest)));
    }

    /** SEL-05 판매자 전환 확정. 새 토큰(isSeller=true)을 발급한다. */
    @PostMapping("/seller-profile")
    public ResponseEntity<ApiResponse<LoginResponse>> createSellerProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SellerProfileCreateRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.createSellerProfile(userId, request), TraceIdUtils.from(httpRequest)));
    }
}
