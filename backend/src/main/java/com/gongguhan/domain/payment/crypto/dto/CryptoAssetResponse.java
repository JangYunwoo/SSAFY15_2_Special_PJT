package com.gongguhan.domain.payment.crypto.dto;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;

/**
 * CRYP-01 지원 디지털 자산 (FR-PAY-27, 32).
 *
 * <p>{@code tokenContract}를 노출하는 이유는 프론트가 MetaMask에 토큰을 추가하고
 * ERC-20 {@code transfer}를 호출할 때 컨트랙트 주소가 필요하기 때문이다.
 * 공개 정보이므로 숨길 이유가 없다.
 */
public record CryptoAssetResponse(
        String assetId,
        String symbol,
        long chainId,
        String chainName,
        String tokenContract,
        int decimals,
        int requiredConfirmations
) {

    public static CryptoAssetResponse from(CryptoAsset asset) {
        return new CryptoAssetResponse(
                String.valueOf(asset.getAssetId()),
                asset.getSymbol(),
                asset.getChainId(),
                asset.getChainName(),
                asset.getTokenContract(),
                asset.getDecimals(),
                asset.getRequiredConfirmations());
    }
}
