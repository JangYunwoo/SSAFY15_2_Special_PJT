ALTER TABLE return_requests
    ADD COLUMN pickup_zipcode CHAR(5) NULL AFTER reject_reason,
    ADD COLUMN pickup_address1 VARCHAR(255) NULL AFTER pickup_zipcode,
    ADD COLUMN pickup_address2 VARCHAR(255) NULL AFTER pickup_address1,
    ADD COLUMN auto_approve_at DATETIME NULL
        COMMENT '판매자 무응답 자동 승인 예정 시각' AFTER requested_at,
    ADD KEY idx_returns_auto_approve (status, admin_review_required, auto_approve_at);

CREATE TABLE return_request_histories (
    history_id  BIGINT       NOT NULL AUTO_INCREMENT,
    return_id   BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    note        VARCHAR(500) NULL,
    occurred_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY idx_return_histories_return (return_id, occurred_at, history_id),
    CONSTRAINT fk_return_histories_return
        FOREIGN KEY (return_id) REFERENCES return_requests (return_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '반품 상태 변경 이력';
