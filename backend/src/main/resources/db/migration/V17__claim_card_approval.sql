-- 실행권은 프로세스 종료 후에도 유지한다. 결과 불명 거래를 자동 초기화하지 않는다.
ALTER TABLE payments
    ADD COLUMN card_approval_status VARCHAR(20) NULL,
    ADD COLUMN card_institution_tx_no VARCHAR(20) NULL;

-- 기존 카드 승인 증거가 있으면 로컬 확정 실패 건도 재승인하지 않는다.
UPDATE payments p JOIN card_payments c ON c.payment_id = p.payment_id
SET p.card_approval_status = CASE WHEN p.status = 'PAID' THEN 'COMPLETED' ELSE 'UNKNOWN' END,
    p.card_institution_tx_no = c.institution_tx_no;
