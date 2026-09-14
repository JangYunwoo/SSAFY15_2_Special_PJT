package com.gongguhan.domain.refund.controller;

import com.gongguhan.domain.refund.dto.RefundResponse;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.domain.refund.service.RefundQueryService;
import com.gongguhan.domain.refund.service.RefundQueryService.RefundListResult;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 환불 조회·재처리 (API 명세서 11장, RFD-03·04).
 *
 * <p>{@code /admin/**} 는 SecurityConfig 가 {@code hasRole("ADMIN")} 으로 막습니다.
 *
 * <p><b>주의:</b> 관리자 인증({@code /auth/admins/login})은 SecurityConfig 에 경로만
 * 선언돼 있고 아직 구현되지 않았습니다. 그래서 이 두 엔드포인트는 코드와 테스트로만
 * 검증됐고, 실제 HTTP 호출 검증은 관리자 인증 구현 이후에 가능합니다.
 */
@RestController
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final RefundQueryService refundQueryService;
    private final RefundOrchestrationService refundOrchestrationService;

    public AdminRefundController(RefundQueryService refundQueryService,
                                 RefundOrchestrationService refundOrchestrationService) {
        this.refundQueryService = refundQueryService;
        this.refundOrchestrationService = refundOrchestrationService;
    }

    /** RFD-03 실패 포함 환불 목록 조회. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundResponse>>> getRefunds(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            HttpServletRequest httpRequest) {
        RefundListResult result = refundQueryService.getAllRefunds(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(
                result.body(), result.meta(), TraceIdUtils.from(httpRequest)));
    }

    /** 관리자 환불 상세. 목록에서 한 건을 여는 경로입니다. */
    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefund(
            @PathVariable Long refundId,
            HttpServletRequest httpRequest) {
        RefundResponse response = refundQueryService.getRefund(refundId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    /**
     * RFD-04 실패 환불 재처리 (FR-RFD-09, FR-ADM-08).
     *
     * <p><b>{@code Idempotency-Key} 가 필수입니다</b>(명세 11장). 재처리는 돈을 다시
     * 내보내는 동작이라, 관리자가 버튼을 두 번 누르거나 응답이 늦어 다시 요청하면
     * 이중 지급이 됩니다.
     *
     * <p>지금 키는 형식 검증만 하고 저장하지는 않습니다. 실제 이중 실행 차단은
     * 원장 쪽에서 합니다 — 완료된 환불은 {@code retry()} 자체가 거부되고, 진행 중인
     * 환불은 {@code UNIQUE(participation_id, refund_marker)} 가 막습니다. 키 기반
     * 응답 캐싱까지 하려면 별도 저장소가 필요하며, 그건 결제 쪽
     * {@code payments.idempotency_key} 와 같은 방식으로 맞추는 것이 좋습니다.
     */
    @PostMapping("/{refundId}/retry")
    public ResponseEntity<ApiResponse<RefundResponse>> retry(
            @PathVariable Long refundId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        validateIdempotencyKey(idempotencyKey);

        Refund refund = refundOrchestrationService.retry(refundId);
        RefundResponse response = refundQueryService.getRefund(refund.getRefundId());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key는 1자 이상 " + IDEMPOTENCY_KEY_MAX_LENGTH + "자 이하여야 합니다.");
        }
    }
}
