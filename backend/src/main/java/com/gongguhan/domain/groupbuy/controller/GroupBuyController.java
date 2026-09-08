package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/group-buys")
public class GroupBuyController {

    private final GroupBuyService groupBuyService;

    public GroupBuyController(GroupBuyService groupBuyService) {
        this.groupBuyService = groupBuyService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupBuySummaryResponse>>> getGroupBuys(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<GroupBuySummaryResponse> result = groupBuyService.getPublicGroupBuys(
                keyword, categoryId, status, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }

    @GetMapping("/{groupBuyId}")
    public ResponseEntity<ApiResponse<GroupBuyDetailResponse>> getGroupBuy(
            @PathVariable Long groupBuyId,
            HttpServletRequest httpRequest) {
        GroupBuyDetailResponse result = groupBuyService.getPublicDetail(groupBuyId);
        return ResponseEntity.ok(ApiResponse.success(result, TraceIdUtils.from(httpRequest)));
    }
}
