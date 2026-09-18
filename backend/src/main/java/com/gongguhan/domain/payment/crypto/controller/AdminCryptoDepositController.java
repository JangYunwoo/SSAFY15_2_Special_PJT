package com.gongguhan.domain.payment.crypto.controller;

import com.gongguhan.domain.payment.crypto.dto.CryptoDepositResponse;
import com.gongguhan.domain.payment.crypto.service.CryptoDepositRefundService;
import com.gongguhan.domain.refund.dto.RefundResponse;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.service.RefundQueryService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제에 붙지 못한 입금의 조회·환불 (S15P21B205-80, FR-PAY-26 / FR-SLT-10).
 *
 * <p>{@code /admin/**} 는 SecurityConfig 가 {@code hasRole("ADMIN")} 으로 막습니다.
 *
 * <p><b>왜 관리자 경로인가</b> — 돈을 내보내는 동작이라 사람 확인을 거칩니다. 특히
 * {@code UNDERPAID} 는 구매자가 곧 부족분을 더 보낼 수도 있어, 배치가 성급히 되돌리면
 * 정상 결제를 방해합니다. 정산에서 집계와 실행을 나눈 것과 같은 판단입니다.
 */
@RestController
@RequestMapping("/admin/crypto/deposits")
public class AdminCryptoDepositController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final CryptoDepositRefundService depositRefundService;
    private final RefundQueryService refundQueryService;

    public AdminCryptoDepositController(CryptoDepositRefundService depositRefundService,
                                        RefundQueryService refundQueryService) {
        this.depositRefundService = depositRefundService;
        this.refundQueryService = refundQueryService;
    }

    /**
     * 돌려보낼 수 있는 입금 목록.
     *
     * <p>{@code UNMATCHED}·{@code UNDERPAID}·{@code OVERPAID} 만 나옵니다. 결제에 붙은
     * 입금({@code MATCHED})은 돌려보낼 대상이 아닙니다.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CryptoDepositResponse>>> getRefundableDeposits(
            HttpServletRequest httpRequest) {
        List<CryptoDepositResponse> body = depositRefundService.findRefundable().stream()
                .map(CryptoDepositResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(body, TraceIdUtils.from(httpRequest)));
    }

    /**
     * 입금을 보낸 지갑으로 되돌려 보냅니다.
     *
     * <p><b>{@code Idempotency-Key} 가 필수입니다</b>(명세 11장). 돈을 내보내는 동작이라
     * 관리자가 버튼을 두 번 누르거나 응답이 늦어 다시 요청하면 이중 지급이 됩니다.
     *
     * <p>키는 형식만 검증합니다. 실제 이중 실행 차단은 원장이 합니다 —
     * {@code uk_refunds_active_deposit(deposit_id, refund_marker)} 가 한 입금에 활성
     * 환불을 하나로 묶고, 개시가 기존 행을 돌려주면 송금하지 않습니다. RFD-04 와 같은
     * 방식입니다.
     */
    @PostMapping("/{depositId}/refund")
    public ResponseEntity<ApiResponse<RefundResponse>> refund(
            @PathVariable Long depositId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        validateIdempotencyKey(idempotencyKey);

        Refund refund = depositRefundService.refund(depositId);
        RefundResponse response = refundQueryService.getRefund(refund.getRefundId());
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key 는 1~%d자여야 합니다.".formatted(IDEMPOTENCY_KEY_MAX_LENGTH));
        }
    }
}
