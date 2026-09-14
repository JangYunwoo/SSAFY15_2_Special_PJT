package com.gongguhan.domain.payment.card.controller;

import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.CardPaymentService;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CardPaymentController {

    private final CardPaymentService cardPaymentService;
    private final PaymentService paymentService;

    public CardPaymentController(CardPaymentService cardPaymentService, PaymentService paymentService) {
        this.cardPaymentService = cardPaymentService;
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/{paymentId}/card/approve")
    public ResponseEntity<ApiResponse<PaymentResponse>> approve(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            @Valid @RequestBody CardApproveRequest request,
            HttpServletRequest httpRequest) {
        cardPaymentService.approve(userId, paymentId, request);
        PaymentResponse response = paymentService.getPayment(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
