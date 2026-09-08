package com.gongguhan.domain.payment.crypto.controller;

import com.gongguhan.domain.payment.crypto.dto.CryptoAssetResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteResponse;
import com.gongguhan.domain.payment.crypto.service.CryptoQuoteService;
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
 * 디지털 자산 결제 API (API 명세서 6장).
 *
 * <p>이 컨트롤러는 CRYP-01·CRYP-02만 담는다. 트랜잭션 해시 등록(CRYP-03)과
 * 상태 조회(CRYP-04)는 체인 연동이 붙는 별도 작업이다.
 */
@RestController
public class CryptoPaymentController {

    private final CryptoQuoteService cryptoQuoteService;

    public CryptoPaymentController(CryptoQuoteService cryptoQuoteService) {
        this.cryptoQuoteService = cryptoQuoteService;
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
}
