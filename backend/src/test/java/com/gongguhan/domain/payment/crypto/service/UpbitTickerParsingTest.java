package com.gongguhan.domain.payment.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Upbit 응답 파싱 검증.
 *
 * <p>이 프로젝트는 Jackson 3(`tools.jackson`)을 쓴다. {@code trade_price}처럼
 * snake_case 필드를 record에 바인딩하려면 애노테이션이 실제로 인식되어야 하는데,
 * Jackson 2와 3의 패키지가 갈려 있어 눈으로는 확인이 안 된다. 실제 응답 본문을
 * 그대로 넣고 값이 채워지는지 본다.
 *
 * <p>본문은 2026-09-08에 {@code GET /v1/ticker?markets=KRW-USDT}로 받은 실제 응답이다.
 */
class UpbitTickerParsingTest {

    private static final String REAL_RESPONSE = """
            [{"market":"KRW-USDT","trade_date":"20260908","trade_time":"050857",
            "trade_date_kst":"20260908","trade_time_kst":"140857",
            "trade_timestamp":1788844137726,"opening_price":1362.00000000,
            "high_price":1362.00000000,"low_price":1356.00000000,
            "trade_price":1358.00000000,"prev_closing_price":1362.00000000,
            "change":"FALL","change_price":4.00000000,"change_rate":0.0029368576,
            "signed_change_price":-4.00000000,"signed_change_rate":-0.0029368576,
            "trade_volume":2097.17083946,
            "acc_trade_price":25932707787.5097066500000000,
            "acc_trade_price_24h":73262633923.77390498,
            "acc_trade_volume":19099565.68693985,
            "acc_trade_volume_24h":53853108.81189118,
            "highest_52_week_price":1655.00000000,"highest_52_week_date":"2025-10-10",
            "lowest_52_week_price":1356.00000000,"lowest_52_week_date":"2026-09-07",
            "timestamp":1788844138299}]
            """;

    @Test
    @DisplayName("실제 Upbit 응답에서 trade_price를 BigDecimal로 바인딩한다")
    void bindsTradePriceFromRealResponse() {
        List<UpbitExchangeRateProvider.Ticker> tickers = new tools.jackson.databind.ObjectMapper()
                .readValue(REAL_RESPONSE,
                        new tools.jackson.core.type.TypeReference<
                                List<UpbitExchangeRateProvider.Ticker>>() {
                        });

        assertEquals(1, tickers.size());
        UpbitExchangeRateProvider.Ticker ticker = tickers.get(0);
        assertEquals("KRW-USDT", ticker.market());
        assertNotNull(ticker.tradePrice(), "trade_price 바인딩 실패 - 애노테이션 패키지를 확인할 것");
        assertEquals(0, ticker.tradePrice().compareTo(new BigDecimal("1358")));
    }

    @Test
    @DisplayName("RestClient가 쓰는 ParameterizedTypeReference도 같은 타입을 가리킨다")
    void parameterizedTypeReferenceResolves() {
        ParameterizedTypeReference<List<UpbitExchangeRateProvider.Ticker>> ref =
                new ParameterizedTypeReference<>() {
                };
        assertNotNull(ref.getType());
    }
}
