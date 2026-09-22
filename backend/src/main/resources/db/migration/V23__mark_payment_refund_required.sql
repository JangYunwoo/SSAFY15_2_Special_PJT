-- 외부 결제 승인은 성공했으나 공구 종료로 내부 확정이 불가능해진 결제를 표시한다.
-- 값이 채워지면 자동 환불 재조정(Phase 2)과 관리자 조회 대상이 된다. 과거 행은 임의 backfill 하지 않는다
-- (UNKNOWN 카드 행이 이 케이스인지 코드만으로 단정할 수 없음 - 제공사 대조 필요).
ALTER TABLE payments ADD COLUMN reconcile_state VARCHAR(20) NULL
    COMMENT '외부청구 성공+공구종료로 미확정된 결제의 재조정 상태(REFUND_REQUIRED 등)';
CREATE INDEX idx_payments_reconcile_state ON payments(reconcile_state);
