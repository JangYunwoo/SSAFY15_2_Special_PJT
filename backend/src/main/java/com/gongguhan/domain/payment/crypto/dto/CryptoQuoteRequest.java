package com.gongguhan.domain.payment.crypto.dto;

import jakarta.validation.constraints.NotNull;

/**
 * CRYP-02 Quote 발급 요청.
 *
 * <p>{@code assetId}를 다시 받는 이유는 {@code payments} 테이블에 결제 준비 시
 * 고른 자산을 저장하는 컬럼이 없기 때문이다. PAY-01이 {@code cryptoAssetId}를
 * 검증만 하고 버린다. 스키마에 컬럼을 추가하면 이 필드는 없앨 수 있다.
 */
public record CryptoQuoteRequest(
        @NotNull(message = "assetId는 필수입니다.") Long assetId
) {
}
