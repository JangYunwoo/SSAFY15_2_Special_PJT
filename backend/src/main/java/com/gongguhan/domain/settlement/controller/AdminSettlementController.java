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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 정산 조회 (API 명세서 12장, STL-03).
 *
 * <p>실행(STL-04)·보류(STL-05)·보류 해제(STL-06)·재처리(STL-07)는 실제 계좌이체를
 * 부르는 동작이라 별도 티켓({@code S15P21B205-82})입니다. 이 컨트롤러는 조회만
 * 담당합니다.
 *
 * <p><b>주의:</b> 관리자 인증({@code /auth/admins/login})은 아직 구현되지 않았습니다.
 * 코드와 테스트로만 검증됐고 실제 HTTP 호출 검증은 그 구현 이후에 가능합니다.
 */
@RestController
@RequestMapping("/admin/settlements")
public class AdminSettlementController {

    private final SettlementQueryService settlementQueryService;

    public AdminSettlementController(SettlementQueryService settlementQueryService) {
        this.settlementQueryService = settlementQueryService;
    }

    /** STL-03 전체 정산 목록·집계 조회 (FR-STL-12~14). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SettlementResponse>>> getSettlements(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        SettlementListResult result = settlementQueryService.getAllSettlements(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }

    /** 관리자 정산 상세. 목록에서 한 건을 여는 경로입니다. 소유권 검사를 하지 않습니다. */
    @GetMapping("/{settlementId}")
    public ResponseEntity<ApiResponse<SettlementDetailResponse>> getSettlement(
            @PathVariable Long settlementId,
            HttpServletRequest httpRequest) {
        SettlementDetailResponse response =
                settlementQueryService.getSettlement(null, settlementId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
