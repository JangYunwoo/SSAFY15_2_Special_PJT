package com.gongguhan.domain.escrow.controller;

import com.gongguhan.domain.escrow.dto.EscrowResponse;
import com.gongguhan.domain.escrow.service.EscrowQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 에스크로 조회 (API 명세서 8장).
 *
 * <p>상태를 바꾸는 엔드포인트는 없습니다. 해제·환불은 결제·정산·환불 유스케이스
 * 내부에서만 일어납니다.
 */
@RestController
public class EscrowController {

    private final EscrowQueryService escrowQueryService;

    public EscrowController(EscrowQueryService escrowQueryService) {
        this.escrowQueryService = escrowQueryService;
    }

    /** ESC-01 참여자 에스크로 상태 조회. */
    @GetMapping("/users/me/participations/{participationId}/escrow")
    public ResponseEntity<ApiResponse<EscrowResponse>> getMyEscrow(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long participationId,
            HttpServletRequest httpRequest) {
        EscrowResponse response = escrowQueryService.getMyEscrow(userId, participationId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
