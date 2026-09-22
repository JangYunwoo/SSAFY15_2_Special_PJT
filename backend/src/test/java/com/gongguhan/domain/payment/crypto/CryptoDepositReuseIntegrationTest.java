package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.crypto.entity.*;
import com.gongguhan.domain.payment.crypto.repository.*;
import com.gongguhan.domain.payment.crypto.service.*;
import com.gongguhan.domain.payment.crypto.dto.*;
import com.gongguhan.global.error.BusinessException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cryptodepositreuse;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoDepositReuseIntegrationTest {
    @Autowired CryptoTxRecordService records;
    @Autowired CryptoWalletProofService proofs;
    @Autowired CryptoTxVerificationService verification;
    @Autowired CryptoAssetRepository assets;
    @Autowired CryptoQuoteRepository quotes;
    @Autowired CryptoWalletRepository wallets;
    @Autowired CryptoDepositRepository deposits;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate slotGate;
    @MockitoBean ChainClient chain;
    static final AtomicLong IDS=new AtomicLong(995000);
    static final ECKeyPair TEST_KEY=ECKeyPair.create(BigInteger.ONE); // 무자금 시험용 공개 고정 키
    static final String BUYER="0x"+Keys.getAddress(TEST_KEY);
    static final String RECEIVE="0x2222222222222222222222222222222222222222";

    @BeforeEach void productionUniqueConstraints() {
        // Hibernate create-drop에는 Flyway의 unique key가 없으므로 동일 제약을 시험 DB에 명시한다.
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS qa_crypto_payment_tx ON crypto_payments(tx_hash)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS qa_crypto_deposit_log ON crypto_deposits(tx_hash,log_index)");
    }

    @ParameterizedTest @ValueSource(strings={"UNDERPAID","OVERPAID","REFUNDED"})
    void returnedOrMismatchedDepositCannotFundNewPayment(String status) {
        long assetId=IDS.incrementAndGet();
        String token=String.format("0x%040x",assetId);
        String hash=String.format("0x%064x",assetId);
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,'QA',80002,'QA',?,6,5,TRUE,NOW())",assetId,token);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(?,80002,?,'RECEIVE',TRUE,NOW())",assetId,RECEIVE);
        var receipt=new ChainClient.ReceiptView(true,100,List.of(new ChainClient.TransferLog(token,BUYER,RECEIVE,new BigInteger("5000000"),0)));
        long first=payment(assetId,"OVERPAID".equals(status)?3:7);
        assertThatThrownBy(()->record(first,assetId,hash,receipt)).isInstanceOfSatisfying(BusinessException.class,
                error->assertThat(error.getErrorCode().name()).isEqualTo("CRYPTO_AMOUNT_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_payments WHERE tx_hash=?",Integer.class,hash)).isZero();
        CryptoDeposit deposit=deposits.findByTxHashAndLogIndex(hash,0).orElseThrow();
        if("REFUNDED".equals(status)) { records.markDepositRefunded(deposit.getDepositId()); }
        assertThat(deposits.findById(deposit.getDepositId()).orElseThrow().getStatus()).isEqualTo(status);

        long second=payment(assetId,5);
        assertThatThrownBy(()->record(second,assetId,hash,receipt))
                .as("입금 원장이 반환 대상이거나 반환 완료이면 새 결제에 쓸 수 없다")
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,second)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?",Integer.class,second)).isZero();
        assertThat(deposits.findById(deposit.getDepositId()).orElseThrow().getStatus()).isEqualTo(status);
    }

    private void record(long paymentId,long assetId,String hash,ChainClient.ReceiptView receipt) {
        var challenge=proofs.issue(paymentId,paymentId,new CryptoWalletChallengeRequest(paymentId,BUYER,hash));
        var signed=Sign.signPrefixedMessage(challenge.message().getBytes(StandardCharsets.UTF_8),TEST_KEY);
        String signature="0x"+Numeric.toHexStringNoPrefix(signed.getR())+Numeric.toHexStringNoPrefix(signed.getS())+Numeric.toHexStringNoPrefix(signed.getV());
        when(chain.findReceipt(hash)).thenReturn(Optional.of(receipt));
        when(chain.getLatestBlockNumber()).thenReturn(105L);
        verification.submitTransaction(paymentId,paymentId,new CryptoTxSubmitRequest(paymentId,hash,BUYER,challenge.challengeId(),signature));
    }

    @ParameterizedTest @ValueSource(strings={"DETECTED","UNMATCHED","FOREIGN_MATCH","CHAIN","TOKEN","FROM","TO","AMOUNT","SAME_MATCH"})
    void existingDepositMustBelongToExactlyTheSameAcceptedTransfer(String variant) {
        AssetFixture a=assetFixture();
        long paymentId=payment(a.id(),5);
        String other="0x3333333333333333333333333333333333333333";
        CryptoDeposit d=CryptoDeposit.detected("CHAIN".equals(variant)?80003:80002,a.hash(),0,100,
                "TOKEN".equals(variant)?other:a.token(),"FROM".equals(variant)?other:BUYER,
                "TO".equals(variant)?other:RECEIVE,new BigInteger("AMOUNT".equals(variant)?"6000000":"5000000"),
                new java.math.BigDecimal("5"),6,LocalDateTime.now(clock));
        if(!"DETECTED".equals(variant)) {
            d.matchTo("FOREIGN_MATCH".equals(variant)?paymentId+10000:paymentId,
                    "UNMATCHED".equals(variant)?"UNMATCHED":"MATCHED","QA",LocalDateTime.now(clock));
        }
        long depositId=deposits.save(d).getDepositId();
        if("SAME_MATCH".equals(variant)) {
            record(paymentId,a.id(),a.hash(),a.receipt());
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,paymentId)).isEqualTo("PAID");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_deposits WHERE tx_hash=?",Integer.class,a.hash())).isEqualTo(1);
        } else {
            assertThatThrownBy(()->record(paymentId,a.id(),a.hash(),a.receipt()))
                    .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.getErrorCode().name()).isEqualTo("CRYPTO_AMOUNT_MISMATCH"));
            assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,paymentId)).isEqualTo("PROCESSING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?",Integer.class,paymentId)).isZero();
        }
        assertThat(deposits.findById(depositId).orElseThrow().getStatus()).isEqualTo(d.getStatus());
    }

    @Test void concurrentNewPaymentsCannotConsumeOneRefundableDeposit() throws Exception {
        AssetFixture a=assetFixture();
        long first=payment(a.id(),5),second=payment(a.id(),5);
        CryptoDeposit d=CryptoDeposit.detected(80002,a.hash(),0,100,a.token(),BUYER,RECEIVE,
                new BigInteger("5000000"),new java.math.BigDecimal("5"),6,LocalDateTime.now(clock));
        d.matchTo(null,"UNDERPAID","QA",LocalDateTime.now(clock));
        deposits.save(d);
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<Boolean>> attempts=new java.util.ArrayList<>();
            for(long id: new long[]{first,second}) {
                attempts.add(pool.submit(()->{
                    start.await();
                    try {
                        records.record(id,quotes.findById(id).orElseThrow(),assets.findById(a.id()).orElseThrow(),
                                wallets.findById(a.id()).orElseThrow(),BUYER,a.hash(),a.receipt(),105);
                        return true;
                    } catch(BusinessException rejected) { return false; }
                }));
            }
            start.countDown();
            for(var attempt:attempts) { assertThat(attempt.get(10,java.util.concurrent.TimeUnit.SECONDS)).isFalse(); }
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_payments WHERE tx_hash=?",Integer.class,a.hash())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id IN (?,?)",Integer.class,first,second)).isZero();
        assertThat(deposits.findByTxHashAndLogIndex(a.hash(),0).orElseThrow().getStatus()).isEqualTo("UNDERPAID");
    }

    private AssetFixture assetFixture() {
        long id=IDS.incrementAndGet();
        String token=String.format("0x%040x",id),hash=String.format("0x%064x",id);
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) VALUES(?,'QA',80002,'QA',?,6,5,TRUE,NOW())",id,token);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(?,80002,?,'RECEIVE',TRUE,NOW())",id,RECEIVE);
        return new AssetFixture(id,token,hash,new ChainClient.ReceiptView(true,100,
                List.of(new ChainClient.TransferLog(token,BUYER,RECEIVE,new BigInteger("5000000"),0))));
    }
    record AssetFixture(long id,String token,String hash,ChainClient.ReceiptView receipt) { }
    private long payment(long assetId,int tokenAmount) {
        long id=IDS.incrementAndGet();
        long krw=tokenAmount*1000L;
        var now=LocalDateTime.now(clock);
        jdbc.update("INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,asset_amount,issued_at,expires_at,used) VALUES(?,?,1000,0,1000,?,?,?,?,FALSE)",id,assetId,krw,tokenAmount,now,now.plusMinutes(10));
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,5,?,0,0,1,?,?,'RECRUITING',0,NOW(),NOW())",id,id,id,krw,now.minusDays(1),now.plusDays(1));
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,shipping_recipient_name,shipping_recipient_phone,shipping_zipcode,shipping_address1,created_at,updated_at) VALUES(?,?,?,'CRYPTO',?,?,'Y',?,'PROCESSING','QA','01000000000','00000','QA',NOW(),NOW())",id,id,id,krw,now.plusMinutes(10),UUID.randomUUID().toString());
        jdbc.update("UPDATE payments SET prepare_crypto_asset_id=? WHERE payment_id=?",assetId,id);
        return id;
    }
}
