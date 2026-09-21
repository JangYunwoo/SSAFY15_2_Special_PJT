package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "crypto_wallet_challenges")
public class CryptoWalletChallenge {
    @Id @Column(length = 36) private String challengeId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long paymentId;
    @Column(nullable = false) private Long quoteId;
    @Column(nullable = false) private long chainId;
    @Column(nullable = false, length = 42) private String fromAddress;
    @Column(nullable = false, length = 66) private String txHash;
    @Column(nullable = false, length = 2000) private String message;
    @Column(nullable = false) private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;

    protected CryptoWalletChallenge() {}
    public CryptoWalletChallenge(String id, Long userId, Long paymentId, Long quoteId, long chainId,
            String address, String hash, String message, LocalDateTime expiresAt) {
        this.challengeId = id; this.userId = userId; this.paymentId = paymentId; this.quoteId = quoteId;
        this.chainId = chainId; this.fromAddress = address; this.txHash = hash;
        this.message = message; this.expiresAt = expiresAt;
    }
    public String getChallengeId() { return challengeId; }
    public Long getUserId() { return userId; }
    public Long getPaymentId() { return paymentId; }
    public Long getQuoteId() { return quoteId; }
    public long getChainId() { return chainId; }
    public String getFromAddress() { return fromAddress; }
    public String getTxHash() { return txHash; }
    public String getMessage() { return message; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(LocalDateTime now) { consumedAt = now; }
}
