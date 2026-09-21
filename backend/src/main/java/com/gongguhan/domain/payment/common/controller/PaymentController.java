package com.gongguhan.domain.payment.common.controller;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.dto.PaymentHistoryResponse;
import com.gongguhan.domain.payment.common.service.PaymentHistoryService;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentHistoryService paymentHistoryService;

    public PaymentController(PaymentService paymentService, PaymentHistoryService paymentHistoryService) {
        this.paymentService = paymentService;
        this.paymentHistoryService = paymentHistoryService;
    }

    @PostMapping("/group-buys/{groupBuyId}/payments/prepare")
    public ResponseEntity<ApiResponse<PaymentResponse>> prepare(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody PaymentPrepareRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        PaymentResponse response = paymentService.prepare(userId, groupBuyId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            HttpServletRequest httpRequest) {
        PaymentResponse response = paymentService.getPayment(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/payments/{paymentId}/abort")
    public ResponseEntity<ApiResponse<Void>> abort(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            HttpServletRequest httpRequest) {
        paymentService.abort(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/users/me/payments")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> listHistory(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<PaymentHistoryResponse> page = paymentHistoryService.listHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                page.getContent(), PageResponse.from(page), TraceIdUtils.from(httpRequest)));
    }
}
