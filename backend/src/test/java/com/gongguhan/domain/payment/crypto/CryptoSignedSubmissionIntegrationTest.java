package com.gongguhan.domain.payment.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.payment.crypto.dto.*;
import com.gongguhan.domain.payment.crypto.service.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.*;
import org.web3j.utils.Numeric;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:signedsubmission;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class CryptoSignedSubmissionIntegrationTest {
    @Autowired CryptoWalletProofService proofs;
    @Autowired CryptoTxVerificationService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean ChainClient chain;

    @Test void signedSubmissionCommitsProofQuoteAndDepositThenRecoversIdenticalResponse() {
        var key = ECKeyPair.create(BigInteger.ONE); // 테스트 전용, 실제 자금 없는 고정 키
        String from = "0x" + Keys.getAddress(key), to = "0x" + "b".repeat(40), token = "0x" + "c".repeat(40), hash = "0x" + "a".repeat(64);
        var now = LocalDateTime.now(clock);
        jdbc.update("""
                INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,slot_expires_at,
                slot_marker,idempotency_key,status,created_at,updated_at)
                VALUES(880001,880001,1,1,'CRYPTO',1000,?,'Y','signed-fixture','PROCESSING',NOW(),NOW())
                """, now.plusMinutes(10));
        jdbc.update("""
                INSERT INTO crypto_assets(asset_id,symbol,chain_id,chain_name,token_contract,decimals,required_confirmations,enabled,created_at)
                VALUES(880001,'USDT',80002,'Polygon Amoy',?,6,5,TRUE,NOW())
                """, token);
        jdbc.update("INSERT INTO crypto_wallets(wallet_id,chain_id,address,purpose,active,created_at) VALUES(880001,80002,?,'RECEIVE',TRUE,NOW())", to);
        jdbc.update("""
                INSERT INTO crypto_quotes(quote_id,asset_id,market_rate,buffer_rate,applied_rate,krw_amount,
                asset_amount,issued_at,expires_at,used) VALUES(880001,880001,1000,0,1000,1000,1,?,?,FALSE)
                """, now, now.plusMinutes(4));
        var challenge = proofs.issue(1L, 880001L, new CryptoWalletChallengeRequest(880001L, from, hash));
        var sig = Sign.signPrefixedMessage(challenge.message().getBytes(StandardCharsets.UTF_8), key);
        var signature = "0x" + Numeric.toHexStringNoPrefix(sig.getR()) + Numeric.toHexStringNoPrefix(sig.getS()) + Numeric.toHexStringNoPrefix(sig.getV());
        var request = new CryptoTxSubmitRequest(880001L, hash, from, challenge.challengeId(), signature);
        when(chain.findReceipt(hash)).thenReturn(Optional.of(new ChainClient.ReceiptView(true, 100,
                List.of(new ChainClient.TransferLog(token, from, to, BigInteger.valueOf(1000000), 0)))));
        when(chain.getLatestBlockNumber()).thenReturn(100L);
        var first = service.submitTransaction(1L, 880001L, request);
        var retry = service.submitTransaction(1L, 880001L, request);
        assertThat(first).isEqualTo(retry);
        assertThat(first.phase()).isEqualTo("CONFIRMING");
        assertThat(jdbc.queryForObject("SELECT consumed_at IS NOT NULL FROM crypto_wallet_challenges WHERE challenge_id=?", Boolean.class, challenge.challengeId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT used FROM crypto_quotes WHERE quote_id=880001", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_payments WHERE payment_id=880001", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM crypto_deposits WHERE payment_id=880001", Integer.class)).isEqualTo(1);
        verify(chain, times(1)).findReceipt(hash);
        verify(chain, times(1)).getLatestBlockNumber();
    }
}
