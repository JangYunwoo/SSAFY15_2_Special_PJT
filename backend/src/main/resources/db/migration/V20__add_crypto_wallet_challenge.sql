CREATE TABLE crypto_wallet_challenges (
    challenge_id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    quote_id BIGINT NOT NULL,
    chain_id BIGINT NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    tx_hash VARCHAR(66) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    INDEX idx_crypto_challenge_expiry (expires_at),
    CONSTRAINT fk_crypto_challenge_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_crypto_challenge_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT fk_crypto_challenge_quote FOREIGN KEY (quote_id) REFERENCES crypto_quotes(quote_id)
);
