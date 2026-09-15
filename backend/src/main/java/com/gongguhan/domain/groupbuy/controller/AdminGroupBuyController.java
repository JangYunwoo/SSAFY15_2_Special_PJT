package com.gongguhan.domain.groupbuy.controller;

import com.gongguhan.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.service.GroupBuyQueryService;
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

/**
 * 관리자 공동구매 검색·조회 (ADM-01·02). {@code /admin/**}은 SecurityConfig가 ROLE_ADMIN으로 막는다.
 *
 * <p>거래·결제·배송·환불·정산 "종합"은 도메인별 관리자 API({@code /admin/settlements},
 * {@code /admin/refunds}, {@code /admin/penalties}, {@code /admin/returns})로 이미 조회할 수 있어,
 * 여기서는 공동구매 검색과 상세를 제공한다(기존 조회 재사용).
 */
@RestController
@RequestMapping("/admin/group-buys")
public class AdminGroupBuyController {

    private final GroupBuyQueryService groupBuyQueryService;

    public AdminGroupBuyController(GroupBuyQueryService groupBuyQueryService) {
        this.groupBuyQueryService = groupBuyQueryService;
    }

    /** ADM-01 전체 공동구매·거래 상태 검색. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<GroupBuySummaryResponse>>> getGroupBuys(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Page<GroupBuySummaryResponse> result =
                groupBuyQueryService.getPublicGroupBuys(keyword, categoryId, status, sort, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.getContent(), PageResponse.from(result), TraceIdUtils.from(httpRequest)));
    }

    /** ADM-02 거래 종합 조회(공동구매 상세). 결제·환불·정산 세부는 도메인별 /admin API 참고. */
    @GetMapping("/{groupBuyId}")
    public ResponseEntity<ApiResponse<GroupBuyDetailResponse>> getGroupBuy(
            @PathVariable Long groupBuyId, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ApiResponse.success(
                groupBuyQueryService.getPublicDetail(groupBuyId), TraceIdUtils.from(httpRequest)));
    }
}
