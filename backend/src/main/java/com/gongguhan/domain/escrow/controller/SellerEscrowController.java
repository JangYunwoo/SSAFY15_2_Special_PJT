package com.gongguhan.domain.escrow.controller;

import com.gongguhan.domain.escrow.dto.GroupBuyEscrowResponse;
import com.gongguhan.domain.escrow.service.EscrowQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 에스크로 조회 (API 명세서 8장).
 *
 * <p>{@code /sellers/**} 는 SecurityConfig 가 이미 {@code hasRole("SELLER")} 로 막습니다.
 * 여기서는 그 판매자가 <b>해당 공동구매의 소유자인지</b>를 서비스가 확인합니다.
 */
@RestController
@RequestMapping("/sellers/group-buys")
public class SellerEscrowController {

    private final EscrowQueryService escrowQueryService;

    public SellerEscrowController(EscrowQueryService escrowQueryService) {
        this.escrowQueryService = escrowQueryService;
    }

    /**
     * ESC-02 공동구매별 보관 내역·합계 조회.
     *
     * <p>{@code escrows} 목록은 페이지로 끊기고, {@code summary} 는 전체 기준입니다.
     * 페이지 정보는 응답 봉투의 {@code meta} 에 담깁니다.
     */
    @GetMapping("/{groupBuyId}/escrows")
    public ResponseEntity<ApiResponse<GroupBuyEscrowResponse>> getGroupBuyEscrows(
            @AuthenticationPrincipal Long sellerId,
            @PathVariable Long groupBuyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        EscrowQueryService.GroupBuyEscrowResult result =
                escrowQueryService.getGroupBuyEscrows(sellerId, groupBuyId, page, size);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }
}
