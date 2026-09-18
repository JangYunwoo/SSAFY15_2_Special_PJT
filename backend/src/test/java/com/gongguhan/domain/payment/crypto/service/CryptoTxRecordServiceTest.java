package com.gongguhan.domain.payment.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 영수증 검증과 기록 검증.
 *
 * <p>여기서 확인하는 것은 <b>남의 트랜잭션이나 잘못된 금액이 결제로 통과하지 않는지</b>다.
 * 프론트가 보낸 txHash 를 그대로 믿으면 해시 복사만으로 결제 완료를 받아낼 수 있다.
 */
class CryptoTxRecordServiceTest {

    private static final Long PAYMENT_ID = 900L;
    private static final Long USER_ID = 1L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long ADDRESS_ID = 501L;
    private static final Long QUOTE_ID = 11001L;
    private static final Long ASSET_ID = 10L;
    private static final Long WALLET_ID = 1L;
    private static final long CHAIN_ID = 80002L;
    private static final long KRW_AMOUNT = 54_000L;
    private static final int DECIMALS = 6;
    private static final int REQUIRED_CONFIRMATIONS = 5;

    private static final String TOKEN = "0x2222222222222222222222222222222222222222";
    private static final String RECEIVE_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0xaaaa000000000000000000000000000000000001";
    private static final String OTHER_WALLET = "0xbbbb000000000000000000000000000000000002";
    private static final String TX_HASH =
            "0xabc0000000000000000000000000000000000000000000000000000000000001";

    /** 39.674638 USDT (decimals 6) = 39674638 raw */
    private static final BigDecimal ASSET_AMOUNT = new BigDecimal("39.674638");
    private static final BigInteger RAW_AMOUNT = new BigInteger("39674638");

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDateTime NOW_LDT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final long TX_BLOCK = 1_000L;

    private CryptoPaymentRepository cryptoPaymentRepository;
    private CryptoDepositRepository cryptoDepositRepository;
    private CryptoDepositRecorder depositRecorder;
    private CryptoQuoteRepository cryptoQuoteRepository;
    private PaymentRepository paymentRepository;
    private UserAddressRepository userAddressRepository;
    private PaymentService paymentService;
    private GroupBuySlotPort groupBuySlotPort;
    private CryptoTxRecordService service;

    @BeforeEach
    void setUp() {
        cryptoPaymentRepository = mock(CryptoPaymentRepository.class);
        // save() 는 넘긴 인스턴스를 그대로 돌려주도록 둔다.
        //
        // 실제 JpaRepository 는 payment_id 가 직접 지정된 엔티티에 merge() 를 호출해
        // "다른" 관리 객체를 돌려주지만, mock 에 그 의미론을 재현하면 테스트가
        // 프로덕션 코드의 버그를 감추는 방향으로 기울어진다. merge 의미론에 대한
        // 회귀 방지는 CryptoTxPersistenceIntegrationTest 가 실제 DB 로 담당한다.
        // 여기서는 반환값을 쓰지 않아 null 이 되는 것만 막는다.
        when(cryptoPaymentRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        cryptoDepositRepository = mock(CryptoDepositRepository.class);
        depositRecorder = mock(CryptoDepositRecorder.class);
        cryptoQuoteRepository = mock(CryptoQuoteRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        userAddressRepository = mock(UserAddressRepository.class);
        paymentService = mock(PaymentService.class);
        groupBuySlotPort = mock(GroupBuySlotPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new CryptoTxRecordService(cryptoPaymentRepository, cryptoDepositRepository,
                depositRecorder, cryptoQuoteRepository, paymentRepository, userAddressRepository,
                paymentService, groupBuySlotPort, clock);
    }

    // ------------------------------------------------------------------
    // 픽스처
    // ------------------------------------------------------------------

    private Payment processingPayment(long slotTtlSeconds) {
        return Payment.prepare(GROUP_BUY_ID, USER_ID, ADDRESS_ID, "CRYPTO", KRW_AMOUNT,
                NOW_LDT.plusSeconds(slotTtlSeconds), "idem-1");
    }

    private CryptoAsset usdt() {
        CryptoAsset asset = mock(CryptoAsset.class);
        when(asset.getAssetId()).thenReturn(ASSET_ID);
        when(asset.getChainId()).thenReturn(CHAIN_ID);
        when(asset.getTokenContract()).thenReturn(TOKEN);
        when(asset.getDecimals()).thenReturn(DECIMALS);
        when(asset.getRequiredConfirmations()).thenReturn(REQUIRED_CONFIRMATIONS);
        when(asset.toRawAmount(ASSET_AMOUNT)).thenReturn(RAW_AMOUNT);
        return asset;
    }

    private CryptoQuote quote() {
        CryptoQuote quote = mock(CryptoQuote.class);
        when(quote.getQuoteId()).thenReturn(QUOTE_ID);
        when(quote.getAssetId()).thenReturn(ASSET_ID);
        when(quote.getAssetAmount()).thenReturn(ASSET_AMOUNT);
        return quote;
    }

    private CryptoWallet wallet() {
        CryptoWallet wallet = mock(CryptoWallet.class);
        when(wallet.getWalletId()).thenReturn(WALLET_ID);
        when(wallet.getAddress()).thenReturn(RECEIVE_WALLET);
        return wallet;
    }

    private UserAddress address() {
        UserAddress address = mock(UserAddress.class);
        when(address.getRecipientName()).thenReturn("받는사람");
        when(address.getRecipientPhone()).thenReturn("010-1111-2222");
        when(address.getZipcode()).thenReturn("06236");
        when(address.getAddress1()).thenReturn("서울 강남구 테헤란로 1");
        when(address.getAddress2()).thenReturn("101호");
        return address;
    }

    /** 정상 입금 1건이 담긴 성공 영수증. */
    private ChainClient.ReceiptView successReceipt(String from, String to, BigInteger amount,
                                                    String token) {
        return new ChainClient.ReceiptView(true, TX_BLOCK,
                List.of(new ChainClient.TransferLog(token, from, to, amount, 0)));
    }

    /** 협력 객체를 정상 경로로 세운다. Quote claim 성공. */
    private void givenRecordable(Payment payment) {
        UserAddress addr = address();
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(cryptoQuoteRepository.claim(eq(QUOTE_ID), any(LocalDateTime.class))).thenReturn(1);
        when(userAddressRepository.findByAddressIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(addr));
    }

    private CryptoPayment record(Payment payment, ChainClient.ReceiptView receipt,
                                  long latestBlock) {
        return service.record(PAYMENT_ID, quote(), usdt(), wallet(),
                BUYER_WALLET, TX_HASH, receipt, latestBlock);
    }

    // ------------------------------------------------------------------
    // 정상 경로
    // ------------------------------------------------------------------

    @Test
    @DisplayName("금액·주소·토큰이 모두 일치하면 기록하고 입금을 MATCHED 로 남긴다")
    void recordsMatchedDeposit() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        CryptoPayment result = record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK);

        assertEquals(CryptoPayment.RECEIPT_SUCCESS, result.getReceiptStatus());
        assertEquals(TX_BLOCK, result.getBlockNumber());
        verify(cryptoPaymentRepository).save(result);

        var captor = org.mockito.ArgumentCaptor.forClass(CryptoDeposit.class);
        verify(cryptoDepositRepository).save(captor.capture());
        assertEquals(CryptoDeposit.STATUS_MATCHED, captor.getValue().getStatus());
        assertEquals(RAW_AMOUNT, captor.getValue().rawAmountValue());
    }

    @Test
    @DisplayName("컨펌이 아직 부족하면 결제를 확정하지 않는다")
    void doesNotCompleteBeforeRequiredConfirmations() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        // 최신 블록 == 트랜잭션 블록 -> 컨펌 1개. 필요 5개에 못 미친다.
        CryptoPayment result = record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK);

        assertEquals(1, result.getConfirmations());
        verify(paymentService, never()).completePayment(anyLong(), any());
    }

    @Test
    @DisplayName("제출 시점에 컨펌이 이미 충족돼 있으면 즉시 확정한다")
    void completesWhenAlreadyConfirmed() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        // 블록이 4개 더 쌓여 컨펌 5개
        CryptoPayment result = record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK + 4);

        assertEquals(5, result.getConfirmations());
        assertTrue(result.isConfirmed());
        verify(paymentService).completePayment(eq(PAYMENT_ID), any());
    }

    @Test
    @DisplayName("노드가 트랜잭션을 아직 모르면 영수증 없이 등록하고 폴링에 넘긴다")
    void registersWithoutReceiptWhenNodeUnaware() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        CryptoPayment result = record(payment, null, TX_BLOCK);

        assertEquals(null, result.getReceiptStatus());
        verify(cryptoPaymentRepository).save(result);
        verify(cryptoDepositRepository, never()).save(any());
        verify(paymentService, never()).completePayment(anyLong(), any());
    }

    // ------------------------------------------------------------------
    // 검증 실패 — 여기가 핵심
    // ------------------------------------------------------------------

    @Test
    @DisplayName("우리 수취 지갑으로 온 입금이 없으면 거부한다")
    void rejectsTransferToOtherWallet() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, OTHER_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.CRYPTO_AMOUNT_MISMATCH, e.getErrorCode());
        verify(cryptoDepositRepository, never()).save(any());
        verify(paymentService, never()).completePayment(anyLong(), any());
    }

    @Test
    @DisplayName("다른 토큰 컨트랙트의 전송은 거부한다")
    void rejectsDifferentToken() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        String fakeToken = "0x9999999999999999999999999999999999999999";

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, fakeToken), TX_BLOCK));

        assertEquals(ErrorCode.CRYPTO_AMOUNT_MISMATCH, e.getErrorCode());
    }

    @Test
    @DisplayName("신고한 지갑이 아닌 곳에서 온 입금은 거부한다 - 남의 트랜잭션 도용 차단")
    void rejectsTransferFromOtherSender() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(OTHER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.CRYPTO_AMOUNT_MISMATCH, e.getErrorCode());
    }

    @Test
    @DisplayName("입금액이 1 wei 라도 적으면 거부한다")
    void rejectsUnderpayment() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        BigInteger short1 = RAW_AMOUNT.subtract(BigInteger.ONE);

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, short1, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.CRYPTO_AMOUNT_MISMATCH, e.getErrorCode());
        assertTrue(e.getMessage().contains("적습니다"), "미달 사유가 드러나야 한다: " + e.getMessage());
    }

    @Test
    @DisplayName("입금액이 더 많아도 견적과 다르면 거부한다")
    void rejectsOverpayment() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        BigInteger extra = RAW_AMOUNT.add(BigInteger.valueOf(1_000));

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, extra, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.CRYPTO_AMOUNT_MISMATCH, e.getErrorCode());
    }

    /**
     * 결제는 거부되지만 돈은 이미 우리 지갑에 들어와 있습니다. 여기서 남기지 않으면
     * 누구에게 돌려줘야 하는지 알 수 없습니다(FR-PAY-26).
     *
     * <p>record 는 @Transactional 이라 예외를 던지면 롤백됩니다. 그래서 기록은 별도 빈의
     * REQUIRES_NEW 트랜잭션이 맡습니다 - 이 테스트는 그 빈이 호출되는지만 봅니다.
     */
    @Test
    @DisplayName("미달 입금도 관리자 환불 대상으로 기록한다")
    void recordsUnderpaidDepositBeforeRejecting() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        BigInteger short1 = RAW_AMOUNT.subtract(BigInteger.ONE);

        assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, short1, TOKEN), TX_BLOCK));

        verify(depositRecorder).recordMismatched(any(), any(), anyLong(),
                eq(CryptoDeposit.STATUS_UNDERPAID));
    }

    @Test
    @DisplayName("초과 입금도 관리자 환불 대상으로 기록한다")
    void recordsOverpaidDepositBeforeRejecting() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        BigInteger extra = RAW_AMOUNT.add(BigInteger.valueOf(1_000));

        assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, extra, TOKEN), TX_BLOCK));

        verify(depositRecorder).recordMismatched(any(), any(), anyLong(),
                eq(CryptoDeposit.STATUS_OVERPAID));
    }

    @Test
    @DisplayName("트랜잭션이 revert 되면 결제를 실패시키고 슬롯을 놓아준다")
    void failsPaymentAndReleasesSlotOnRevert() {
        Payment payment = processingPayment(600);
        givenRecordable(payment);
        ChainClient.ReceiptView reverted = new ChainClient.ReceiptView(false, TX_BLOCK, List.of());

        CryptoPayment result = record(payment, reverted, TX_BLOCK);

        assertEquals(CryptoPayment.RECEIPT_REVERTED, result.getReceiptStatus());
        assertEquals(Payment.STATUS_FAILED, payment.getStatus());
        // 슬롯을 놓지 않으면 그 자리가 영구히 막힌다.
        verify(groupBuySlotPort).release(GROUP_BUY_ID);
        verify(cryptoDepositRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Quote 중복 사용 방지 (배정된 동시성 과제)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Quote claim 에 실패하면 아무것도 기록하지 않는다")
    void rejectsWhenQuoteClaimFails() {
        Payment payment = processingPayment(600);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        // 다른 요청이 먼저 claim 했거나 만료됨
        when(cryptoQuoteRepository.claim(eq(QUOTE_ID), any(LocalDateTime.class))).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.QUOTE_EXPIRED, e.getErrorCode());
        verify(cryptoPaymentRepository, never()).save(any());
        verify(cryptoDepositRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 종료된 결제에는 기록하지 않는다")
    void rejectsFinalizedPayment() {
        Payment payment = processingPayment(600);
        payment.markFailed("USER_ABORTED");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        BusinessException e = assertThrows(BusinessException.class, () -> record(payment,
                successReceipt(BUYER_WALLET, RECEIVE_WALLET, RAW_AMOUNT, TOKEN), TX_BLOCK));

        assertEquals(ErrorCode.PAYMENT_ALREADY_PROCESSED, e.getErrorCode());
        // claim 조차 하지 않아야 한다. 견적을 낭비시키면 안 된다.
        verify(cryptoQuoteRepository, never()).claim(anyLong(), any());
    }

    // ------------------------------------------------------------------
    // 컨펌 폴링
    // ------------------------------------------------------------------

    @Test
    @DisplayName("폴링에서 컨펌이 충족되면 결제를 확정한다")
    void completesViaPolling() {
        Payment payment = processingPayment(600);
        CryptoPayment cryptoPayment = CryptoPayment.submit(
                PAYMENT_ID, usdt(), quote(), wallet(), BUYER_WALLET, TX_HASH);
        cryptoPayment.applyReceipt(true, TX_BLOCK);

        UserAddress addr = address();
        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(cryptoPayment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(userAddressRepository.findByAddressIdAndUserId(ADDRESS_ID, USER_ID))
                .thenReturn(Optional.of(addr));

        boolean completed = service.refreshConfirmations(PAYMENT_ID, TX_BLOCK + 4);

        assertTrue(completed);
        assertEquals(5, cryptoPayment.getConfirmations());
        verify(paymentService).completePayment(eq(PAYMENT_ID), any());
    }

    @Test
    @DisplayName("슬롯 만료 후 컨펌이 도달하면 확정하지 않고 실패시킨다")
    void failsWhenSlotExpiredBeforeConfirmation() {
        // 슬롯이 이미 과거에 만료된 결제
        Payment payment = Payment.prepare(GROUP_BUY_ID, USER_ID, ADDRESS_ID, "CRYPTO",
                KRW_AMOUNT, NOW_LDT.minusSeconds(1), "idem-expired");
        CryptoPayment cryptoPayment = CryptoPayment.submit(
                PAYMENT_ID, usdt(), quote(), wallet(), BUYER_WALLET, TX_HASH);
        cryptoPayment.applyReceipt(true, TX_BLOCK);

        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(cryptoPayment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        boolean completed = service.refreshConfirmations(PAYMENT_ID, TX_BLOCK + 10);

        // 확정하면 자리는 남에게 넘어갔는데 참여 건이 생겨 인원이 초과된다.
        assertTrue(!completed);
        assertEquals(Payment.STATUS_FAILED, payment.getStatus());
        verify(groupBuySlotPort).release(GROUP_BUY_ID);
        verify(paymentService, never()).completePayment(anyLong(), any());
    }

    @Test
    @DisplayName("이미 확정된 결제는 폴링이 건드리지 않는다")
    void pollingSkipsCompletedPayment() {
        Payment payment = processingPayment(600);
        payment.markPaid(777L, NOW_LDT);
        CryptoPayment cryptoPayment = CryptoPayment.submit(
                PAYMENT_ID, usdt(), quote(), wallet(), BUYER_WALLET, TX_HASH);
        cryptoPayment.applyReceipt(true, TX_BLOCK);

        when(cryptoPaymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(cryptoPayment));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));

        boolean completed = service.refreshConfirmations(PAYMENT_ID, TX_BLOCK + 100);

        assertTrue(!completed);
        verify(paymentService, never()).completePayment(anyLong(), any());
    }
}
