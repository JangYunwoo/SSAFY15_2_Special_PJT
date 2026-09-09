package com.gongguhan.domain.payment.crypto.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 코인 결제 설정.
 *
 * @param chainId                  결제를 받는 체인. Polygon Amoy = 80002.
 * @param rpcUrl                   JSON-RPC 엔드포인트. 영수증·블록·로그 조회에 쓴다.
 * @param rpcTimeoutMs             RPC 타임아웃. 공용 노드는 간헐적으로 느려진다.
 * @param confirmationPollBatchSize 컨펌 폴링 1회에 처리할 최대 건수. RPC 호출이
 *                                 건당 1회 이상 발생하므로 한 주기에 몰아넣으면
 *                                 노드 호출 한도에 걸리고 다음 실행과 겹친다.
 * @param exchangeBaseUrl          시세 조회 API 기준 URL.
 * @param exchangeMarket           조회할 마켓 코드. Upbit 기준 {@code KRW-USDT}.
 * @param exchangeTimeoutMs        시세 조회 타임아웃. 결제 화면을 붙잡지 않도록 짧게.
 * @param rateCacheTtlMs           시세 캐시 유효시간(NFR-PF-03). 길게 잡으면 낡은
 *                                 환율로 견적을 내게 되고, Buffer(0.3%)로 흡수할 수
 *                                 없는 차이가 생겨 플랫폼이 손실을 떠안는다.
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(
        long chainId,
        String rpcUrl,
        int rpcTimeoutMs,
        int confirmationPollBatchSize,
        String exchangeBaseUrl,
        String exchangeMarket,
        int exchangeTimeoutMs,
        long rateCacheTtlMs
) {
}
