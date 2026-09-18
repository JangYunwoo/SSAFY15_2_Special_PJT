package com.gongguhan.domain.payment.crypto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.domain.refund.service.RefundLedgerService.OpenResult;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 결제에 붙지 못한 입금의 환불 (S15P21B205-80, FR-PAY-26).
 *
 * <p><b>핵심 정책: 받은 수량 그대로 돌려준다.</b> 참여 취소 환불과 달리 이 돈은 Quote 로
 * 원화 가치가 확정된 적이 없습니다. 환율로 환산하면 동의한 적 없는 변동을 보낸 사람에게
 * 지우게 되므로, 들어온 최소 단위 수량을 그대로 되돌립니다.
 */
class CryptoDepositRefundServiceTest {

    private static final Long DEPOSIT_ID = 77L;
    private static final Long ASSET_ID = 10L;
    private static final Long REFUND_ID = 500L;
    private static final long CHAIN_ID = 80002L;
    private static final String SYMBOL = "USDT";
    private static final String TOKEN = "0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c";
    private static final String SENDER = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final String RAW_AMOUNT = "5792354";
    private static final BigDecimal ASSET_AMOUNT = new BigDecimal("5.792354");
    private static final String TX_HASH =
            "0x13ce84b68bbe75a66fe8a3f6fde680b19402781cb5e29d19d3b53d4bff0f88d1";

    private CryptoDepositRepository cryptoDepositRepository;
    private CryptoAssetRepository cryptoAssetRepository;
    private CryptoRefundService cryptoRefundService;
    private CryptoTxRecordService recordService;
    private RefundLedgerService ledgerService;
    private ExchangeRateProvider exchangeRateProvider;

    private CryptoDepositRefundService service;

    @BeforeEach
    void setUp() {
        cryptoDepositRepository = mock(CryptoDepositRepository.class);
        cryptoAssetRepository = mock(CryptoAssetRepository.class);
        cryptoRefundService = mock(CryptoRefundService.class);
        recordService = mock(CryptoTxRecordService.class);
        ledgerService = mock(RefundLedgerService.class);
        exchangeRateProvider = mock(ExchangeRateProvider.class);

        service = new CryptoDepositRefundService(cryptoDepositRepository, cryptoAssetRepository,
                cryptoRefundService, recordService, ledgerService, exchangeRateProvider);
    }

    // ------------------------------------------------------------------
    // 고정 객체
    // ------------------------------------------------------------------

    private CryptoDeposit deposit(String status) {
        CryptoDeposit deposit = mock(CryptoDeposit.class);
        when(deposit.getDepositId()).thenReturn(DEPOSIT_ID);
        when(deposit.getStatus()).thenReturn(status);
        when(deposit.getChainId()).thenReturn(CHAIN_ID);
        when(deposit.getTokenContract()).thenReturn(TOKEN);
        when(deposit.getFromAddress()).thenReturn(SENDER);
        when(deposit.getRawAmount()).thenReturn(RAW_AMOUNT);
        when(deposit.getAssetAmount()).thenReturn(ASSET_AMOUNT);
        return deposit;
    }

    private CryptoAsset asset() {
        CryptoAsset asset = mock(CryptoAsset.class);
        when(asset.getAssetId()).thenReturn(ASSET_ID);
        when(asset.getSymbol()).thenReturn(SYMBOL);
        return asset;
    }

    private Refund refundRow() {
        Refund refund = mock(Refund.class);
        when(refund.getRefundId()).thenReturn(REFUND_ID);
        return refund;
    }

    /** 성공 경로의 공통 스터빙. 중첩 스터빙을 피하려고 mock 을 먼저 만들어 둡니다. */
    private CryptoDeposit givenRefundableDeposit(String status) {
        CryptoDeposit deposit = deposit(status);
        CryptoAsset asset = asset();
        Refund opened = refundRow();
        when(cryptoDepositRepository.findById(DEPOSIT_ID)).thenReturn(Optional.of(deposit));
        when(cryptoAssetRepository.findByChainIdAndTokenContractIgnoreCase(CHAIN_ID, TOKEN))
                .thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(new BigDecimal("1377"));
        when(ledgerService.openForUnmatchedDeposit(eq(DEPOSIT_ID), anyLong(), eq(SENDER)))
                .thenReturn(new OpenResult(opened, true));
        when(cryptoRefundService.isEnabled()).thenReturn(true);
        return deposit;
    }

    // ------------------------------------------------------------------
    // 금액 정책
    // ------------------------------------------------------------------

    /**
     * 이 돈은 Quote 로 원화 가치가 확정된 적이 없습니다. 환율로 환산해 보내면 동의한 적
     * 없는 변동을 보낸 사람에게 지우게 됩니다.
     */
    @Test
    @DisplayName("들어온 수량 그대로 돌려보낸다 - 환율로 환산하지 않는다")
    void sendsBackExactlyWhatWasReceived() {
        givenRefundableDeposit(CryptoDeposit.STATUS_UNMATCHED);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                TX_HASH, ASSET_AMOUNT, null, SENDER);
        when(cryptoRefundService.refundAsReceived(eq(ASSET_ID), eq(SENDER), any()))
                .thenReturn(transfer);
        Refund completed = refundRow();
        when(ledgerService.completeByCrypto(eq(REFUND_ID), eq(TX_HASH), any()))
                .thenReturn(completed);

        service.refund(DEPOSIT_ID);

        ArgumentCaptor<BigInteger> sent = ArgumentCaptor.forClass(BigInteger.class);
        verify(cryptoRefundService).refundAsReceived(eq(ASSET_ID), eq(SENDER), sent.capture());
        assertEquals(new BigInteger(RAW_AMOUNT), sent.getValue());
    }

    /**
     * refunds.amount 는 원화 단위라 비워둘 수 없습니다. 실제 송금액과는 무관한 참고값이며,
     * 시세를 못 가져오면 지어내지 않고 실패시킵니다.
     */
    @Test
    @DisplayName("원장에는 환불 시점 환산액을 참고값으로 기록한다")
    void recordsKrwReferenceInLedger() {
        givenRefundableDeposit(CryptoDeposit.STATUS_UNMATCHED);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                TX_HASH, ASSET_AMOUNT, null, SENDER);
        when(cryptoRefundService.refundAsReceived(eq(ASSET_ID), eq(SENDER), any()))
                .thenReturn(transfer);
        Refund completed = refundRow();
        when(ledgerService.completeByCrypto(eq(REFUND_ID), eq(TX_HASH), any()))
                .thenReturn(completed);

        service.refund(DEPOSIT_ID);

        // 5.792354 x 1377 = 7976.07... -> 원 단위 버림 7976
        verify(ledgerService).openForUnmatchedDeposit(DEPOSIT_ID, 7976L, SENDER);
    }

    @Test
    @DisplayName("시세를 못 가져오면 기록을 지어내지 않고 실패시킨다")
    void failsWhenRateUnavailable() {
        CryptoDeposit deposit = deposit(CryptoDeposit.STATUS_UNMATCHED);
        CryptoAsset asset = asset();
        when(cryptoDepositRepository.findById(DEPOSIT_ID)).thenReturn(Optional.of(deposit));
        when(cryptoAssetRepository.findByChainIdAndTokenContractIgnoreCase(CHAIN_ID, TOKEN))
                .thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refund(DEPOSIT_ID));

        assertEquals(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, exception.getErrorCode());
        verify(ledgerService, never()).openForUnmatchedDeposit(anyLong(), anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // 이중 송금 방지
    // ------------------------------------------------------------------

    /**
     * 개시는 멱등이라 같은 요청이 두 번 오면 기존 행이 돌아옵니다. 그걸 구분하지 않으면
     * 원장은 한 건인데 토큰이 두 번 나갑니다.
     */
    @Test
    @DisplayName("이미 진행 중인 환불이면 송금하지 않는다")
    void doesNotSendWhenRefundAlreadyOpen() {
        CryptoDeposit deposit = deposit(CryptoDeposit.STATUS_UNMATCHED);
        CryptoAsset asset = asset();
        Refund existing = refundRow();
        when(cryptoDepositRepository.findById(DEPOSIT_ID)).thenReturn(Optional.of(deposit));
        when(cryptoAssetRepository.findByChainIdAndTokenContractIgnoreCase(CHAIN_ID, TOKEN))
                .thenReturn(Optional.of(asset));
        when(exchangeRateProvider.getKrwRate(SYMBOL)).thenReturn(new BigDecimal("1377"));
        when(ledgerService.openForUnmatchedDeposit(eq(DEPOSIT_ID), anyLong(), eq(SENDER)))
                .thenReturn(new OpenResult(existing, false));

        service.refund(DEPOSIT_ID);

        verify(cryptoRefundService, never()).refundAsReceived(anyLong(), anyString(), any());
    }

    /**
     * 결제에 붙은 입금은 구매자가 대가를 받은 돈입니다. 돌려보내면 물건도 주고 돈도
     * 돌려주는 셈이 됩니다.
     */
    @Test
    @DisplayName("결제에 붙은 입금은 돌려보낼 수 없다")
    void rejectsMatchedDeposit() {
        CryptoDeposit matched = deposit(CryptoDeposit.STATUS_MATCHED);
        when(cryptoDepositRepository.findById(DEPOSIT_ID)).thenReturn(Optional.of(matched));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refund(DEPOSIT_ID));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        verify(ledgerService, never()).openForUnmatchedDeposit(anyLong(), anyLong(), anyString());
    }

    // ------------------------------------------------------------------
    // 실패 처리
    // ------------------------------------------------------------------

    /**
     * 송금이 이미 나갔을 수 있어 자동 재시도하면 두 번 지급됩니다. 재처리 전에 체인에서
     * 확인이 필요하다는 것을 사유에 남깁니다.
     */
    @Test
    @DisplayName("송금 결과를 모르면 확인이 필요하다는 사유로 실패시킨다")
    void marksUnknownResultForManualCheck() {
        givenRefundableDeposit(CryptoDeposit.STATUS_UNDERPAID);
        when(cryptoRefundService.refundAsReceived(eq(ASSET_ID), eq(SENDER), any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, "타임아웃"));
        Refund failed = refundRow();
        when(ledgerService.fail(eq(REFUND_ID), anyString())).thenReturn(failed);

        service.refund(DEPOSIT_ID);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(ledgerService).fail(eq(REFUND_ID), reason.capture());
        assertEquals(true, reason.getValue().contains("체인에서 확인"));
        verify(recordService, never()).markDepositRefunded(anyLong());
    }

    @Test
    @DisplayName("환불 지갑 개인키가 없으면 송금하지 않는다")
    void failsWhenRefundWalletDisabled() {
        givenRefundableDeposit(CryptoDeposit.STATUS_OVERPAID);
        when(cryptoRefundService.isEnabled()).thenReturn(false);
        Refund failed = refundRow();
        when(ledgerService.fail(eq(REFUND_ID), anyString())).thenReturn(failed);

        service.refund(DEPOSIT_ID);

        verify(cryptoRefundService, never()).refundAsReceived(anyLong(), anyString(), any());
    }

    /** 돈이 나갔으면 입금도 종료 상태가 돼야 목록에 다시 뜨지 않습니다. */
    @Test
    @DisplayName("송금이 끝나면 입금을 환불 완료로 표시한다")
    void marksDepositRefundedAfterTransfer() {
        givenRefundableDeposit(CryptoDeposit.STATUS_UNMATCHED);
        CryptoRefundService.RefundTransfer transfer = new CryptoRefundService.RefundTransfer(
                TX_HASH, ASSET_AMOUNT, null, SENDER);
        when(cryptoRefundService.refundAsReceived(eq(ASSET_ID), eq(SENDER), any()))
                .thenReturn(transfer);
        Refund completed = refundRow();
        when(ledgerService.completeByCrypto(eq(REFUND_ID), eq(TX_HASH), any()))
                .thenReturn(completed);

        service.refund(DEPOSIT_ID);

        verify(recordService).markDepositRefunded(DEPOSIT_ID);
    }

    // ------------------------------------------------------------------
    // 목록
    // ------------------------------------------------------------------

    @Test
    @DisplayName("돌려보낼 수 있는 상태만 목록에 담는다")
    void listsOnlyRefundableStatuses() {
        when(cryptoDepositRepository.findByStatusInOrderByDetectedAtAsc(any()))
                .thenReturn(List.of());

        service.findRefundable();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> statuses = ArgumentCaptor.forClass(List.class);
        verify(cryptoDepositRepository).findByStatusInOrderByDetectedAtAsc(statuses.capture());
        assertEquals(List.of(CryptoDeposit.STATUS_UNMATCHED, CryptoDeposit.STATUS_UNDERPAID,
                CryptoDeposit.STATUS_OVERPAID), statuses.getValue());
    }
}
