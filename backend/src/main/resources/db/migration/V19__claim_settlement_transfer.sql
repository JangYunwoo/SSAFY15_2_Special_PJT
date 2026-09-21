-- 정산 이체 실행권은 프로세스 종료 후에도 유지한다.
ALTER TABLE settlements ADD COLUMN transfer_claimed BOOLEAN NOT NULL DEFAULT FALSE;

-- 과거에 대조가 필요한 상태는 새 실행권 도입 후에도 안전하게 차단한다.
UPDATE settlements SET transfer_claimed = TRUE WHERE transfer_status = 'UNKNOWN';
