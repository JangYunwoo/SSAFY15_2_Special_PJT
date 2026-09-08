package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 지원 디지털 자산 (CRYP-01, FR-PAY-27·32).
 *
 * <p>컬럼 구조가 EVM 전제다. {@code token_contract}가 NOT NULL이고 주소 길이가 42자라
 * ERC-20 토큰만 표현할 수 있다. BTC 같은 비EVM 자산을 넣으려면 스키마부터 손봐야 한다.
 *
 * <p>필요 컨펌 수를 자산별로 두는 이유는 체인마다 안전 컨펌 수가 다르기 때문이다.
 * 결제 건에는 이 값을 스냅샷으로 복사해 두어, 나중에 정책을 바꿔도 진행 중인
 * 결제의 검증 기준이 흔들리지 않게 한다.
 */
@Entity
@Table(name = "crypto_assets")
public class CryptoAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_id")
    private Long assetId;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "chain_name", nullable = false, length = 30)
    private String chainName;

    @Column(name = "token_contract", nullable = false, length = 42)
    private String tokenContract;

    @Column(nullable = false)
    private int decimals;

    @Column(name = "required_confirmations", nullable = false)
    private int requiredConfirmations;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CryptoAsset() {
    }

    /**
     * 사람이 읽는 수량을 온체인 uint256 원본값으로 바꾼다.
     *
     * <p>소수 이하가 {@code decimals}를 넘으면 <b>버린다</b>. 올림하면 견적보다 많은
     * 금액을 요구하게 되고, 그 차이는 구매자가 MetaMask에서 보게 되는 금액과
     * 어긋난다. 잘라낸 만큼의 미달은 언더페이 판정에서 걸러진다.
     *
     * @throws ArithmeticException decimals 자리로 정확히 표현할 수 없는 경우
     */
    public BigInteger toRawAmount(BigDecimal assetAmount) {
        return assetAmount
                .setScale(decimals, RoundingMode.DOWN)
                .movePointRight(decimals)
                .toBigIntegerExact();
    }

    /** 온체인 uint256 원본값을 사람이 읽는 수량으로 되돌린다. */
    public BigDecimal toAssetAmount(BigInteger rawAmount) {
        return new BigDecimal(rawAmount).movePointLeft(decimals);
    }

    /** 온체인 주소는 대소문자가 섞여 오므로(EIP-55 체크섬) 비교는 항상 대소문자 무시로 한다. */
    public boolean matchesTokenContract(String contractAddress) {
        return contractAddress != null && tokenContract.equalsIgnoreCase(contractAddress);
    }

    public Long getAssetId() {
        return assetId;
    }

    public String getSymbol() {
        return symbol;
    }

    public long getChainId() {
        return chainId;
    }

    public String getChainName() {
        return chainName;
    }

    public String getTokenContract() {
        return tokenContract;
    }

    public int getDecimals() {
        return decimals;
    }

    public int getRequiredConfirmations() {
        return requiredConfirmations;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
