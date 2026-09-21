package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.domain.payment.crypto.service.ChainClient;
import com.gongguhan.domain.payment.crypto.service.CryptoTxRecordService;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:cryptofailslot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
@Transactional
class CryptoFailureSlotIntegrationTest {
    private static final String FROM = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String TOKEN = "0xcccccccccccccccccccccccccccccccccccccccc";
    private static final String HASH = "0x" + "d".repeat(64);
    @Autowired CryptoTxRecordService records;
    @Autowired CryptoAssetRepository assets;
    @Autowired CryptoQuoteRepository quotes;
    @Autowired CryptoWalletRepository wallets;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;

    @BeforeEach void fixture() {
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("""
                INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,
                shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at)
                VALUES(9791,501,10,5,1000,0,0,1,?,?,'RECRUITING',0,NOW(),NOW())
                """, now.minusDays(1), now.plusDays(1));
        jdbc.update("""
                INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,
                slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at)
                VALUES(9991,9791,9191,9591,'CRYPTO',1000,?,'Y','crypto-failure-fixture','PROCESSING',NOW(),NOW())
                """, now.plusMinutes(10));
        jdbc.update("""
                INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at)
                VALUES(9891,'USDT',80002,'Polygon Amoy',?,6,5,TRUE,NOW())
                """, TOKEN);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(9891,80002,?,'RECEIVE',TRUE,NOW())", TO);
        jdbc.update("""
                INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,asset_amount,issued_at,expires_at,used)
                VALUES(9891,9891,1000,0,1000,1000,1,?,?,FALSE)
                """, now, now.plusMinutes(5));
    }

    @ParameterizedTest @ValueSource(strings = {"REVERT", "LATE_REVERT", "EXPIRED", "INVALID_TRANSFER"})
    void failureReleasesDatabaseAndGateExactlyOnce(String path) {
        var revert = new ChainClient.ReceiptView(false, 100, List.of());
        records.record(9991L, quotes.findById(9891L).orElseThrow(), assets.findById(9891L).orElseThrow(),
                wallets.findById(9891L).orElseThrow(), FROM, HASH,
                "REVERT".equals(path) ? revert : null, 100);
        if ("LATE_REVERT".equals(path)) records.applyLateReceipt(9991L, revert, 100);
        if ("INVALID_TRANSFER".equals(path)) records.applyLateReceipt(9991L,
                new ChainClient.ReceiptView(true, 100, List.of()), 100);
        if ("EXPIRED".equals(path)) {
            records.applyLateReceipt(9991L, new ChainClient.ReceiptView(true, 100,
                    List.of(new ChainClient.TransferLog(TOKEN, FROM, TO, BigInteger.valueOf(1000000), 0))), 100);
            em.flush(); em.clear();
            jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=9991", LocalDateTime.now(clock).minusSeconds(1));
            records.refreshConfirmations(9991L, 104);
        }
        em.flush(); em.clear();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=9991", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=9791", Integer.class)).isZero();
        verify(gate, times(1)).release(9791L);
        records.applyLateReceipt(9991L, revert, 105);
        records.refreshConfirmations(9991L, 105);
        verify(gate, times(1)).release(9791L);
    }
}
