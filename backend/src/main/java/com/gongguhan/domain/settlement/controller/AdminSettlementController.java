package com.gongguhan.domain.settlement.controller;

import com.gongguhan.domain.settlement.dto.SettlementDetailResponse;
import com.gongguhan.domain.settlement.dto.SettlementHoldRequest;
import com.gongguhan.domain.settlement.dto.SettlementResponse;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.service.SettlementQueryService;
import com.gongguhan.domain.settlement.service.SettlementQueryService.SettlementListResult;
import com.gongguhan.domain.settlement.service.SettlementTransferService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 정산 조회 (API 명세서 12장, STL-03).
 *
 * <p>실행(STL-04)·보류(STL-05)·보류 해제(STL-06)·재처리(STL-07)는 실제 계좌이체를
 * 부릅니다. 판매자에게는 이 엔드포인트들을 열지 않습니다 — 판매자가 스스로 지급을
 * 일으킬 수 있으면 검증을 우회하게 됩니다.
 *
 * <p><b>주의:</b> 관리자 인증({@code /auth/admins/login})은 아직 구현되지 않았습니다.
 * 코드와 테스트로만 검증됐고 실제 HTTP 호출 검증은 그 구현 이후에 가능합니다.
 */
@RestController
@RequestMapping("/admin/settlements")
public class AdminSettlementController {

    private final SettlementQueryService settlementQueryService;
    private final SettlementTransferService settlementTransferService;

    public AdminSettlementController(SettlementQueryService settlementQueryService,
                                     SettlementTransferService settlementTransferService) {
        this.settlementQueryService = settlementQueryService;
        this.settlementTransferService = settlementTransferService;
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

    /**
     * STL-04 정산 계좌이체 실행 (FR-STL-01~05, 12).
     *
     * <p>실행 전에 상태를 다시 확인합니다 — 목록을 조회한 뒤 버튼을 누르기까지 사이에
     * 보류되거나 이미 이체됐을 수 있습니다.
     *
     * <p>이체가 실패해도 200 입니다. 응답의 {@code transferStatus} 와
     * {@code transferFailReason} 으로 결과를 판단하세요 — 실패는 오류가 아니라
     * 재처리 대상이라는 상태이기 때문입니다.
     */
    @PostMapping("/{settlementId}/execute")
    public ResponseEntity<ApiResponse<SettlementResponse>> execute(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long settlementId,
            HttpServletRequest httpRequest) {
        Settlement settlement = settlementTransferService.execute(adminId, settlementId);
        return ResponseEntity.ok(ApiResponse.success(
                SettlementResponse.from(settlement), TraceIdUtils.from(httpRequest)));
    }

    /** STL-05 분쟁 사유 정산 보류 (FR-STL-13). */
    @PostMapping("/{settlementId}/hold")
    public ResponseEntity<ApiResponse<SettlementResponse>> hold(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long settlementId,
            @Valid @RequestBody SettlementHoldRequest request,
            HttpServletRequest httpRequest) {
        Settlement settlement =
                settlementTransferService.hold(adminId, settlementId, request.reason());
        return ResponseEntity.ok(ApiResponse.success(
                SettlementResponse.from(settlement), TraceIdUtils.from(httpRequest)));
    }

    /** STL-06 정산 보류 해제 (FR-STL-13). 다시 실행 대기 상태가 됩니다. */
    @PostMapping("/{settlementId}/release-hold")
    public ResponseEntity<ApiResponse<SettlementResponse>> releaseHold(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long settlementId,
            HttpServletRequest httpRequest) {
        Settlement settlement = settlementTransferService.releaseHold(adminId, settlementId);
        return ResponseEntity.ok(ApiResponse.success(
                SettlementResponse.from(settlement), TraceIdUtils.from(httpRequest)));
    }

    /**
     * STL-07 실패 정산 재처리 (FR-ADM-08).
     *
     * <p>실행(STL-04)과 같은 동작입니다. 자동 재시도 배치가 건드리지 않는
     * <b>결과 불명({@code UNKNOWN}) 건</b>을 사람이 확인한 뒤 처리하는 경로이기도
     * 합니다. 다만 결과 불명 상태에서는 그대로 재실행할 수 없고, 금융망 거래내역을
     * 확인한 뒤 상태를 정리해야 합니다 — 이미 이체됐는데 다시 보내면 이중 지급입니다.
     */
    @PostMapping("/{settlementId}/retry")
    public ResponseEntity<ApiResponse<SettlementResponse>> retry(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long settlementId,
            HttpServletRequest httpRequest) {
        Settlement settlement = settlementTransferService.execute(adminId, settlementId);
        return ResponseEntity.ok(ApiResponse.success(
                SettlementResponse.from(settlement), TraceIdUtils.from(httpRequest)));
    }
}
