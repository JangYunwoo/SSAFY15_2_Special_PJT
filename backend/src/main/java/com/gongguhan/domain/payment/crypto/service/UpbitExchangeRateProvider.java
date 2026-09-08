package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Upbit 공개 시세 API로 원화 환율을 읽는다.
 *
 * <p>인증이 없는 공개 엔드포인트라 API 키가 필요하지 않다. 대신 호출 한도가 있어
 * Quote 발급마다 부르면 막힌다. 짧은 TTL 캐시로 호출 수를 줄인다(NFR-PF-03).
 *
 * <p><b>캐시를 프로세스 로컬로 둔 이유.</b> Redis를 쓰면 인스턴스 간에 공유되지만,
 * Redis 장애가 결제 경로를 막는다. 시세는 초 단위로 갱신되는 값이라 인스턴스별로
 * 조금 달라도 문제가 없다. 반대로 결제가 멈추는 건 치명적이다.
 *
 * <p><b>조회 실패 시 캐시를 재사용하지 않는다.</b> 낡은 환율로 견적을 내면 그 차이가
 * 전부 플랫폼 손실로 남는다. Buffer는 0.3%뿐이라 몇 분 된 시세도 흡수하지 못한다.
 * 견적 발급을 실패시키고 구매자에게 다시 시도하게 하는 편이 안전하다.
 */
@Component
public class UpbitExchangeRateProvider implements ExchangeRateProvider {

    private static final Logger log = LoggerFactory.getLogger(UpbitExchangeRateProvider.class);

    private final RestClient restClient;
    private final CryptoProperties properties;
    private final Clock clock;
    private final AtomicReference<CachedRate> cache = new AtomicReference<>();

    public UpbitExchangeRateProvider(CryptoProperties properties, Clock clock) {
        Duration timeout = Duration.ofMillis(properties.exchangeTimeoutMs());
        // 타임아웃을 반드시 건다. 기본값은 무제한이라 거래소가 응답을 끌면
        // 결제 화면이 그만큼 멈춘다(NFR-PF-04 슬롯 요청 1초 이내와 충돌).
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        // RestClient.Builder 빈을 주입받지 않는다. Spring Boot 4의
        // spring-boot-starter-webmvc에는 RestClientAutoConfiguration이 없어
        // 그 빈이 존재하지 않는다. 여기서 직접 만들면 공용 build.gradle에
        // 의존성을 추가하지 않아도 된다.
        this.restClient = RestClient.builder()
                .baseUrl(properties.exchangeBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public BigDecimal getKrwRate(String symbol) {
        CachedRate cached = cache.get();
        long now = clock.millis();
        if (cached != null && cached.isFresh(symbol, now, properties.rateCacheTtlMs())) {
            return cached.rate();
        }

        BigDecimal rate = fetch();
        cache.set(new CachedRate(symbol, rate, now));
        return rate;
    }

    private BigDecimal fetch() {
        List<Ticker> tickers;
        try {
            tickers = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/ticker")
                            .queryParam("markets", properties.exchangeMarket())
                            .build())
                    .retrieve()
                    .body(TICKER_LIST);
        } catch (RestClientException e) {
            // 응답 본문에 시세가 담기므로 예외 메시지에 민감정보는 없다. 원인 추적을 위해 남긴다.
            log.warn("시세 조회 실패 market={}", properties.exchangeMarket(), e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "시세를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (tickers == null || tickers.isEmpty() || tickers.get(0).tradePrice() == null) {
            log.warn("시세 응답이 비어 있음 market={}", properties.exchangeMarket());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "시세를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }

        BigDecimal tradePrice = tickers.get(0).tradePrice();
        if (tradePrice.signum() <= 0) {
            // 0이나 음수 시세로 나눗셈을 하면 ArithmeticException 또는 음수 수량이 나온다.
            log.warn("시세가 0 이하 market={} price={}", properties.exchangeMarket(), tradePrice);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "시세를 조회할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
        return tradePrice;
    }

    private static final org.springframework.core.ParameterizedTypeReference<List<Ticker>> TICKER_LIST =
            new org.springframework.core.ParameterizedTypeReference<>() {
            };

    /** Upbit {@code /v1/ticker} 응답 중 필요한 필드만 받는다. */
    record Ticker(
            @com.fasterxml.jackson.annotation.JsonProperty("market") String market,
            @com.fasterxml.jackson.annotation.JsonProperty("trade_price") BigDecimal tradePrice
    ) {
    }

    private record CachedRate(String symbol, BigDecimal rate, long fetchedAtMillis) {

        boolean isFresh(String requestedSymbol, long nowMillis, long ttlMillis) {
            return symbol.equals(requestedSymbol) && nowMillis - fetchedAtMillis < ttlMillis;
        }
    }
}
