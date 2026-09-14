package com.gongguhan.domain.penalty.controller;

import com.gongguhan.domain.penalty.dto.SellerPenaltySummaryResponse;
import com.gongguhan.domain.penalty.service.PenaltyQueryService;
import com.gongguhan.domain.penalty.service.PenaltyQueryService.SellerPenaltyResult;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 본인 위반 이력 조회 (API 명세서 13장, PEN-01).
 *
 * <p>판매자가 상태를 바꾸는 엔드포인트는 없습니다. 확정·기각·취소는 관리자만 합니다.
 */
@RestController
public class SellerPenaltyController {

    private final PenaltyQueryService penaltyQueryService;

    public SellerPenaltyController(PenaltyQueryService penaltyQueryService) {
        this.penaltyQueryService = penaltyQueryService;
    }

    /** PEN-01 위반 이력·누적 횟수 조회 (FR-PEN-09~12). */
    @GetMapping("/sellers/me/penalties")
    public ResponseEntity<ApiResponse<SellerPenaltySummaryResponse>> getMyPenalties(
            @AuthenticationPrincipal Long sellerId,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        SellerPenaltyResult result = penaltyQueryService.getMyPenalties(sellerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }
}
