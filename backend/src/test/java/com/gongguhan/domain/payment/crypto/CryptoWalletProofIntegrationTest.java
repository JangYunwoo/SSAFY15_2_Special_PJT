package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.crypto.dto.*;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.*;
import com.gongguhan.domain.payment.crypto.service.CryptoTxRecordService;
import com.gongguhan.domain.payment.crypto.service.CryptoWalletProofService;
import com.gongguhan.global.error.BusinessException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:walletproof;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoWalletProofIntegrationTest {
    // 테스트 전용 공개 고정 키. 실제 지갑/자금에 사용하지 않는다.
    private static final ECKeyPair KEY = ECKeyPair.create(BigInteger.ONE);
    private static final String ADDRESS = "0x" + Keys.getAddress(KEY);
    private static final String HASH = "0x" + "a".repeat(64);
    private static final AtomicLong IDS = new AtomicLong(850000);
    @Autowired CryptoWalletProofService proofs;
    @Autowired CryptoWalletChallengeRepository challenges;
    @Autowired CryptoAssetRepository assets;
    @Autowired CryptoQuoteRepository quotes;
    @Autowired CryptoPaymentRepository cryptoPayments;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean CryptoTxRecordService records;
    private long id;

    @BeforeEach void fixture() {
        id = IDS.incrementAndGet();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("""
                INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,slot_expires_at,
                slot_marker,idempotency_key,status,created_at,updated_at)
                VALUES(?,?,1,1,'CRYPTO',1000,?,'Y',?,'PROCESSING',NOW(),NOW())
                """, id, id, now.plusMinutes(10), "proof-" + id);
        jdbc.update("""
                INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at)
                VALUES(?, ?,80002,'Polygon Amoy','0xcccccccccccccccccccccccccccccccccccccccc',6,5,TRUE,NOW())
                """, id, "T" + id);
        jdbc.update("""
                INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,
                asset_amount,issued_at,expires_at,used) VALUES(?,?,1000,0,1000,1000,1,?,?,FALSE)
                """, id, id, now, now.plusMinutes(4));
    }

    private CryptoWalletChallengeResponse issue() {
        return proofs.issue(1L, id, new CryptoWalletChallengeRequest(id, ADDRESS, HASH));
    }
    private String sign(String message, ECKeyPair key) {
        var sig = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), key);
        return "0x" + Numeric.toHexStringNoPrefix(sig.getR()) + Numeric.toHexStringNoPrefix(sig.getS())
                + Numeric.toHexStringNoPrefix(sig.getV());
    }
    private CryptoTxSubmitRequest request(CryptoWalletChallengeResponse proof) {
        return new CryptoTxSubmitRequest(id, HASH, ADDRESS, proof.challengeId(), sign(proof.message(), KEY));
    }
    private CryptoPayment record(CryptoTxSubmitRequest request) {
        return proofs.recordVerified(1L, id, request, quotes.findById(id).orElseThrow(),
                assets.findById(id).orElseThrow(), mock(CryptoWallet.class), null, 1);
    }
    private void successfulRecord() {
        when(records.record(anyLong(), any(), any(), any(), any(), any(), any(), anyLong())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            CryptoWallet wallet = mock(CryptoWallet.class);
            when(wallet.getWalletId()).thenReturn(1L);
            when(wallet.getAddress()).thenReturn("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
            return cryptoPayments.saveAndFlush(CryptoPayment.submit(id, assets.findById(id).orElseThrow(),
                    quotes.findById(id).orElseThrow(), wallet, ADDRESS, HASH));
        });
    }

    @Test void validSignatureIsAcceptedAndDomainPurposeAndExpiryAreBound() {
        var proof = issue();
        assertThat(proof.message()).contains("Domain: j15b205.p.ssafy.io", "does not send funds", "Payment ID: " + id,
                "Chain ID: 80002", "Nonce: " + proof.challengeId());
        assertThat(proof.expiresAt()).isEqualTo(quotes.findById(id).orElseThrow().getExpiresAt().atOffset(java.time.ZoneOffset.UTC).toString());
        assertThat(proofs.verifyAndRecover(1L, id, request(proof))).isEmpty();
        verifyNoInteractions(records);
        assertThat(issue().challengeId()).isNotEqualTo(proof.challengeId());
    }

    @ParameterizedTest @ValueSource(strings = {"USER", "PAYMENT", "QUOTE", "ADDRESS", "HASH", "SIGNER", "MESSAGE", "MALFORMED", "EXPIRED"})
    void alteredOrExpiredProofCannotReachRecord(String mutation) {
        var proof = issue();
        var original = request(proof);
        long user = "USER".equals(mutation) ? 2L : 1L;
        long payment = "PAYMENT".equals(mutation) ? id + 999 : id;
        if ("EXPIRED".equals(mutation)) jdbc.update("UPDATE crypto_wallet_challenges SET expires_at=? WHERE challenge_id=?",
                LocalDateTime.now(clock).minusSeconds(1), proof.challengeId());
        var request = new CryptoTxSubmitRequest("QUOTE".equals(mutation) ? id + 1 : id,
                "HASH".equals(mutation) ? "0x" + "b".repeat(64) : HASH,
                "ADDRESS".equals(mutation) ? "0x" + "b".repeat(40) : ADDRESS, proof.challengeId(),
                switch (mutation) {
                    case "SIGNER" -> sign(proof.message(), ECKeyPair.create(BigInteger.TWO));
                    case "MESSAGE" -> sign(proof.message() + "changed", KEY);
                    case "MALFORMED" -> "0x00";
                    default -> original.signature();
                });
        assertThatThrownBy(() -> proofs.verifyAndRecover(user, payment, request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records);
        assertThat(challenges.findById(proof.challengeId()).orElseThrow().isConsumed()).isFalse();
    }

    @Test void chainChangeBetweenProofAndRecordingIsRejected() {
        var proof = issue();
        jdbc.update("UPDATE crypto_assets SET chain_id=1 WHERE asset_id=?", id);
        assertThatThrownBy(() -> record(request(proof))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void paymentCancelledOrExpiredAfterProofValidationCannotBeRecorded(boolean cancelled) {
        var proof = issue(); var request = request(proof);
        assertThat(proofs.verifyAndRecover(1L, id, request)).isEmpty();
        // RPC 대기 구간 사이의 취소/만료 커밋 순서를 결정적으로 재현한다.
        if (cancelled) jdbc.update("UPDATE payments SET status='FAILED',slot_marker=NULL WHERE payment_id=?", id);
        else jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=?", LocalDateTime.now(clock).minusSeconds(1), id);
        assertThatThrownBy(() -> record(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(records);
        assertThat(challenges.findById(proof.challengeId()).orElseThrow().isConsumed()).isFalse();
    }

    @Test void failedRecordingRollsBackNonceAndSameRequestCanRetry() {
        var proof = issue();
        var request = request(proof);
        when(records.record(anyLong(), any(), any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new IllegalStateException("fixture failure"));
        assertThatThrownBy(() -> record(request)).isInstanceOf(IllegalStateException.class);
        assertThat(challenges.findById(proof.challengeId()).orElseThrow().isConsumed()).isFalse();
        reset(records);
        successfulRecord();
        record(request);
        assertThat(challenges.findById(proof.challengeId()).orElseThrow().isConsumed()).isTrue();
    }

    @Test void consumedSameRequestRecoversEvenAfterExpiryButCannotBeRepurposed() {
        var proof = issue(); var request = request(proof); successfulRecord(); record(request);
        jdbc.update("UPDATE crypto_wallet_challenges SET expires_at=? WHERE challenge_id=?",
                LocalDateTime.now(clock).minusDays(1), proof.challengeId());
        assertThat(proofs.verifyAndRecover(1L, id, request)).isPresent();
        assertThat(record(request).getPaymentId()).isEqualTo(id);
        verify(records, times(1)).record(anyLong(), any(), any(), any(), any(), any(), any(), anyLong());
        assertThatThrownBy(() -> proofs.verifyAndRecover(2L, id, request)).isInstanceOf(BusinessException.class);
    }

    @Test void concurrentSameNonceRecordsOnceAndReturnsSamePayment() throws Exception {
        var proof = issue(); var request = request(proof); successfulRecord();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return record(request).getPaymentId(); });
            var second = executor.submit(() -> { start.await(); return record(request).getPaymentId(); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(id);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(id);
        }
        verify(records, times(1)).record(anyLong(), any(), any(), any(), any(), any(), any(), anyLong());
    }
}
