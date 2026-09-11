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
 *
 * <p><b>{@code rawAmount}가 송금 금액의 유일한 기준이다.</b> {@code assetAmount}는
 * 원화를 환율로 나눈 소수 18자리 원장값이라 그대로 송금할 수 없다. USDT는
 * {@code decimals}가 6이므로 18자리를 그대로 넣으면 지갑이 거부하거나 임의로
 * 잘라내고, 잘린 값이 서버 기대치와 어긋나면 언더페이로 결제가 실패한다.
 *
 * <p>서버는 {@link CryptoAsset#toRawAmount}로 {@code assetAmount}를 {@code decimals}
 * 자리에서 <b>내림</b>한 값을 기대치로 쓴다. 프론트가 같은 계산을 재현하려면 내림·올림
 * 방향까지 맞춰야 하는데 그 규칙이 API 명세에 드러나 있지 않아 실제로 어긋나기 쉽다.
 * 그래서 서버가 계산 결과를 확정해 내려준다. 프론트는 {@code rawAmount}를 ERC-20
 * {@code transfer}의 두 번째 인자에 그대로 넣고, 화면에는
 * {@code rawAmount / 10^decimals}를 보여주면 된다.
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
        String rawAmount,
        int decimals,
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
                asset.toRawAmount(quote.getAssetAmount()).toString(),
                asset.getDecimals(),
                receiveWallet.getAddress(),
                asset.getRequiredConfirmations(),
                quote.getExpiresAt().atOffset(ZoneOffset.UTC).toString(),
                quote.remainingSeconds(now));
    }
}
