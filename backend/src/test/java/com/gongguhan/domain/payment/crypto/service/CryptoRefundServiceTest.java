package com.gongguhan.domain.payment.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.domain.payment.crypto.service.CryptoRefundService.RefundTransfer;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 코인 환불 계산과 송금 검증 (S15P21B205-80).
 *
 * <p><b>정책: 원화로 보관하고, 환불 시점 환율로 그만큼의 코인을 돌려준다.</b>
 * 플랫폼은 원화 기준으로 항상 정확하고 구매자가 코인 가격 변동을 집니다.
 *
 * <p>계산이 틀리면 구매자가 받을 코인 수량이 틀립니다. 그래서 <b>반올림 방향</b>과
 * <b>Buffer 미적용</b>을 테스트로 고정합니다.
 */
class CryptoRefundServiceTest {

    private static final Long PAYMENT_ID = 900L;
    private static final Long ASSET_ID = 10L;
    private static final int DECIMALS = 6;
    private static final String SYMBOL = "USDT";
    private static final String TOKEN = "0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c";
    private static final String BUYER = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final String TX_HASH =
            "0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134";

    private CryptoPaymentRepository cryptoPaymentRepository;
    private CryptoAssetRepository cryptoAssetRepository;
    private ExchangeRateProvider exchangeRateProvider;
    private ChainTransferClient transferClient;

    private CryptoRefundService service;

    @BeforeEach
    void setUp() {
        cryptoPaymentRepository = mock(CryptoPaymentRepository.class);
        cryptoAssetRepository = mock(CryptoAssetRepository.class);
        exchangeRateProvider = mock(ExchangeRateProvider.class);
        transferClient = mock(ChainTransferClient.class);

        service = new CryptoRefundService(cryptoPaymentRepository, cryptoAssetRepository,
                exchangeRateProvider, transferClient);
    }

    // ------------------------------------------------------------------
    // 고정 객체
    // ------------------------------------------------------------------

    /**
     * 실제 변환 규칙을 그대로 재현합니다.
     *
     * <p>{@code toRawAmount} 는 {@code decimals} 자리에서 <b>내림</b>합니다. 그 규칙
     * 자체는 {@code CryptoAssetTest} 가 검증하고, 여기서는 환불이 그 결과를 그대로
     * 쓰는지를 봅니다.
     */
    private CryptoAsset usdt() {
        CryptoAsset asset = mock(CryptoAsset.class);
        when(asset.getSymbol()).thenReturn(SYMBOL);
        when(asset.getDecimals()).thenReturn(DECIMALS);
        when(asset.getTokenContract()).thenReturn(TOKEN);
        when(asset.toRawAmount(any())).thenAnswer(call ->
                ((BigDecimal) call.getArgument(0))
                        .setScale(DECIMALS, RoundingMode.DOWN)
                        .movePointRight(DECIMALS)
                        .toBigIntegerExact());
        return asset;
    }

    private CryptoPayment payment() {
        CryptoPayment payment = mock(CryptoPayment.class);
        when(payment.getAssetId()).thenReturn(ASSET_ID);
        when(payment.getFromAddress()).thenReturn(BUYER);
        return payment;
    }

    /**
     * 송금 가능한 상태로 협력 객체를 세웁니다.
     *
     * <p>스텁 객체를 {@code when(...)} 호출 <b>밖에서</b> 먼저 완성합니다. 인자 안에서
     * 다른 mock 을 스터빙하면 {@code UnfinishedStubbingException} 이 납니다.
     */
    private void given(String rate) {
        CryptoAsset asset = usdt();
        CryptoPayment payment = payment();

        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cryptoAssetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(new BigDecimal(rate));
        when(transferClient.isEnabled()).thenReturn(true);
        when(transferClient.transferToken(anyString(), anyString(), any()))
                .thenReturn(TX_HASH);
    }

    // ------------------------------------------------------------------
    // 환율 계산
    // ------------------------------------------------------------------

    /**
     * 138,000원을 1,500원 환율로 돌려주면 92 USDT 입니다. 결제 때 100 USDT 를 냈어도
     * 원화 가치 기준이라 92 가 맞습니다.
     */
    @Test
    @DisplayName("환율이 오르면 더 적은 코인을 보낸다 - 원화 가치 기준")
    void sendsFewerTokensWhenRateRises() {
        given("1500");

        RefundTransfer result = service.refundByPayment(PAYMENT_ID, 138_000L);

        assertEquals(0, new BigDecimal("92.000000").compareTo(result.assetAmount()));
        verify(transferClient).transferToken(TOKEN, BUYER, BigInteger.valueOf(92_000_000L));
    }

    @Test
    @DisplayName("환율이 내리면 더 많은 코인을 보낸다 - 플랫폼은 원화 기준으로 정확하다")
    void sendsMoreTokensWhenRateFalls() {
        given("1200");

        RefundTransfer result = service.refundByPayment(PAYMENT_ID, 138_000L);

        assertEquals(0, new BigDecimal("115.000000").compareTo(result.assetAmount()));
        verify(transferClient).transferToken(TOKEN, BUYER, BigInteger.valueOf(115_000_000L));
    }

    /**
     * 결제 때 Buffer 0.3% 를 이미 받았습니다. 환불에 또 적용하면 이중 징수입니다.
     * 시장 환율을 그대로 씁니다.
     */
    @Test
    @DisplayName("Buffer 를 적용하지 않는다 - 결제 때 이미 받았다")
    void doesNotApplyBuffer() {
        given("1380");

        RefundTransfer result = service.refundByPayment(PAYMENT_ID, 138_000L);

        // Buffer 를 적용했다면 138,000 / (1380 × 1.003) = 99.70... 이 되어야 한다.
        assertEquals(0, new BigDecimal("100.000000").compareTo(result.assetAmount()));
        assertEquals(0, new BigDecimal("1380").compareTo(result.appliedRate()));
    }

    /**
     * 올리면 보관한 원화보다 많은 코인을 내보내게 되고, 그 차이가 쌓이면 플랫폼이
     * 손실을 떠안습니다.
     */
    @Test
    @DisplayName("나누어떨어지지 않으면 내림한다 - 보관액보다 많이 내보내지 않는다")
    void roundsDownOnRemainder() {
        given("1384.26036");

        RefundTransfer result = service.refundByPayment(PAYMENT_ID, 54_000L);

        // 54,000 / 1384.26036 = 39.0100024246... -> 6자리 내림 -> 39.010002
        assertEquals(0, new BigDecimal("39.010002").compareTo(result.assetAmount()));
        verify(transferClient).transferToken(TOKEN, BUYER, BigInteger.valueOf(39_010_002L));
    }

    // ------------------------------------------------------------------
    // 송금 전 차단
    // ------------------------------------------------------------------

    /**
     * 낡은 환율로 보내면 금액이 틀어집니다. 결제 Quote 가 시세 조회 실패 시 캐시를
     * 재사용하지 않는 것과 같은 원칙입니다.
     */
    @Test
    @DisplayName("환율을 조회하지 못하면 송금하지 않는다")
    void doesNotSendWhenRateUnavailable() {
        CryptoAsset asset = usdt();
        CryptoPayment payment = payment();
        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cryptoAssetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                        "시세 조회 실패"));

        assertThrows(BusinessException.class,
                () -> service.refundByPayment(PAYMENT_ID, 138_000L));
        verify(transferClient, never()).transferToken(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("환율이 0이거나 음수면 송금하지 않는다")
    void doesNotSendOnInvalidRate() {
        CryptoAsset asset = usdt();
        CryptoPayment payment = payment();
        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cryptoAssetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(BigDecimal.ZERO);

        assertThrows(BusinessException.class,
                () -> service.refundByPayment(PAYMENT_ID, 138_000L));
        verify(transferClient, never()).transferToken(anyString(), anyString(), any());
    }

    /** 환율이 너무 높아 최소 단위에도 못 미치면 0 이 나갑니다. 보내봐야 의미가 없습니다. */
    @Test
    @DisplayName("토큰 최소 단위보다 작으면 송금하지 않는다")
    void doesNotSendDustAmount() {
        given("999999999");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refundByPayment(PAYMENT_ID, 1L));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        verify(transferClient, never()).transferToken(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("0원 이하 환불은 거부한다")
    void rejectsNonPositiveAmount() {
        given("1380");

        assertThrows(BusinessException.class, () -> service.refundByPayment(PAYMENT_ID, 0L));
        verify(transferClient, never()).transferToken(anyString(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 수취 주소
    // ------------------------------------------------------------------

    /**
     * 사용자가 입력한 주소로 보내면, 계정이 탈취됐을 때 공격자가 환불을 자기 지갑으로
     * 빼돌릴 수 있습니다. 결제 시점에 체인으로 검증된 주소가 가장 안전합니다.
     */
    @Test
    @DisplayName("구매자가 결제 때 송금한 지갑으로만 돌려보낸다")
    void sendsOnlyToOriginalPayerWallet() {
        given("1380");

        service.refundByPayment(PAYMENT_ID, 138_000L);

        verify(transferClient).transferToken(anyString(), eq(BUYER), any());
    }

    @Test
    @DisplayName("결제 건에서 구매자 지갑을 꺼낼 수 있다 - 송금 전 원장 기록용")
    void exposesBuyerWalletBeforeSending() {
        CryptoPayment payment = payment();
        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        assertEquals(BUYER, service.findBuyerWallet(PAYMENT_ID));
    }

    @Test
    @DisplayName("결제 건이 없으면 404")
    void missingPaymentIsNotFound() {
        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refundByPayment(PAYMENT_ID, 138_000L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 미매칭 입금
    // ------------------------------------------------------------------

    @Test
    @DisplayName("미매칭 입금은 보낸 지갑으로 그대로 되돌린다")
    void refundsUnmatchedDepositToSender() {
        CryptoAsset asset = usdt();
        when(cryptoAssetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(new BigDecimal("1380"));
        when(transferClient.transferToken(anyString(), anyString(), any())).thenReturn(TX_HASH);

        RefundTransfer result = service.refundUnmatchedDeposit(ASSET_ID, BUYER, 138_000L);

        assertEquals(TX_HASH, result.txHash());
        verify(transferClient).transferToken(TOKEN, BUYER, BigInteger.valueOf(100_000_000L));
    }

    @Test
    @DisplayName("수취 주소가 없으면 송금하지 않는다")
    void rejectsBlankAddress() {
        CryptoAsset asset = usdt();
        when(cryptoAssetRepository.findById(ASSET_ID)).thenReturn(Optional.of(asset));

        assertThrows(BusinessException.class,
                () -> service.refundUnmatchedDeposit(ASSET_ID, "  ", 138_000L));
        verify(transferClient, never()).transferToken(anyString(), anyString(), any());
    }

    // ------------------------------------------------------------------
    // 활성 여부
    // ------------------------------------------------------------------

    @Test
    @DisplayName("개인키가 없으면 비활성으로 보고한다 - 호출 측이 미리 막을 수 있다")
    void reportsDisabledWithoutPrivateKey() {
        when(transferClient.isEnabled()).thenReturn(false);

        assertEquals(false, service.isEnabled());
    }
}
