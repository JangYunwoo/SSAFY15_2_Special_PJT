package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.payment.crypto.service.*;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:depositrefundguard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoDepositRefundSafetyIntegrationTest {
    @Autowired CryptoDepositRefundService service;
    @Autowired CryptoDepositRepository deposits;
    @Autowired RefundLedgerService ledger;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CryptoRefundService chain;
    @MockitoBean ExchangeRateProvider rates;
    @MockitoSpyBean CryptoTxRecordService record;
    static final AtomicLong IDS=new AtomicLong(970000);
    static final String SENDER="0x1111111111111111111111111111111111111111";
    static final String RECEIVER="0x2222222222222222222222222222222222222222";

    @BeforeEach void provider() {
        when(chain.isEnabled()).thenReturn(true);
        when(rates.getKrwRate(anyString())).thenReturn(new BigDecimal("1000"));
    }

    @Test void ambiguousResponseThenSecondAdminRequestMustNotSendAgain() {
        long id=fixture();
        when(chain.refundAsReceived(anyLong(),anyString(),any()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));
        Refund first=service.refund(id);
        Refund second=service.refund(id);
        assertThat(second.getRefundId()).isEqualTo(first.getRefundId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE deposit_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT execution_claimed FROM refunds WHERE refund_id=?",Boolean.class,first.getRefundId())).isTrue();
        assertThatThrownBy(() -> ledger.retry(first.getRefundId())).isInstanceOf(BusinessException.class);
        verify(chain,times(1)).refundAsReceived(anyLong(),eq(SENDER),eq(new BigInteger("5000000")));
    }

    @Test void unexpectedRuntimeFailureAlsoKeepsExecutionClaim() {
        long id=fixture();
        when(chain.refundAsReceived(anyLong(),anyString(),any())).thenThrow(new IllegalStateException("QA uncertain"));
        Refund first=service.refund(id);
        assertThat(service.refund(id).getRefundId()).isEqualTo(first.getRefundId());
        assertThat(first.isExecutionClaimed()).isTrue();
        verify(chain,times(1)).refundAsReceived(anyLong(),anyString(),any());
    }

    @Test void missingWalletDoesNotClaimFundsForever() {
        long id=fixture();
        when(chain.isEnabled()).thenReturn(false);
        Refund failed=service.refund(id);
        assertThat(failed.isExecutionClaimed()).isFalse();
        when(chain.isEnabled()).thenReturn(true);
        success(id);
        Refund completed=service.refund(id);
        assertThat(completed.isCompleted()).isTrue();
        verify(chain,times(1)).refundAsReceived(anyLong(),anyString(),any());
    }

    @Test void completedLedgerCanRepairDepositWithoutPricesOrAnotherTransfer() {
        long id=fixture();
        success(id);
        doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,"QA finalize fail"))
                .doCallRealMethod().when(record).markDepositRefunded(id);
        Refund completed=service.refund(id);
        assertThat(completed.isCompleted()).isTrue();
        assertThat(deposits.findById(id).orElseThrow().getStatus()).isEqualTo("UNMATCHED");
        when(rates.getKrwRate(anyString())).thenThrow(new IllegalStateException("price service unavailable"));
        assertThat(service.refund(id).getRefundId()).isEqualTo(completed.getRefundId());
        assertThat(service.refund(id).getRefundId()).isEqualTo(completed.getRefundId());
        assertThat(deposits.findById(id).orElseThrow().getStatus()).isEqualTo("REFUNDED");
        verify(chain,times(1)).refundAsReceived(anyLong(),anyString(),any());
    }

    @Test void committedButUnsentReservationCanBeResumedByAdmin() {
        long id=fixture();
        Refund pending=ledger.openForUnmatchedDeposit(id,5000,SENDER).refund();
        success(id);
        Refund completed=service.refund(id);
        assertThat(completed.getRefundId()).isEqualTo(pending.getRefundId());
        assertThat(completed.isCompleted()).isTrue();
        verify(chain,times(1)).refundAsReceived(anyLong(),anyString(),any());
    }

    @Test void concurrentAdminRequestCannotOverwriteWinner() throws Exception {
        long id=fixture();
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(chain.refundAsReceived(anyLong(),anyString(),any())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            entered.countDown();
            assertThat(release.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return transfer(id);
        });
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first=executor.submit(() -> service.refund(id));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Refund pending=service.refund(id);
            assertThat(pending.isProcessing()).isTrue();
            release.countDown();
            assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS).isCompleted()).isTrue();
        } finally { release.countDown();executor.shutdownNow(); }
        verify(chain,times(1)).refundAsReceived(anyLong(),anyString(),any());
    }

    private void success(long id) {
        when(chain.refundAsReceived(anyLong(),anyString(),any())).thenReturn(transfer(id));
    }
    private CryptoRefundService.RefundTransfer transfer(long id) {
        return new CryptoRefundService.RefundTransfer(String.format("0x%064x",id),new BigDecimal("5"),null,SENDER);
    }

    private long fixture() {
        long assetId=IDS.incrementAndGet();
        String token=String.format("0x%040x",assetId);
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,'QA',80002,'QA',?,6,5,TRUE,NOW())",assetId,token);
        CryptoDeposit deposit=CryptoDeposit.detected(80002,String.format("0x%064x",assetId),0,100,token,SENDER,RECEIVER,new BigInteger("5000000"),new BigDecimal("5"),6,LocalDateTime.now(ZoneOffset.UTC));
        deposit.markUnmatched("QA",LocalDateTime.now(ZoneOffset.UTC));
        return deposits.save(deposit).getDepositId();
    }
}
