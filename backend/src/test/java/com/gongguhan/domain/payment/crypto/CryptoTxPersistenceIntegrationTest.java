package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.domain.payment.crypto.service.ChainClient;
import com.gongguhan.domain.payment.crypto.service.CryptoTxRecordService;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코인 결제 기록이 <b>실제로 DB 에 저장되는지</b> 검증.
 *
 * <p><b>이 테스트가 존재하는 이유 — 실제 결제에서 발견한 버그.</b>
 *
 * <p>2026-09-11 Polygon Amoy 로 실제 USDT 를 송금해 E2E 를 돌렸을 때, API 응답은
 * {@code confirmations: 126} 을 보여줬는데 DB 에는 {@code confirmations = 0},
 * {@code confirmed_at = NULL} 이 남아 있었습니다.
 *
 * <p>원인: {@code crypto_payments} 는 {@code payment_id} 를 직접 지정하는 엔티티라
 * ({@code @GeneratedValue} 없음) Spring Data 의 {@code isNew()} 가 false 를 돌려주고,
 * {@code save()} 가 {@code persist()} 대신 {@code merge()} 를 호출합니다.
 * {@code merge()} 는 새 관리 객체를 반환하며 넘긴 인스턴스를 detached 로 남기므로,
 * 반환값을 쓰지 않으면 이후 변경이 전부 유실됩니다.
 *
 * <p><b>mock 을 쓰는 단위 테스트로는 절대 잡을 수 없습니다.</b> mock 리포지토리에는
 * merge 의미론이 없어 원본 객체가 그대로 변경되기 때문입니다. 그래서 실제 영속성
 * 컨텍스트를 쓰는 이 통합 테스트가 필요합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class CryptoTxPersistenceIntegrationTest {

    private static final Long PAYMENT_ID = 900L;
    private static final Long GROUP_BUY_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long ADDRESS_ID = 1L;
    private static final long KRW_AMOUNT = 54_000L;
    private static final long TX_BLOCK = 1_000L;

    private static final String TOKEN = "0x2dae8b4f165b56e8bdec4cb0bc0d8f19c7466a7c";
    private static final String RECEIVE = "0x557ba62ce10a53320f3b0c50fa9d211c41c6c6c7";
    private static final String BUYER = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final String TX_HASH =
            "0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134";

    /** 39.528990 USDT (decimals 6) */
    private static final BigDecimal ASSET_AMOUNT = new BigDecimal("39.528990");
    private static final BigInteger RAW_AMOUNT = new BigInteger("39528990");

    @Autowired
    private CryptoTxRecordService recordService;

    @Autowired
    private CryptoPaymentRepository cryptoPaymentRepository;

    @Autowired
    private CryptoAssetRepository cryptoAssetRepository;

    @Autowired
    private CryptoWalletRepository cryptoWalletRepository;

    @Autowired
    private CryptoQuoteRepository cryptoQuoteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private Long assetId;
    private Long walletId;
    private Long quoteId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM crypto_deposits");
        jdbcTemplate.update("DELETE FROM crypto_payments");
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM participations");
        jdbcTemplate.update("DELETE FROM payments");
        jdbcTemplate.update("DELETE FROM crypto_quotes");
        jdbcTemplate.update("DELETE FROM crypto_wallets");
        jdbcTemplate.update("DELETE FROM crypto_assets");

        jdbcTemplate.update("""
                INSERT INTO crypto_assets
                  (symbol, chain_id, chain_name, token_contract, decimals,
                   required_confirmations, enabled, created_at)
                VALUES ('USDT', 80002, 'Polygon Amoy', ?, 6, 5, TRUE, CURRENT_TIMESTAMP)
                """, TOKEN);
        assetId = cryptoAssetRepository.findAll().get(0).getAssetId();

        jdbcTemplate.update("""
                INSERT INTO crypto_wallets (chain_id, address, purpose, active, created_at)
                VALUES (80002, ?, 'RECEIVE', TRUE, CURRENT_TIMESTAMP)
                """, RECEIVE);
        walletId = cryptoWalletRepository.findAll().get(0).getWalletId();

        jdbcTemplate.update("""
                INSERT INTO crypto_quotes
                  (asset_id, market_rate, buffer_rate, applied_rate, krw_amount,
                   asset_amount, issued_at, expires_at, used)
                VALUES (?, 1366.00000000, 0.0030, 1366.08600000, ?, ?,
                        CURRENT_TIMESTAMP, DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), FALSE)
                """, assetId, KRW_AMOUNT, ASSET_AMOUNT);
        quoteId = cryptoQuoteRepository.findAll().get(0).getQuoteId();

        // 슬롯이 살아 있는 PROCESSING 결제
        jdbcTemplate.update("""
                INSERT INTO payments
                  (payment_id, group_buy_id, user_id, address_id, method, krw_amount,
                   slot_expires_at, slot_marker, idempotency_key, status,
                   created_at, updated_at)
                VALUES (?, ?, ?, ?, 'CRYPTO', ?,
                        DATEADD('MINUTE', 10, CURRENT_TIMESTAMP), 'Y', 'idem-persist-1',
                        'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PAYMENT_ID, GROUP_BUY_ID, USER_ID, ADDRESS_ID, KRW_AMOUNT);
    }

    private ChainClient.ReceiptView successReceipt() {
        return new ChainClient.ReceiptView(true, TX_BLOCK,
                List.of(new ChainClient.TransferLog(TOKEN, BUYER, RECEIVE, RAW_AMOUNT, 2)));
    }

    /**
     * 컨펌 수가 부족한 상태로 기록한 뒤 DB 를 다시 읽습니다.
     *
     * <p>결제를 확정시키지 않기 위해(참여 건·에스크로 생성 경로를 타지 않도록)
     * 최신 블록을 트랜잭션 블록과 같게 둬서 컨펌 1 로 만듭니다.
     */
    @Test
    @Transactional
    @DisplayName("영수증 상태와 컨펌 수가 실제로 DB 에 저장된다 - merge 반환값 누락 회귀 방지")
    void persistsReceiptAndConfirmations() {
        CryptoAsset asset = cryptoAssetRepository.findById(assetId).orElseThrow();
        CryptoQuote quote = cryptoQuoteRepository.findById(quoteId).orElseThrow();
        CryptoWallet wallet = cryptoWalletRepository.findById(walletId).orElseThrow();

        recordService.record(PAYMENT_ID, quote, asset, wallet, BUYER, TX_HASH,
                successReceipt(), TX_BLOCK);

        // 영속성 컨텍스트를 비우고 DB 에서 다시 읽는다.
        // 이 과정을 생략하면 1차 캐시의 객체를 보게 되어 버그가 드러나지 않는다.
        entityManager.flush();
        entityManager.clear();
        CryptoPayment saved = cryptoPaymentRepository.findById(PAYMENT_ID).orElseThrow();

        assertThat(saved.getReceiptStatus()).isEqualTo(CryptoPayment.RECEIPT_SUCCESS);
        assertThat(saved.getBlockNumber()).isEqualTo(TX_BLOCK);
        assertThat(saved.getRawAmount()).isEqualTo(RAW_AMOUNT.toString());
        // 최신 블록 == 트랜잭션 블록 -> 컨펌 1. 0 이면 merge 반환값을 안 쓴 것이다.
        assertThat(saved.getConfirmations()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("입금 기록이 DB 에 남는다")
    void persistsDeposit() {
        CryptoAsset asset = cryptoAssetRepository.findById(assetId).orElseThrow();
        CryptoQuote quote = cryptoQuoteRepository.findById(quoteId).orElseThrow();
        CryptoWallet wallet = cryptoWalletRepository.findById(walletId).orElseThrow();

        recordService.record(PAYMENT_ID, quote, asset, wallet, BUYER, TX_HASH,
                successReceipt(), TX_BLOCK);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crypto_deposits WHERE payment_id = ?",
                Integer.class, PAYMENT_ID);
        assertThat(count).isEqualTo(1);

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM crypto_deposits WHERE payment_id = ?",
                String.class, PAYMENT_ID);
        assertThat(status).isEqualTo("MATCHED");
    }

    @Test
    @Transactional
    @DisplayName("Quote 가 원자적으로 claim 되어 used = true 로 저장된다")
    void persistsQuoteClaim() {
        CryptoAsset asset = cryptoAssetRepository.findById(assetId).orElseThrow();
        CryptoQuote quote = cryptoQuoteRepository.findById(quoteId).orElseThrow();
        CryptoWallet wallet = cryptoWalletRepository.findById(walletId).orElseThrow();

        recordService.record(PAYMENT_ID, quote, asset, wallet, BUYER, TX_HASH,
                successReceipt(), TX_BLOCK);

        Boolean used = jdbcTemplate.queryForObject(
                "SELECT used FROM crypto_quotes WHERE quote_id = ?", Boolean.class, quoteId);
        assertThat(used).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("노드가 트랜잭션을 모르면 영수증 상태를 비워 저장한다")
    void persistsWithoutReceipt() {
        CryptoAsset asset = cryptoAssetRepository.findById(assetId).orElseThrow();
        CryptoQuote quote = cryptoQuoteRepository.findById(quoteId).orElseThrow();
        CryptoWallet wallet = cryptoWalletRepository.findById(walletId).orElseThrow();

        recordService.record(PAYMENT_ID, quote, asset, wallet, BUYER, TX_HASH, null, TX_BLOCK);

        CryptoPayment saved = cryptoPaymentRepository.findById(PAYMENT_ID).orElseThrow();
        assertThat(saved.getReceiptStatus()).isNull();
        assertThat(saved.getTxHash()).isEqualTo(TX_HASH);
        // 폴링이 이어받을 수 있도록 행 자체는 저장돼 있어야 한다.
        assertThat(cryptoPaymentRepository.findByTxHash(TX_HASH)).isPresent();
    }
}
