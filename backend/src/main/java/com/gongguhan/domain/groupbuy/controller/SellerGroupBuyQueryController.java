package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.SellerGroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.SellerParticipantResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/group-buys")
public class SellerGroupBuyQueryController {

    private final GroupBuyQueryService groupBuyQueryService;

    public SellerGroupBuyQueryController(GroupBuyQueryService groupBuyQueryService) {
        this.groupBuyQueryService = groupBuyQueryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SellerGroupBuySummaryResponse>>> getGroupBuys(
            @AuthenticationPrincipal Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<SellerGroupBuySummaryResponse> result = groupBuyQueryService.getSellerGroupBuys(sellerId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/{groupBuyId}")
    public ResponseEntity<ApiResponse<SellerGroupBuyDetailResponse>> getDetail(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                groupBuyQueryService.getSellerDetail(sellerId, groupBuyId),
                TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/{groupBuyId}/participants")
    public ResponseEntity<ApiResponse<List<SellerParticipantResponse>>> getParticipants(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<SellerParticipantResponse> result =
                groupBuyQueryService.getSellerParticipants(sellerId, groupBuyId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }
}
