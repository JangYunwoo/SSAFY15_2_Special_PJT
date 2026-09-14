package com.gongguhan.domain.payment.crypto.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 사람이 읽는 수량 <-> 온체인 uint256 변환 검증.
 *
 * <p><b>이 변환이 구매자가 실제로 보낼 금액을 결정한다.</b> 결과가 1 단위라도
 * 어긋나면 언더페이로 판정돼 결제가 실패하고, 반대로 크면 구매자가 더 낸다.
 * 그래서 별도로 검증한다.
 *
 * <p>Quote 응답이 {@code rawAmount} 를 직접 내려주게 되면서 이 값이 API 로 노출됐다.
 * 프론트가 스스로 계산하지 않고 이 결과를 그대로 쓰도록 하는 것이 설계 의도다.
 *
 * <p>엔티티에 공개 생성자가 없어 필드를 리플렉션으로 채운다. 테스트 편의를 위해
 * 프로덕션 코드에 생성자를 열지 않기 위한 선택이다.
 */
class CryptoAssetTest {

    private static final int DECIMALS = 6;

    private CryptoAsset asset;

    @BeforeEach
    void setUp() {
        asset = new CryptoAsset();
        ReflectionTestUtils.setField(asset, "decimals", DECIMALS);
        ReflectionTestUtils.setField(asset, "tokenContract",
                "0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c");
    }

    @Test
    @DisplayName("소수 자리가 정확히 맞으면 그대로 환산한다")
    void convertsExactAmount() {
        assertEquals(new BigInteger("39528990"),
                asset.toRawAmount(new BigDecimal("39.528990")));
    }

    @Test
    @DisplayName("정수 수량도 decimals 만큼 자리를 올린다")
    void convertsWholeAmount() {
        assertEquals(new BigInteger("1000000"), asset.toRawAmount(new BigDecimal("1")));
    }

    /**
     * Quote 는 원화를 환율로 나눠 소수 18자리로 저장한다. USDT 는 6자리이므로
     * 남는 12자리는 반드시 버려야 한다.
     */
    @Test
    @DisplayName("decimals 를 넘는 소수 자리는 버린다 - Quote 는 18자리로 저장된다")
    void truncatesExcessDecimals() {
        assertEquals(new BigInteger("39528990"),
                asset.toRawAmount(new BigDecimal("39.528990123456789012")));
    }

    /**
     * 올림이면 견적보다 많은 금액을 요구하게 되고, 그 금액은 구매자가 지갑에서 보는
     * 숫자와 어긋난다. 반올림이 아니라 내림이어야 한다.
     */
    @Test
    @DisplayName("절반을 넘는 자리도 올리지 않는다 - 반올림이 아니라 내림이다")
    void neverRoundsUp() {
        assertEquals(new BigInteger("39528990"),
                asset.toRawAmount(new BigDecimal("39.5289909999")));
        assertEquals(BigInteger.ZERO, asset.toRawAmount(new BigDecimal("0.0000009")));
    }

    @Test
    @DisplayName("뒤에 붙은 0 은 자리수에 영향을 주지 않는다")
    void ignoresTrailingZeros() {
        assertEquals(new BigInteger("39528990"),
                asset.toRawAmount(new BigDecimal("39.52899000000")));
    }

    @Test
    @DisplayName("온체인 값을 사람이 읽는 수량으로 되돌린다")
    void convertsBack() {
        assertEquals(0, new BigDecimal("39.528990")
                .compareTo(asset.toAssetAmount(new BigInteger("39528990"))));
    }

    /** 왕복 변환은 내림이 일어난 만큼만 줄어들고, 그 이상 어긋나지 않아야 한다. */
    @Test
    @DisplayName("환산 후 되돌리면 버려진 자리만큼만 줄어든다")
    void roundTripLosesOnlyTruncatedDigits() {
        BigDecimal quoted = new BigDecimal("39.528990999999999999");
        BigDecimal back = asset.toAssetAmount(asset.toRawAmount(quoted));

        assertEquals(0, new BigDecimal("39.528990").compareTo(back));
        // 손실은 1 단위(0.000001) 미만이어야 한다.
        assertEquals(-1, quoted.subtract(back).compareTo(new BigDecimal("0.000001")));
    }

    @Test
    @DisplayName("토큰 컨트랙트 비교는 대소문자를 무시한다 - EIP-55 체크섬 주소가 섞여 온다")
    void matchesTokenContractIgnoringCase() {
        assertTrue(asset.matchesTokenContract("0x2DAe8b4F165B56e8bdEC4cB0bC0d8f19c7466a7c"));
        assertFalse(asset.matchesTokenContract("0x0000000000000000000000000000000000000000"));
    }
}
