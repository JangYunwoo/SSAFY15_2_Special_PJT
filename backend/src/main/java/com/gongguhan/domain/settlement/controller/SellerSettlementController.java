package com.gongguhan.domain.settlement.controller;

import com.gongguhan.domain.settlement.dto.SettlementDetailResponse;
import com.gongguhan.domain.settlement.dto.SettlementResponse;
import com.gongguhan.domain.settlement.service.SettlementQueryService;
import com.gongguhan.domain.settlement.service.SettlementQueryService.SettlementListResult;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 정산 조회 (API 명세서 12장, STL-01·02).
 *
 * <p>판매자가 정산을 실행하거나 보류하는 엔드포인트는 없습니다. 실행·보류·재처리는
 * 관리자만 합니다 — 판매자가 스스로 지급을 일으킬 수 있으면 검증을 우회하게 됩니다.
 */
@RestController
@RequestMapping("/sellers/settlements")
public class SellerSettlementController {

    private final SettlementQueryService settlementQueryService;

    public SellerSettlementController(SettlementQueryService settlementQueryService) {
        this.settlementQueryService = settlementQueryService;
    }

    /** STL-01 정산 목록 조회 (FR-STL-06~10, 14). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> getMySettlements(
            @AuthenticationPrincipal Long sellerId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        SettlementListResult result =
                settlementQueryService.getMySettlements(sellerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }

    /** STL-02 정산 상세·포함/제외 참여 건 조회 (FR-STL-07~08). */
    @GetMapping("/{settlementId}")
    public ResponseEntity<ApiResponse<SettlementDetailResponse>> getMySettlement(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long settlementId,
            HttpServletRequest httpRequest) {
        SettlementDetailResponse response =
                settlementQueryService.getSettlement(sellerId, settlementId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
