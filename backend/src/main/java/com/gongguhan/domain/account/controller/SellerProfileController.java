package com.gongguhan.domain.account.controller;

import com.gongguhan.domain.account.dto.SellerProfileResponse;
import com.gongguhan.domain.account.dto.SellerProfileUpdateRequest;
import com.gongguhan.domain.account.service.SellerProfileService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 판매자 프로필 API (SEL-06·07). /sellers/**는 SecurityConfig에서 SELLER 역할을 요구한다. */
@RestController
@RequestMapping("/sellers/me")
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    public SellerProfileController(SellerProfileService sellerProfileService) {
        this.sellerProfileService = sellerProfileService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SellerProfileResponse>> getMe(
            @AuthenticationPrincipal Long sellerId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                sellerProfileService.getProfile(sellerId), TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<SellerProfileResponse>> updateMe(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody SellerProfileUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                sellerProfileService.updateProfile(sellerId, request), TraceIdUtils.from(httpRequest)));
    }
}
