-- 에스크로 부분 환불 (S15P21B205-99)
--
-- 반품 환불액이 결제금액 전액이 아닌 경우가 있다. 54,000원 결제에 단순변심 반품이면
-- 최초 배송비를 돌려주지 않아 42,000원만 환불되고, 남은 12,000원은 판매자에게
-- 정산되어야 한다.
--
-- 지금까지는 Escrow.refund() 가 상태를 REFUNDED 로 종료시켜 남은 금액이 정산
-- 대상에서 사라졌다. 부분 환불 누계를 따로 두고, 전액이 환불될 때만 종료시킨다.
--
--   보관 중 금액 = amount - refunded_amount
--
-- amount 를 직접 줄이지 않는 이유: 불변식 점검 쿼리가
-- "escrows.amount <> participations.total_amount" 로 어긋난 행을 찾는다(FR-ESC-08).
-- amount 를 줄이면 부분 환불된 건이 전부 오류로 잡힌다. 원래 보관액은 그대로 두고
-- 빠져나간 금액만 따로 센다.
ALTER TABLE escrows
    ADD COLUMN refunded_amount BIGINT NOT NULL DEFAULT 0
        COMMENT '부분 환불 누계. 보관 중 금액 = amount - refunded_amount'
        AFTER amount;

-- 이미 환불로 종료된 행은 전액이 빠져나간 상태다.
UPDATE escrows SET refunded_amount = amount WHERE status = 'REFUNDED';
