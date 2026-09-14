package com.gongguhan.domain.penalty.controller;

import com.gongguhan.domain.penalty.dto.PenaltyResponse;
import com.gongguhan.domain.penalty.dto.PenaltyReviewRequest;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.service.PenaltyQueryService;
import com.gongguhan.domain.penalty.service.PenaltyQueryService.AdminPenaltyResult;
import com.gongguhan.domain.penalty.service.PenaltyQueryService.PenaltyDetailResult;
import com.gongguhan.domain.penalty.service.PenaltyService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 위반 검토 (API 명세서 13장, PEN-02~06).
 *
 * <p>{@code /admin/**} 는 SecurityConfig 가 {@code hasRole("ADMIN")} 으로 막습니다.
 *
 * <p><b>주의:</b> 관리자 인증({@code /auth/admins/login})은 아직 구현되지 않았습니다.
 * 그래서 이 엔드포인트들은 코드와 테스트로만 검증됐고, 실제 HTTP 호출 검증은 관리자
 * 인증 구현 이후에 가능합니다. 에스크로 ESC-03·환불 RFD-03/04 와 같은 상황입니다.
 */
@RestController
@RequestMapping("/admin/penalties")
public class AdminPenaltyController {

    private final PenaltyQueryService penaltyQueryService;
    private final PenaltyService penaltyService;

    public AdminPenaltyController(PenaltyQueryService penaltyQueryService,
                                  PenaltyService penaltyService) {
        this.penaltyQueryService = penaltyQueryService;
        this.penaltyService = penaltyService;
    }

    /** PEN-02 위반 후보·확정·기각 목록 조회 (FR-PEN-03~06, FR-ADM-10). */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PenaltyResponse>>> getPenalties(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        AdminPenaltyResult result = penaltyQueryService.getPenalties(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }

    /** PEN-03 위반 후보 근거 조회 (FR-PEN-06). 같은 판매자의 확정 이력을 함께 돌려줍니다. */
    @GetMapping("/{penaltyId}")
    public ResponseEntity<ApiResponse<PenaltyDetailResult>> getPenalty(
            @PathVariable Long penaltyId,
            HttpServletRequest httpRequest) {
        PenaltyDetailResult result = penaltyQueryService.getPenalty(penaltyId);
        return ResponseEntity.ok(ApiResponse.success(result, TraceIdUtils.from(httpRequest)));
    }

    /** PEN-04 위반 확정·누적·필요 시 정지 (FR-PEN-06~10). */
    @PostMapping("/{penaltyId}/confirm")
    public ResponseEntity<ApiResponse<PenaltyResponse>> confirm(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long penaltyId,
            @Valid @RequestBody PenaltyReviewRequest request,
            HttpServletRequest httpRequest) {
        SellerPenalty penalty = penaltyService.confirm(adminId, penaltyId, request.memo());
        return ResponseEntity.ok(ApiResponse.success(
                PenaltyResponse.forAdmin(penalty), TraceIdUtils.from(httpRequest)));
    }

    /** PEN-05 위반 후보 기각 (FR-PEN-06). */
    @PostMapping("/{penaltyId}/dismiss")
    public ResponseEntity<ApiResponse<PenaltyResponse>> dismiss(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long penaltyId,
            @Valid @RequestBody PenaltyReviewRequest request,
            HttpServletRequest httpRequest) {
        SellerPenalty penalty = penaltyService.dismiss(adminId, penaltyId, request.memo());
        return ResponseEntity.ok(ApiResponse.success(
                PenaltyResponse.forAdmin(penalty), TraceIdUtils.from(httpRequest)));
    }

    /** PEN-06 확정 위반 취소 (FR-PEN-13). 누적이 임계 아래로 내려가면 정지도 풀립니다. */
    @PostMapping("/{penaltyId}/revoke")
    public ResponseEntity<ApiResponse<PenaltyResponse>> revoke(
            @AuthenticationPrincipal Long adminId,
            @PathVariable Long penaltyId,
            @Valid @RequestBody PenaltyReviewRequest request,
            HttpServletRequest httpRequest) {
        SellerPenalty penalty = penaltyService.revoke(adminId, penaltyId, request.memo());
        return ResponseEntity.ok(ApiResponse.success(
                PenaltyResponse.forAdmin(penalty), TraceIdUtils.from(httpRequest)));
    }
}
