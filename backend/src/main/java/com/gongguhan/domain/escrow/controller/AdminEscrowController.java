package com.gongguhan.domain.escrow.controller;

import com.gongguhan.domain.escrow.dto.EscrowAdminSummaryResponse;
import com.gongguhan.domain.escrow.service.EscrowQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 에스크로 집계 (API 명세서 8장 ESC-03).
 *
 * <p>{@code /admin/**} 는 SecurityConfig 가 {@code hasRole("ADMIN")} 으로 막습니다.
 * 별도 소유권 검증이 없는 이유는 플랫폼 전체를 보는 화면이기 때문입니다.
 *
 * <p><b>주의:</b> 관리자 인증(`/auth/admins/login`)은 SecurityConfig 에 경로만 선언돼
 * 있고 아직 구현되지 않았습니다. 그래서 이 엔드포인트는 코드와 단위 테스트로만
 * 검증됐고 실제 HTTP 호출 검증은 관리자 인증 구현 이후에 가능합니다.
 */
@RestController
@RequestMapping("/admin/escrows")
public class AdminEscrowController {

    private final EscrowQueryService escrowQueryService;

    public AdminEscrowController(EscrowQueryService escrowQueryService) {
        this.escrowQueryService = escrowQueryService;
    }

    /** ESC-03 전체 보관액·정산 대기액 집계. */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<EscrowAdminSummaryResponse>> getSummary(
            HttpServletRequest httpRequest) {
        EscrowAdminSummaryResponse response = escrowQueryService.getAdminSummary();
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
