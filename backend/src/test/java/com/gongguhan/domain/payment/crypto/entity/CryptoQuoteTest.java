package com.gongguhan.domain.payment.crypto.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quote 환율 계산 검증.
 *
 * <p>여기서 검증하는 것은 "계산식을 그대로 옮겼는지"가 아니라 <b>돈이 새지 않는지</b>다.
 * 자산 수량을 올림하므로 환산액이 원화 청구액보다 적어질 수 없어야 한다.
 */
class CryptoQuoteTest {

    private static final Long ASSET_ID = 1L;
    private static final LocalDateTime ISSUED_AT = LocalDateTime.parse("2026-01-01T00:00:00");
    private static final LocalDateTime EXPIRES_AT = LocalDateTime.parse("2026-01-01T00:10:00");

    private CryptoQuote issue(String marketRate, String bufferRate, long krwAmount) {
        return CryptoQuote.issue(ASSET_ID, new BigDecimal(marketRate), new BigDecimal(bufferRate),
                krwAmount, ISSUED_AT, EXPIRES_AT);
    }

    @Test
    @DisplayName("적용 환율은 시세에 Buffer를 가산한 값이다")
    void appliedRateAddsBuffer() {
        CryptoQuote quote = issue("1380.12", "0.003", 54_000L);

        // 1380.12 x 1.003 = 1384.26036
        assertEquals("1384.26036000", quote.getAppliedRate().toPlainString());
    }

    @Test
    @DisplayName("Buffer가 0이면 적용 환율은 시세와 같다")
    void zeroBufferKeepsMarketRate() {
        CryptoQuote quote = issue("1380.12", "0", 54_000L);

        assertEquals("1380.12000000", quote.getAppliedRate().toPlainString());
    }

    @Test
    @DisplayName("환산한 자산 수량은 원화 청구액보다 부족하지 않다")
    void assetAmountNeverUnderCoversKrw() {
        // 나누어떨어지지 않는 조합을 골라 절상 방향을 확인한다.
        CryptoQuote quote = issue("1384.26036", "0.003", 54_000L);

        BigDecimal converted = quote.getAssetAmount().multiply(quote.getAppliedRate());
        assertTrue(converted.compareTo(BigDecimal.valueOf(quote.getKrwAmount())) >= 0,
                "올림했으므로 환산액이 청구액보다 작을 수 없다. converted=" + converted.toPlainString());
    }

    @Test
    @DisplayName("컬럼 스케일에 맞춰 저장한다 - 환율 8자리, Buffer 4자리, 수량 18자리")
    void normalizesScalesToColumnDefinition() {
        CryptoQuote quote = issue("1380.1", "0.003", 54_000L);

        assertEquals(8, quote.getMarketRate().scale());
        assertEquals(4, quote.getBufferRate().scale());
        assertEquals(8, quote.getAppliedRate().scale());
        assertEquals(18, quote.getAssetAmount().scale());
        // DB에서 다시 읽어도 같은 문자열이 나와야 프론트가 견적을 비교할 수 있다.
        assertEquals("0.0030", quote.getBufferRate().toPlainString());
    }

    @Test
    @DisplayName("발급 직후에는 미사용 상태다")
    void issuedQuoteIsUnused() {
        assertFalse(issue("1380.12", "0.003", 54_000L).isUsed());
    }

    @Test
    @DisplayName("만료 시각에 도달하면 만료로 본다 - 경계 포함")
    void expiresAtBoundaryIsExpired() {
        CryptoQuote quote = issue("1380.12", "0.003", 54_000L);

        assertFalse(quote.isExpired(EXPIRES_AT.minusSeconds(1)));
        // 경계를 만료로 처리해야 "만료 직전 통과"가 생기지 않는다.
        assertTrue(quote.isExpired(EXPIRES_AT));
        assertTrue(quote.isExpired(EXPIRES_AT.plusSeconds(1)));
    }

    @Test
    @DisplayName("잔여 시간은 만료 후 음수가 되지 않는다")
    void remainingSecondsNeverNegative() {
        CryptoQuote quote = issue("1380.12", "0.003", 54_000L);

        assertEquals(600, quote.remainingSeconds(ISSUED_AT));
        assertEquals(0, quote.remainingSeconds(EXPIRES_AT));
        // 음수를 그대로 내보내면 프론트 카운트다운이 거꾸로 흐른다.
        assertEquals(0, quote.remainingSeconds(EXPIRES_AT.plusMinutes(5)));
    }
}
