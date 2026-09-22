package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;

import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.domain.payment.crypto.service.ChainClient;
import com.gongguhan.domain.payment.crypto.service.CryptoTxRecordService;
import com.gongguhan.global.error.BusinessException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * §7 수정 검증: 정상 금액(MATCHED) 코인 입금이 {@code CryptoTxRecordService.record}/{@code applyLateReceipt}의
 * 같은 트랜잭션에서 기록된 뒤, 슬롯은 살아 있으나 공구가 종료(CANCELED/모집종료)됐을 때 확정이
 * 롤백되어 <b>입금 관측이 사라지지 않고</b> 보존된 채 반환 후보(UNMATCHED)로 재분류되는지 검증한다.
 *
 * <p>수정 전에는 completePayment가 GROUP_BUY_NOT_RECRUITING로 트랜잭션 전체를 롤백해 MATCHED 입금
 * 행까지 사라졌다. 수정 후에는 예외 없이 입금을 보존하고 UNMATCHED로 분류해 관리자 반환 경로에 올린다.
 * 대조: 미달(UNDERPAID) 입금은 recordMismatched가 REQUIRES_NEW라 원래도 롤백에서 살아남는다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cryptodeprollback;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoDepositTerminationRollbackReproductionTest {

    @Autowired CryptoTxRecordService records;
    @Autowired CryptoDepositRepository deposits;
    @Autowired CryptoAssetRepository assets;
    @Autowired CryptoQuoteRepository quotes;
    @Autowired CryptoWalletRepository wallets;
    @Autowired com.gongguhan.domain.payment.crypto.service.CryptoDepositRefundService depositRefunds;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean com.gongguhan.domain.groupbuy.service.GroupBuySlotGate slotGate;

    static final AtomicLong IDS = new AtomicLong(965000);
    static final String BUYER = "0x1111111111111111111111111111111111111111";
    static final String RECEIVE = "0x2222222222222222222222222222222222222222";

    record Fixture(long id, String token, String hash) {}

    Fixture seed() {
        long id = IDS.incrementAndGet();
        String token = String.format("0x%040x", id);
        String hash = String.format("0x%064x", id);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) "
                + "VALUES(?,?,'test-only','QA',FALSE,FALSE,'ACTIVE',NOW(),NOW())", id, "crypto-" + id + "@example.test");
        jdbc.update("INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at) "
                + "VALUES(?,'QA',80002,'QA',?,6,5,TRUE,NOW())", id, token);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) "
                + "VALUES(?,80002,?,'RECEIVE',TRUE,NOW())", id, RECEIVE);
        jdbc.update("INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,asset_amount,issued_at,expires_at,used) "
                + "VALUES(?,?,1000,0,1000,5000,5,?,?,FALSE)", id, id, now, now.plusMinutes(10));
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) "
                + "VALUES(?,?,?,5,5000,0,0,1,?,?,'RECRUITING',0,NOW(),NOW())", id, id, id, now.minusDays(1), now.plusDays(1));
        // completePayment는 storedSnapshot을 confirm보다 먼저 요구한다 - 배송 스냅샷을 직접 채워
        // 종료 시 throw가 GROUP_BUY_NOT_RECRUITING이 되도록 한다(스냅샷 누락으로 새지 않게).
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,"
                + "shipping_recipient_name,shipping_recipient_phone,shipping_zipcode,shipping_address1,shipping_address2,shipping_delivery_request,created_at,updated_at) "
                + "VALUES(?,?,?,'CRYPTO',5000,?,'Y',?,'PROCESSING','QA','01000000000','00000','QA','QA','QA',NOW(),NOW())",
                id, id, id, now.plusMinutes(10), UUID.randomUUID().toString());
        return new Fixture(id, token, hash);
    }

    ChainClient.ReceiptView matched(Fixture f) {
        return new ChainClient.ReceiptView(true, 100,
                List.of(new ChainClient.TransferLog(f.token(), BUYER, RECEIVE, new BigInteger("5000000"), 0)));
    }

    ChainClient.ReceiptView underpaid(Fixture f) {
        return new ChainClient.ReceiptView(true, 100,
                List.of(new ChainClient.TransferLog(f.token(), BUYER, RECEIVE, new BigInteger("4000000"), 0)));
    }

    void terminate(long id, String state) {
        jdbc.update("UPDATE group_buys SET status=?,reserved_slots=0 WHERE group_buy_id=?", state, id);
    }

    long depositCountByPayment(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM crypto_deposits WHERE payment_id=?", Long.class, id);
    }

    long depositCountByHash(String hash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM crypto_deposits WHERE tx_hash=?", Long.class, hash);
    }

    String paymentStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?", String.class, id);
    }

    long participationCount(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=?", Long.class, id);
    }

    CryptoQuote quote(long id) {
        return quotes.findById(id).orElseThrow();
    }

    void record(Fixture f, ChainClient.ReceiptView receipt, long latestBlock) {
        records.record(f.id, quote(f.id), assets.findById(f.id).orElseThrow(),
                wallets.findById(f.id).orElseThrow(), BUYER, f.hash, receipt, latestBlock);
    }

    /** 정상 대조군: 공구 RECRUITING 유지 + 충분한 컨펌 → PAID + MATCHED 입금 1 + 참여 1. */
    @Test
    void positiveControlCompletesWithMatchedDeposit() {
        Fixture f = seed();
        record(f, matched(f), 105); // 컨펌 6 >= 필요 5
        assertThat(paymentStatus(f.id)).isEqualTo("PAID");
        assertThat(depositCountByPayment(f.id)).isEqualTo(1);
        assertThat(participationCount(f.id)).isEqualTo(1);
    }

    String depositStatusByPayment(long id) {
        return jdbc.queryForObject("SELECT status FROM crypto_deposits WHERE payment_id=?", String.class, id);
    }

    /** 수정(record 경로): 확정 순간 공구가 종료돼도 예외 없이 입금을 보존하고 UNMATCHED로 재분류한다. */
    @Test
    void recordPathPreservesMatchedDepositAsRefundCandidateWhenGroupTerminates() {
        Fixture f = seed();
        terminate(f.id, "CANCELED");
        record(f, matched(f), 105); // 예외 없이 정상 반환
        System.out.printf("AUDIT crypto-record terminal=CANCELED deposit=%d status=%s payment=%s participations=%d%n",
                depositCountByPayment(f.id), depositStatusByPayment(f.id), paymentStatus(f.id), participationCount(f.id));
        assertThat(depositCountByPayment(f.id)).as("온체인 입금 관측이 보존된다").isEqualTo(1);
        assertThat(depositStatusByPayment(f.id)).as("반환 후보로 재분류").isEqualTo("UNMATCHED");
        assertThat(paymentStatus(f.id)).isEqualTo("FAILED");
        assertThat(participationCount(f.id)).isZero();
        // 보존·재분류된 입금이 관리자 반환 경로(findRefundable)에 올라온다.
        assertThat(depositRefunds.findRefundable()).anyMatch(d -> d.getPaymentId() != null && d.getPaymentId() == f.id);
    }

    /** 수정(applyLateReceipt 경로): 폴링 완료 시점에 공구가 종료돼도 입금을 보존·재분류한다. */
    @Test
    void lateReceiptPathPreservesMatchedDepositWhenGroupTerminates() {
        Fixture f = seed();
        record(f, null, 100); // 영수증 미확인 등록(입금 없음, PROCESSING)
        terminate(f.id, "CANCELED");
        records.applyLateReceipt(f.id, matched(f), 105); // 예외 없이 정상 반환
        System.out.printf("AUDIT crypto-lateReceipt terminal=CANCELED deposit=%d status=%s payment=%s participations=%d%n",
                depositCountByPayment(f.id), depositStatusByPayment(f.id), paymentStatus(f.id), participationCount(f.id));
        assertThat(depositCountByPayment(f.id)).isEqualTo(1);
        assertThat(depositStatusByPayment(f.id)).isEqualTo("UNMATCHED");
        assertThat(paymentStatus(f.id)).isEqualTo("FAILED");
        assertThat(participationCount(f.id)).isZero();
    }

    /** 대조(비대칭 증거): 미달 입금은 recordMismatched(REQUIRES_NEW)라 record 롤백에서도 살아남는다. */
    @Test
    void underpaidDepositSurvivesRollbackUnlikeMatched() {
        Fixture f = seed();
        assertThatThrownBy(() -> record(f, underpaid(f), 105))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("CRYPTO_AMOUNT_MISMATCH"));
        String status = jdbc.queryForObject("SELECT status FROM crypto_deposits WHERE tx_hash=?", String.class, f.hash);
        System.out.printf("AUDIT crypto-underpaid survivesRollback deposit=%d status=%s%n",
                depositCountByHash(f.hash), status);
        assertThat(depositCountByHash(f.hash)).as("미달 입금은 REQUIRES_NEW라 롤백에서 보존된다").isEqualTo(1);
        assertThat(status).isEqualTo("UNDERPAID");
    }
}
