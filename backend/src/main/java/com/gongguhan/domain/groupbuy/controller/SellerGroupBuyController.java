package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyTerminationRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyUpdateRequest;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyService;
import com.gongguhan.domain.groupbuy.service.GroupBuyWithdrawalService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sellers/group-buys")
public class SellerGroupBuyController {

    private final GroupBuyService groupBuyService;
    private final GroupBuyWithdrawalService groupBuyWithdrawalService;

    public SellerGroupBuyController(GroupBuyService groupBuyService,
                                    GroupBuyWithdrawalService groupBuyWithdrawalService) {
        this.groupBuyService = groupBuyService;
        this.groupBuyWithdrawalService = groupBuyWithdrawalService;
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
    public ResponseEntity<ApiResponse<SellerGroupBuyDetailResponse>> create(
            @AuthenticationPrincipal Long sellerId,
            @Valid @RequestBody GroupBuyCreateRequest request,
            HttpServletRequest httpRequest) {
        SellerGroupBuyDetailResponse response = groupBuyService.create(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    @PatchMapping("/{groupBuyId}")
    public ResponseEntity<ApiResponse<SellerGroupBuyDetailResponse>> update(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                groupBuyService.update(sellerId, groupBuyId, request),
                TraceIdUtils.from(httpRequest)));
    }

    @PostMapping("/{groupBuyId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyTerminationRequest request) {
        groupBuyService.cancel(sellerId, groupBuyId, request.reason());
        return ResponseEntity.noContent().build();
    }

    /** GRP-10 참여 발생 후 판매자 귀책 판매 철회(공동구매 취소 + 전원 환불 + 위반 후보 seam). */
    @PostMapping("/{groupBuyId}/withdrawal")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @Valid @RequestBody GroupBuyTerminationRequest request) {
        groupBuyWithdrawalService.withdraw(sellerId, groupBuyId, request.reason());
        return ResponseEntity.noContent().build();
    }
}
