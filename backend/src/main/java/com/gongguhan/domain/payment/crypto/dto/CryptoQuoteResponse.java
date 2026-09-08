package com.gongguhan.domain.payment.crypto.dto;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * CRYP-02 Quote 응답 (API 명세서 6장 "디지털 자산 Quote 응답").
 *
 * <p>환율과 자산 수량은 문자열로 내보낸다(명세 1.5). JSON Number로 보내면
 * 자바스크립트 {@code Number}가 배정밀도라 소수 18자리에서 값이 뭉개진다.
 * 구매자가 MetaMask에 입력하는 수량이 1 wei라도 달라지면 언더페이로 떨어진다.
 *
 * <p>{@code remainingSeconds}는 명세 예시에 없지만 FR-PAY-25가 Quote 잔여 시간
 * 표시를 요구하므로 함께 내보낸다. 프론트가 만료 시각으로 직접 계산하면 클라이언트
 * 시계 오차가 그대로 드러난다.
 */
public record CryptoQuoteResponse(
        String quoteId,
        String paymentId,
        String asset,
        long chainId,
        String tokenContract,
        long krwAmount,
        String marketRate,
        String bufferRate,
        String appliedRate,
        String assetAmount,
        String depositAddress,
        int requiredConfirmations,
        String expiresAt,
        long remainingSeconds
) {

    public static CryptoQuoteResponse of(CryptoQuote quote, CryptoAsset asset,
                                          CryptoWallet receiveWallet, Long paymentId,
                                          LocalDateTime now) {
        return new CryptoQuoteResponse(
                String.valueOf(quote.getQuoteId()),
                String.valueOf(paymentId),
                asset.getSymbol(),
                asset.getChainId(),
                asset.getTokenContract(),
                quote.getKrwAmount(),
                quote.getMarketRate().toPlainString(),
                quote.getBufferRate().toPlainString(),
                quote.getAppliedRate().toPlainString(),
                quote.getAssetAmount().toPlainString(),
                receiveWallet.getAddress(),
                asset.getRequiredConfirmations(),
                quote.getExpiresAt().atOffset(ZoneOffset.UTC).toString(),
                quote.remainingSeconds(now));
    }
}
