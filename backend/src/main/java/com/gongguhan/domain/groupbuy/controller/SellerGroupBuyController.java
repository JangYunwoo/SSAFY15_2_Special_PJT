package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/group-buys")
public class SellerGroupBuyController {

    private final GroupBuyService groupBuyService;

    public SellerGroupBuyController(GroupBuyService groupBuyService) {
        this.groupBuyService = groupBuyService;
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<GroupBuyAmountPreviewResponse>> preview(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody GroupBuyAmountPreviewRequest request,
            HttpServletRequest httpRequest) {
        GroupBuyAmountPreviewResponse response = groupBuyService.preview(sellerId, request);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupBuyResponse>> create(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody GroupBuyCreateRequest request,
            HttpServletRequest httpRequest) {
        GroupBuyResponse response = groupBuyService.create(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
