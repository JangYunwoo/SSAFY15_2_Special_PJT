package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerParticipantResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerGroupBuySummaryResponse>>> getGroupBuys(
            @AuthenticationPrincipal Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<SellerGroupBuySummaryResponse> result = groupBuyService.getSellerGroupBuys(sellerId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/{groupBuyId}/participants")
    public ResponseEntity<ApiResponse<List<SellerParticipantResponse>>> getParticipants(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<SellerParticipantResponse> result =
                groupBuyService.getSellerParticipants(sellerId, groupBuyId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }
}
