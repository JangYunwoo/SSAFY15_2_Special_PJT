package com.gongguhan.domain.participation.controller;

import com.gongguhan.domain.participation.dto.PurchaseConfirmRequest;
import com.gongguhan.domain.participation.dto.PurchaseConfirmResponse;
import com.gongguhan.domain.participation.service.PurchaseConfirmService;
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

/** 구매확정 API (CFM-01). */
@RestController
public class PurchaseConfirmController {

    private final PurchaseConfirmService purchaseConfirmService;

    public PurchaseConfirmController(PurchaseConfirmService purchaseConfirmService) {
        this.purchaseConfirmService = purchaseConfirmService;
    }

    @PostMapping("/users/me/participations/{participationId}/confirm")
    public ResponseEntity<ApiResponse<PurchaseConfirmResponse>> confirm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            @Valid @RequestBody PurchaseConfirmRequest request,
            HttpServletRequest httpRequest) {
        PurchaseConfirmResponse response =
                purchaseConfirmService.confirmManually(userId, participationId, request.noticeAgreed());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
