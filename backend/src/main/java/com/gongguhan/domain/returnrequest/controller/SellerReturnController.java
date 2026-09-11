package com.gongguhan.domain.returnrequest.controller;

import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnRejectRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import com.gongguhan.domain.returnrequest.service.ReturnRequestService;
import com.gongguhan.domain.returnrequest.service.ReturnRefundService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/returns")
public class SellerReturnController {

    private final ReturnRequestService service;
    private final ReturnRefundService refundService;

    public SellerReturnController(ReturnRequestService service,
                                  ReturnRefundService refundService) {
        this.service = service;
        this.refundService = refundService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<java.util.List<ReturnSummaryResponse>>> list(
            @AuthenticationPrincipal Long sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupBuyId,
            Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<ReturnSummaryResponse> result = service.sellerList(sellerId, status, groupBuyId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PageResponse.from(result),
                TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> approve(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long returnId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                service.approve(sellerId, returnId), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/{returnId}/reject")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> reject(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long returnId,
            @Valid @RequestBody ReturnRejectRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                service.reject(sellerId, returnId, request.reason()), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/{returnId}/pickup-complete")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> pickupComplete(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long returnId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.pickupComplete(sellerId, returnId), TraceIdUtils.from(httpRequest)));
    }
}
