-- 외부 청구는 성공했으나 공구 종료로 참여 확정이 안 된 결제(참여행 없음)를 환불 원장에 연결한다.
-- refunds엔 결제 직접 참조가 없었다(참여/반품/입금으로만 연결). payment_id로 참여 없는 결제 환불을 개시·중복차단한다.
ALTER TABLE refunds ADD COLUMN payment_id BIGINT NULL
    COMMENT '참여 없이 결제 단위로 개시된 환불(승인 후 공구종료 등)' AFTER deposit_id;
ALTER TABLE refunds ADD CONSTRAINT fk_refunds_payment
    FOREIGN KEY (payment_id) REFERENCES payments (payment_id);
-- 같은 결제에 활성 환불은 하나. participation/deposit과 같은 (대상, marker) 유일성 패턴.
ALTER TABLE refunds ADD UNIQUE KEY uk_refunds_active_payment (payment_id, refund_marker);
