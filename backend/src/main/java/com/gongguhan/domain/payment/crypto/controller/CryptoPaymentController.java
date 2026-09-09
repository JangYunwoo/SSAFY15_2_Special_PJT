package com.gongguhan.domain.payment.crypto.controller;

import com.gongguhan.domain.payment.crypto.dto.CryptoAssetResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoPaymentStatusResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoTxSubmitRequest;
import com.gongguhan.domain.payment.crypto.service.CryptoQuoteService;
import com.gongguhan.domain.payment.crypto.service.CryptoTxVerificationService;
import com.gongguhan.global.common.ApiResponse;
import com.gongguhan.global.common.TraceIdUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 디지털 자산 결제 API (API 명세서 6장, CRYP-01~04).
 *
 * <p>결제 흐름은 네 단계다.
 *
 * <ol>
 *   <li>CRYP-01 — 지원 자산 목록 확인</li>
 *   <li>CRYP-02 — 시세에 Buffer 를 얹은 Quote 발급, 수취 주소 안내</li>
 *   <li>CRYP-03 — MetaMask 송금 후 txHash 등록. 서버가 영수증을 직접 검증</li>
 *   <li>CRYP-04 — 컨펌 진행 상태 조회. 확정은 폴링 스케줄러가 처리</li>
 * </ol>
 */
@RestController
public class CryptoPaymentController {

    private final CryptoQuoteService cryptoQuoteService;
    private final CryptoTxVerificationService cryptoTxVerificationService;

    public CryptoPaymentController(CryptoQuoteService cryptoQuoteService,
                                    CryptoTxVerificationService cryptoTxVerificationService) {
        this.cryptoQuoteService = cryptoQuoteService;
        this.cryptoTxVerificationService = cryptoTxVerificationService;
    }

    /** CRYP-01 지원 디지털 자산 목록. */
    @GetMapping("/crypto/assets")
    public ResponseEntity<ApiResponse<List<CryptoAssetResponse>>> listAssets(
            HttpServletRequest httpRequest) {
        List<CryptoAssetResponse> assets = cryptoQuoteService.listAssets();
        return ResponseEntity.ok(ApiResponse.success(assets, TraceIdUtils.from(httpRequest)));
    }

    /** CRYP-02 환율·Buffer 적용 Quote 생성. */
    @PostMapping("/payments/{paymentId}/crypto/quote")
    public ResponseEntity<ApiResponse<CryptoQuoteResponse>> issueQuote(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            @Valid @RequestBody CryptoQuoteRequest request,
            HttpServletRequest httpRequest) {
        CryptoQuoteResponse response = cryptoQuoteService.issueQuote(userId, paymentId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    /**
     * CRYP-03 사용자가 제출한 트랜잭션 해시 등록.
     *
     * <p>등록에 성공해도 결제 완료가 아니다. 필요 컨펌 수에 도달해야 확정되며,
     * 보통은 폴링 스케줄러가 그 시점에 처리한다. 응답의 {@code phase}로 진행 상태를
     * 알 수 있다.
     */
    @PostMapping("/payments/{paymentId}/crypto/transactions")
    public ResponseEntity<ApiResponse<CryptoPaymentStatusResponse>> submitTransaction(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            @Valid @RequestBody CryptoTxSubmitRequest request,
            HttpServletRequest httpRequest) {
        CryptoPaymentStatusResponse response =
                cryptoTxVerificationService.submitTransaction(userId, paymentId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }

    /** CRYP-04 입금·컨펌·매칭 상태 조회. */
    @GetMapping("/payments/{paymentId}/crypto/status")
    public ResponseEntity<ApiResponse<CryptoPaymentStatusResponse>> getStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long paymentId,
            HttpServletRequest httpRequest) {
        CryptoPaymentStatusResponse response =
                cryptoTxVerificationService.getStatus(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.success(response, TraceIdUtils.from(httpRequest)));
    }
}
