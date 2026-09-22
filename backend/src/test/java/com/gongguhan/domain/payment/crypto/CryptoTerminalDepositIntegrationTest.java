package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.gongguhan.domain.payment.crypto.entity.*;
import com.gongguhan.domain.payment.crypto.repository.*;
import com.gongguhan.domain.payment.crypto.service.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.gongguhan.global.error.BusinessException;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cryptoterminal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoTerminalDepositIntegrationTest {
    @Autowired CryptoTxRecordService records;
    @Autowired CryptoPaymentRepository cryptoPayments;
    @Autowired CryptoDepositRepository deposits;
    @Autowired CryptoAssetRepository assets;
    @Autowired CryptoQuoteRepository quotes;
    @Autowired CryptoWalletRepository wallets;
    @Autowired JdbcTemplate jdbc;
    @Autowired CryptoDepositRefundService refundService;
    @MockitoBean CryptoRefundService transfer;
    @MockitoBean ExchangeRateProvider rates;
    @MockitoBean com.gongguhan.domain.groupbuy.service.GroupBuySlotGate slotGate;
    static final AtomicLong IDS=new AtomicLong(960000);
    static final String BUYER="0x1111111111111111111111111111111111111111";
    static final String RECEIVE="0x2222222222222222222222222222222222222222";

    @Test void expiredPaymentRemainsInAwaitingReceiptQueue() {
        Fixture f=fixture(false);
        expire(f.id());
        assertThat(cryptoPayments.findAwaitingReceipt(PageRequest.of(0,500))).extracting(CryptoPayment::getPaymentId).contains(f.id());
    }

    @Test void lateReceiptAfterExpirationBecomesUnmatchedDepositNotParticipation() {
        Fixture f=fixture(false);
        expire(f.id());
        records.applyLateReceipt(f.id(),receipt(f),105);
        assertThat(deposits.findByPaymentId(f.id())).hasSize(1);
        assertThat(deposits.findByPaymentId(f.id()).get(0).getStatus()).isEqualTo("UNMATCHED");
        assertThat(cryptoPayments.findById(f.id()).orElseThrow().getConfirmations()).isEqualTo(6);
        assertNoParticipation(f.id());
    }

    @Test void receiptAlreadySeenBeforeExpirationStillTracksFinalityAndRefundClassification() {
        Fixture f=fixture(true);
        expire(f.id());
        assertThat(cryptoPayments.findPendingConfirmation(PageRequest.of(0,500))).extracting(CryptoPayment::getPaymentId).contains(f.id());
        records.refreshConfirmations(f.id(),105);
        CryptoDeposit d=deposits.findByPaymentId(f.id()).get(0);
        assertThat(d.getStatus()).isEqualTo("UNMATCHED");
        assertThat(d.getConfirmations()).isZero();
        records.refreshDepositConfirmations(d.getDepositId(),receipt(f),105);
        assertThat(deposits.findById(d.getDepositId()).orElseThrow().getConfirmations()).isEqualTo(6);
        assertNoParticipation(f.id());
    }

    @Test void lateReceiptOnElapsedButUnprocessedSlotFailsOnceAndKeepsDeposit() {
        Fixture f=fixture(false);
        jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=?",LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1),f.id());
        records.applyLateReceipt(f.id(),receipt(f),105);
        records.applyLateReceipt(f.id(),receipt(f),106);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,f.id())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.id())).isZero();
        assertThat(deposits.findByPaymentId(f.id())).hasSize(1);
        assertThat(deposits.findByPaymentId(f.id()).get(0).getStatus()).isEqualTo("UNMATCHED");
    }

    @Test void lateRevertDoesNotCreateRefundableMoneyOrReopenExpiredPayment() {
        Fixture f=fixture(false);
        expire(f.id());
        records.applyLateReceipt(f.id(),new ChainClient.ReceiptView(false,100,List.of()),105);
        assertThat(cryptoPayments.findById(f.id()).orElseThrow().isReverted()).isTrue();
        assertThat(deposits.findByPaymentId(f.id())).isEmpty();
        assertNoParticipation(f.id());
    }

    @Test void mismatchedDepositCannotBeReturnedUntilFreshReceiptHasRequiredConfirmations() {
        Fixture f=fixture(false);
        long depositId=unmatched(f);
        when(transfer.isEnabled()).thenReturn(true);
        when(rates.getKrwRate(anyString())).thenReturn(new BigDecimal("1000"));
        when(transfer.refundAsReceived(anyLong(),anyString(),any())).thenReturn(
                new CryptoRefundService.RefundTransfer(String.format("0x%064x",f.id()+100000),new BigDecimal("5"),null,BUYER));
        assertThat(records.findUnconfirmedDeposits(80002,PageRequest.of(0,500))).extracting(CryptoDeposit::getDepositId).contains(depositId);
        records.refreshDepositConfirmations(depositId,receipt(f),102);
        assertThatThrownBy(() -> refundService.refund(depositId)).isInstanceOf(BusinessException.class);
        verify(transfer,never()).refundAsReceived(anyLong(),anyString(),any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE deposit_id=?",Integer.class,depositId)).isZero();
        records.refreshDepositConfirmations(depositId,receipt(f),104);
        assertThat(refundService.refund(depositId).isCompleted()).isTrue();
        verify(transfer,times(1)).refundAsReceived(anyLong(),eq(BUYER),eq(new BigInteger("5000000")));
    }

    @ParameterizedTest
    @ValueSource(strings={"missing","revert","sender","amount"})
    void absentOrDifferentReceiptCannotMatureRefund(String invalid) {
        Fixture f=fixture(false);
        long id=unmatched(f);
        ChainClient.ReceiptView observed=switch(invalid) {
            case "missing" -> null;
            case "revert" -> new ChainClient.ReceiptView(false,100,List.of());
            case "sender" -> new ChainClient.ReceiptView(true,100,List.of(new ChainClient.TransferLog(f.token(),RECEIVE,RECEIVE,new BigInteger("5000000"),0)));
            default -> new ChainClient.ReceiptView(true,100,List.of(new ChainClient.TransferLog(f.token(),BUYER,RECEIVE,new BigInteger("6000000"),0)));
        };
        records.refreshDepositConfirmations(id,observed,110);
        assertThat(deposits.findById(id).orElseThrow().getConfirmations()).isZero();
    }

    @Test void refreshedReceiptBlockPositionReplacesOldConfirmationBasis() {
        Fixture f=fixture(false);
        long id=unmatched(f);
        records.refreshDepositConfirmations(id,new ChainClient.ReceiptView(true,104,receipt(f).transfers()),105);
        CryptoDeposit observed=deposits.findById(id).orElseThrow();
        assertThat(observed.getBlockNumber()).isEqualTo(104);
        assertThat(observed.getConfirmations()).isEqualTo(2);
    }

    @Test void schedulerTracksExpiredReceiptWithMockedRpcAndNoTokenTransfer() {
        Fixture f=fixture(false);
        expire(f.id());
        ChainClient rpc=mock(ChainClient.class);
        when(rpc.getLatestBlockNumber()).thenReturn(105L);
        when(rpc.findReceipt(f.hash())).thenReturn(Optional.of(receipt(f)));
        var props=new CryptoProperties(80002,"unused",1000,500,"unused","QA",1000,1000);
        new CryptoConfirmationScheduler(cryptoPayments,records,rpc,props).pollConfirmations();
        assertThat(deposits.findByPaymentId(f.id())).hasSize(1);
        assertNoParticipation(f.id());
        verifyNoInteractions(transfer);
    }

    private long unmatched(Fixture f) {
        CryptoDeposit d=CryptoDeposit.detected(80002,f.hash(),0,100,f.token(),BUYER,RECEIVE,
                new BigInteger("5000000"),new BigDecimal("5"),0,LocalDateTime.now(ZoneOffset.UTC));
        d.matchTo(null,CryptoDeposit.STATUS_UNDERPAID,"QA",LocalDateTime.now(ZoneOffset.UTC));
        return deposits.save(d).getDepositId();
    }

    private void expire(long id) { jdbc.update("UPDATE payments SET status='EXPIRED',slot_marker=NULL WHERE payment_id=?",id); }
    private void assertNoParticipation(long id) {
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM escrows WHERE group_buy_id=?",Integer.class,id)).isZero();
    }
    private ChainClient.ReceiptView receipt(Fixture f) {
        return new ChainClient.ReceiptView(true,100,List.of(new ChainClient.TransferLog(f.token(),BUYER,RECEIVE,new BigInteger("5000000"),0)));
    }
    private Fixture fixture(boolean knownReceipt) {
        long id=IDS.incrementAndGet();
        String token=String.format("0x%040x",id);
        String hash=String.format("0x%064x",id);
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,'QA',80002,'QA',?,6,5,TRUE,NOW())",id,token);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(?,80002,?,'RECEIVE',TRUE,NOW())",id,RECEIVE);
        jdbc.update("INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,asset_amount,issued_at,expires_at,used) VALUES(?,?,1000,0,1000,5000,5,?,?,FALSE)",id,id,now,now.plusMinutes(10));
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,5,5000,0,0,1,?,?,'RECRUITING',0,NOW(),NOW())",id,id,id,now.minusDays(1),now.plusDays(1));
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,'CRYPTO',5000,?,'Y',?,'PROCESSING',NOW(),NOW())",id,id,id,now.plusMinutes(10),UUID.randomUUID().toString());
        Fixture f=new Fixture(id,token,hash);
        records.record(id,quotes.findById(id).orElseThrow(),assets.findById(id).orElseThrow(),wallets.findById(id).orElseThrow(),BUYER,hash,knownReceipt?receipt(f):null,100);
        return f;
    }
    record Fixture(long id,String token,String hash) {}
}
