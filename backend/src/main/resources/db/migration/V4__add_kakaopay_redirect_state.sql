ALTER TABLE kakaopay_payments
    ADD COLUMN next_redirect_pc_url VARCHAR(500) NULL
        COMMENT 'Ready 응답 PC 결제 이동 URL' AFTER payment_method_type,
    ADD COLUMN state_nonce VARCHAR(64) NULL
        COMMENT '리다이렉트 state 일회용 nonce' AFTER next_redirect_pc_url,
    ADD COLUMN state_expires_at DATETIME NULL
        COMMENT 'state 만료 시각(결제 슬롯 만료와 동일)' AFTER state_nonce,
    ADD COLUMN state_consumed_at DATETIME NULL
        COMMENT '승인 시 state 소비 시각' AFTER state_expires_at;
