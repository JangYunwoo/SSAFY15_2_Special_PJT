CREATE TABLE kakaopay_cancellations (
    idempotency_key         VARCHAR(64)  NOT NULL,
    payment_id              BIGINT       NOT NULL,
    tid                     VARCHAR(100) NOT NULL,
    requested_amount        BIGINT       NOT NULL,
    status                  VARCHAR(20)  NOT NULL COMMENT 'PENDING/SUCCEEDED/FAILED/UNKNOWN',
    cancel_aid              VARCHAR(100) NULL,
    approved_cancel_amount  BIGINT       NULL,
    canceled_amount         BIGINT       NULL,
    cancel_available_amount BIGINT       NULL,
    failure_reason          VARCHAR(500) NULL,
    created_at              DATETIME     NOT NULL,
    updated_at              DATETIME     NOT NULL,
    PRIMARY KEY (idempotency_key),
    KEY idx_kakao_cancel_payment (payment_id, status),
    KEY idx_kakao_cancel_tid (tid),
    CONSTRAINT fk_kakao_cancel_payment
        FOREIGN KEY (payment_id) REFERENCES kakaopay_payments (payment_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '카카오페이 전체·부분 취소 멱등 요청';
