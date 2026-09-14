package com.gongguhan.domain.refund.controller;

import com.gongguhan.domain.refund.dto.RefundResponse;
import com.gongguhan.domain.refund.service.RefundQueryService;
import com.gongguhan.domain.refund.service.RefundQueryService.RefundListResult;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구매자 환불 조회 (API 명세서 11장, RFD-01·02).
 *
 * <p>환불을 개시하는 엔드포인트는 없습니다. 환불은 모집 실패·참여 취소·반품 같은
 * 다른 유스케이스의 결과로 일어나며, 구매자가 직접 "환불해줘" 라고 호출하는 경로는
 * 명세에 없습니다. 에스크로와 같은 원칙입니다.
 */
@RestController
public class RefundController {

    private final RefundQueryService refundQueryService;

    public RefundController(RefundQueryService refundQueryService) {
        this.refundQueryService = refundQueryService;
    }

    /** RFD-01 내 환불 목록 조회. */
    @GetMapping("/users/me/refunds")
    public ResponseEntity<ApiResponse<java.util.List<RefundResponse>>> getMyRefunds(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        RefundListResult result = refundQueryService.getMyRefunds(userId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }

    /** RFD-02 환불 상세·처리 결과 조회. */
    @GetMapping("/users/me/refunds/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>> getMyRefund(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long refundId,
            HttpServletRequest httpRequest) {
        RefundResponse response = refundQueryService.getMyRefund(userId, refundId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
