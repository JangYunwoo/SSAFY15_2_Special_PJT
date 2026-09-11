package com.gongguhan.domain.product.controller;

import com.gongguhan.domain.product.dto.ProductResponse;
import com.gongguhan.domain.product.dto.WithdrawalRestrictionReviewRequest;
import com.gongguhan.domain.product.service.WithdrawalRestrictionService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final WithdrawalRestrictionService service;

    public AdminProductController(WithdrawalRestrictionService service) {
        this.service = service;
    }

    @GetMapping("/withdrawal-restrictions")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getPendingRestrictions(HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.getPendingProducts(), TraceIdUtils.from(request)));
    }

    @PostMapping("/{productId}/withdrawal-restriction/review")
    public ResponseEntity<ApiResponse<ProductResponse>> review(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long productId,
            @Valid @RequestBody WithdrawalRestrictionReviewRequest reviewRequest,
            HttpServletRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.review(adminId, productId, reviewRequest.status()), TraceIdUtils.from(request)));
    }
}
