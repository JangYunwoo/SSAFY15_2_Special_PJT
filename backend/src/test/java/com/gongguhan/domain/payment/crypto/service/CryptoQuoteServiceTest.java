package com.gongguhan.domain.payment.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteResponse;
import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CryptoQuoteServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long PAYMENT_ID = 900L;
    private static final Long ASSET_ID = 10L;
    private static final long CHAIN_ID = 80002L;
    private static final long KRW_AMOUNT = 54_000L;
    private static final String RECEIVE_ADDRESS = "0x1111111111111111111111111111111111111111";

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime NOW_LDT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private CryptoAssetRepository cryptoAssetRepository;
    private CryptoWalletRepository cryptoWalletRepository;
    private CryptoQuoteRepository cryptoQuoteRepository;
    private PaymentRepository paymentRepository;
    private ExchangeRateProvider exchangeRateProvider;
    private PolicyService policyService;
    private CryptoQuoteService cryptoQuoteService;

    @BeforeEach
    void setUp() {
        cryptoAssetRepository = mock(CryptoAssetRepository.class);
        cryptoWalletRepository = mock(CryptoWalletRepository.class);
        cryptoQuoteRepository = mock(CryptoQuoteRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        exchangeRateProvider = mock(ExchangeRateProvider.class);
        policyService = mock(PolicyService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        cryptoQuoteService = new CryptoQuoteService(
                cryptoAssetRepository, cryptoWalletRepository, cryptoQuoteRepository,
                paymentRepository, exchangeRateProvider, policyService, clock);
    }

    // ------------------------------------------------------------------
    // 픽스처
    // ------------------------------------------------------------------

    /** 슬롯이 {@code slotTtlSeconds}초 뒤 만료되는 코인 결제 건. */
    private Payment cryptoPayment(long slotTtlSeconds) {
        return Payment.prepare(100L, USER_ID, "CRYPTO", KRW_AMOUNT,
                NOW_LDT.plusSeconds(slotTtlSeconds), "idem-crypto-1");
    }

    private CryptoAsset usdt() {
        CryptoAsset asset = mock(CryptoAsset.class);
        when(asset.getAssetId()).thenReturn(ASSET_ID);
        when(asset.getSymbol()).thenReturn("USDT");
        when(asset.getChainId()).thenReturn(CHAIN_ID);
        when(asset.getTokenContract()).thenReturn("0x2222222222222222222222222222222222222222");
        when(asset.getRequiredConfirmations()).thenReturn(5);
        return asset;
    }

    private CryptoWallet receiveWallet(boolean placeholder) {
        CryptoWallet wallet = mock(CryptoWallet.class);
        when(wallet.isPlaceholder()).thenReturn(placeholder);
        if (!placeholder) {
            when(wallet.getAddress()).thenReturn(RECEIVE_ADDRESS);
        }
        return wallet;
    }

    /**
     * 정상 발급이 되도록 협력 객체를 모두 세운다.
     *
     * <p>스텁 객체를 {@code when(...)} 호출 <b>밖에서</b> 먼저 완성한다.
     * {@code when(repo.find()).thenReturn(Optional.of(usdt()))}처럼 인자 안에서
     * 다른 mock을 스터빙하면 스터빙이 중첩되어 {@code UnfinishedStubbingException}이 난다.
     */
    private void givenIssuableContext(Payment payment, long quoteTtlSeconds) {
        CryptoAsset asset = usdt();
        CryptoWallet wallet = receiveWallet(false);

        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(payment));
        when(cryptoAssetRepository.findByAssetIdAndEnabledTrue(ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(cryptoWalletRepository.findFirstByChainIdAndPurposeAndActiveTrue(
                CHAIN_ID, CryptoWallet.PURPOSE_RECEIVE))
                .thenReturn(Optional.of(wallet));
        when(exchangeRateProvider.getKrwRate("USDT")).thenReturn(new BigDecimal("1380.12"));
        when(policyService.getRate(PolicyKey.CRYPTO_BUFFER_RATE)).thenReturn(new BigDecimal("0.003"));
        when(policyService.getSeconds(PolicyKey.QUOTE_TTL_SEC))
                .thenReturn(Duration.ofSeconds(quoteTtlSeconds));
    }

    private CryptoQuoteRequest request() {
        return new CryptoQuoteRequest(ASSET_ID);
    }

    // ------------------------------------------------------------------
    // 정상 발급
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Quote를 발급하고 수취 주소와 필요 컨펌 수를 함께 응답한다")
    void issuesQuoteWithDepositAddress() {
        givenIssuableContext(cryptoPayment(600), 600);

        CryptoQuoteResponse response = cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request());

        assertEquals("USDT", response.asset());
        assertEquals(CHAIN_ID, response.chainId());
        assertEquals(KRW_AMOUNT, response.krwAmount());
        assertEquals("1384.26036000", response.appliedRate());
        assertEquals(RECEIVE_ADDRESS, response.depositAddress());
        assertEquals(5, response.requiredConfirmations());
        verify(cryptoQuoteRepository).save(any(CryptoQuote.class));
    }

    @Test
    @DisplayName("환율과 수량은 문자열로 응답한다 - JSON Number는 소수 18자리를 못 담는다")
    void ratesAreSerializedAsStrings() {
        givenIssuableContext(cryptoPayment(600), 600);

        CryptoQuoteResponse response = cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request());

        assertEquals("0.0030", response.bufferRate());
        assertTrue(response.assetAmount().contains("."), "수량은 소수 문자열이어야 한다");
        assertEquals(18, response.assetAmount().split("\\.")[1].length());
    }

    // ------------------------------------------------------------------
    // 만료 시각 결정 (FR-PAY-24)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("슬롯이 정책 TTL보다 먼저 끝나면 Quote도 슬롯과 함께 만료된다")
    void quoteNeverOutlivesSlot() {
        // 슬롯 잔여 120초, 정책 TTL 600초 -> Quote 만료는 120초 뒤여야 한다.
        Payment payment = cryptoPayment(120);
        givenIssuableContext(payment, 600);

        CryptoQuoteResponse response = cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request());

        assertEquals(120, response.remainingSeconds());
        assertEquals(payment.getSlotExpiresAt().atOffset(ZoneOffset.UTC).toString(),
                response.expiresAt());
    }

    @Test
    @DisplayName("슬롯이 정책 TTL보다 길게 남았으면 정책 TTL을 쓴다")
    void usesPolicyTtlWhenSlotOutlivesIt() {
        givenIssuableContext(cryptoPayment(1_800), 600);

        CryptoQuoteResponse response = cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request());

        assertEquals(600, response.remainingSeconds());
    }

    // ------------------------------------------------------------------
    // 거부 조건
    // ------------------------------------------------------------------

    @Test
    @DisplayName("본인 결제 건이 아니면 거부한다")
    void rejectsOtherUsersPayment() {
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, OTHER_USER_ID))
                .thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(OTHER_USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, e.getErrorCode());
        verify(cryptoQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("코인 결제 건이 아니면 거부한다")
    void rejectsNonCryptoPayment() {
        Payment cardPayment = Payment.prepare(100L, USER_ID, "CARD", KRW_AMOUNT,
                NOW_LDT.plusSeconds(300), "idem-card-1");
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(cardPayment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.VALIDATION_ERROR, e.getErrorCode());
        verify(cryptoQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 종료된 결제 건에는 발급하지 않는다")
    void rejectsFinalizedPayment() {
        Payment payment = cryptoPayment(600);
        payment.markFailed("USER_ABORTED");
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(payment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, e.getErrorCode());
    }

    @Test
    @DisplayName("슬롯이 만료된 결제 건에는 발급하지 않는다")
    void rejectsExpiredSlot() {
        // 슬롯 만료가 이미 과거인 결제 건
        Payment payment = Payment.prepare(100L, USER_ID, "CRYPTO", KRW_AMOUNT,
                NOW_LDT.minusSeconds(1), "idem-crypto-expired");
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(payment));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.PAYMENT_SLOT_EXPIRED, e.getErrorCode());
        verify(cryptoQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 자산으로는 발급하지 않는다")
    void rejectsDisabledAsset() {
        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(cryptoPayment(600)));
        when(cryptoAssetRepository.findByAssetIdAndEnabledTrue(ASSET_ID))
                .thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("수취 지갑이 아직 플레이스홀더 주소면 발급을 막는다")
    void rejectsPlaceholderReceiveWallet() {
        CryptoAsset asset = usdt();
        CryptoWallet placeholderWallet = receiveWallet(true);

        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(cryptoPayment(600)));
        when(cryptoAssetRepository.findByAssetIdAndEnabledTrue(ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(cryptoWalletRepository.findFirstByChainIdAndPurposeAndActiveTrue(
                CHAIN_ID, CryptoWallet.PURPOSE_RECEIVE))
                .thenReturn(Optional.of(placeholderWallet));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        // 영주소로 송금하면 자금이 영구히 사라지므로 500으로 막는다.
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, e.getErrorCode());
        verify(cryptoQuoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("수취 지갑이 없으면 발급을 막는다")
    void rejectsMissingReceiveWallet() {
        CryptoAsset asset = usdt();

        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(cryptoPayment(600)));
        when(cryptoAssetRepository.findByAssetIdAndEnabledTrue(ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(cryptoWalletRepository.findFirstByChainIdAndPurposeAndActiveTrue(
                CHAIN_ID, CryptoWallet.PURPOSE_RECEIVE))
                .thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, e.getErrorCode());
    }

    @Test
    @DisplayName("시세 조회가 실패하면 견적을 내지 않는다 - 낡은 환율로 발급하지 않는다")
    void rejectsWhenExchangeRateUnavailable() {
        CryptoAsset asset = usdt();
        CryptoWallet wallet = receiveWallet(false);

        when(paymentRepository.findByPaymentIdAndUserId(PAYMENT_ID, USER_ID))
                .thenReturn(Optional.of(cryptoPayment(600)));
        when(cryptoAssetRepository.findByAssetIdAndEnabledTrue(ASSET_ID))
                .thenReturn(Optional.of(asset));
        when(cryptoWalletRepository.findFirstByChainIdAndPurposeAndActiveTrue(
                CHAIN_ID, CryptoWallet.PURPOSE_RECEIVE))
                .thenReturn(Optional.of(wallet));
        when(exchangeRateProvider.getKrwRate("USDT"))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));

        BusinessException e = assertThrows(BusinessException.class,
                () -> cryptoQuoteService.issueQuote(USER_ID, PAYMENT_ID, request()));

        assertEquals(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, e.getErrorCode());
        verify(cryptoQuoteRepository, never()).save(any());
    }
}
