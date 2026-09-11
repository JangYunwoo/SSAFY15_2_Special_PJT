package com.gongguhan.domain.returnrequest.controller;

import com.gongguhan.domain.returnrequest.dto.AdminReturnResolveRequest;
import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import com.gongguhan.domain.returnrequest.service.ReturnRequestService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/admin/returns")
public class AdminReturnController {

    private final ReturnRequestService service;

    public AdminReturnController(ReturnRequestService service) {
        this.service = service;
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<List<ReturnSummaryResponse>>> reviews(
            @AuthenticationPrincipal Long adminId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupBuyId,
            Pageable pageable,
            HttpServletRequest httpRequest) {
        Page<ReturnSummaryResponse> result = service.adminReviewList(
                adminId, status, groupBuyId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PageResponse.from(result),
                TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/{returnId}/resolve")
    public ResponseEntity<ApiResponse<ReturnDetailResponse>> resolve(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long returnId,
            @Valid @RequestBody AdminReturnResolveRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                service.resolveByAdmin(adminId, returnId, request.decision(), request.reason()),
                TraceIdUtils.from(httpRequest)));
    }
}
