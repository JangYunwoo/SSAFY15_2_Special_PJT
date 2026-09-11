package com.gongguhan.domain.returnrequest.controller;

import com.gongguhan.domain.returnrequest.dto.ReturnCreateRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnDisputeRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnPreviewRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnPreviewResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import com.gongguhan.domain.returnrequest.service.ReturnRequestService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/users/me")
public class BuyerReturnController {

    private final ReturnRequestService service;

    public BuyerReturnController(ReturnRequestService service) {
        this.service = service;
    }

    @PostMapping("/participations/{participationId}/returns/preview")
    public ResponseEntity<ApiResponse<ReturnPreviewResponse>> preview(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            @Valid @RequestBody ReturnPreviewRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                service.preview(userId, participationId, request), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/participations/{participationId}/returns")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> create(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            @Valid @RequestBody ReturnCreateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                service.create(userId, participationId, request), TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/returns")
    public ResponseEntity<ApiResponse<java.util.List<ReturnSummaryResponse>>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupBuyId,
            Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<ReturnSummaryResponse> result = service.buyerList(userId, status, groupBuyId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PageResponse.from(result),
                TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/returns/{returnId}")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> detail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                service.findBuyerDetail(userId, returnId), TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/returns/{returnId}/disputes")
    public ResponseEntity<ApiResponse<Void>> dispute(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long returnId,
            @Valid @RequestBody ReturnDisputeRequest request,
            HttpServletRequest httpRequest) {
        service.dispute(userId, returnId, request.detailReason());
        return ResponseEntity.ok(ApiResponse.success(null, TraceIdUtils.from(httpRequest)));
    }
}
