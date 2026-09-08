package com.gongguhan.domain.payment.crypto.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 코인 결제 설정.
 *
 * @param chainId            결제를 받는 체인. Polygon Amoy = 80002.
 * @param exchangeBaseUrl    시세 조회 API 기준 URL.
 * @param exchangeMarket     조회할 마켓 코드. Upbit 기준 {@code KRW-USDT}.
 * @param exchangeTimeoutMs  시세 조회 타임아웃. 결제 화면을 붙잡지 않도록 짧게 둔다.
 * @param rateCacheTtlMs     시세 캐시 유효시간(NFR-PF-03). 길게 잡으면 낡은 환율로
 *                           견적을 내게 되고, Buffer(0.3%)로 흡수할 수 없는 차이가
 *                           생겨 플랫폼이 손실을 떠안는다.
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(
        long chainId,
        String exchangeBaseUrl,
        String exchangeMarket,
        int exchangeTimeoutMs,
        long rateCacheTtlMs
) {
}
