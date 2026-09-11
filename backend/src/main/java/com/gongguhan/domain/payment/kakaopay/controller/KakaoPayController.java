package com.gongguhan.domain.payment.kakaopay.controller;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayReadyResponse;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KakaoPayController {

    private final KakaoPayService kakaoPayService;

    public KakaoPayController(KakaoPayService kakaoPayService) {
        this.kakaoPayService = kakaoPayService;
    }

    @PostMapping("/payments/{paymentId}/kakaopay/ready")
    public ResponseEntity<ApiResponse<KakaoPayReadyResponse>> ready(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        validateIdempotencyKey(idempotencyKey);
        KakaoPayReadyResponse response = kakaoPayService.ready(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/payments/kakaopay/approve")
    public ResponseEntity<ApiResponse<PaymentResponse>> approve(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody KakaoPayApproveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        validateIdempotencyKey(idempotencyKey);
        PaymentResponse response = kakaoPayService.approve(userId, request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/payments/{paymentId}/kakaopay/cancel-flow")
    public ResponseEntity<ApiResponse<Void>> cancelFlow(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            HttpServletRequest httpRequest) {
        kakaoPayService.cancelFlow(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/payments/{paymentId}/kakaopay/fail-flow")
    public ResponseEntity<ApiResponse<Void>> failFlow(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            HttpServletRequest httpRequest) {
        kakaoPayService.failFlow(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key는 1자 이상 64자 이하여야 합니다.");
        }
    }
}
