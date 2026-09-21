-- 주소록 삭제와 거래 배송정보 보존은 별도 생명주기다.
-- 원본 address_id는 멱등 요청 비교/선택 이력으로 유지하되 주소록 삭제를 막는 FK만 해제한다.
-- 기존 결제는 과거 주소를 복원할 수 없으므로 일괄 덮어쓰기하지 않는다.
-- 진행 중 레거시는 외부 승인/견적 발급 전에 소유자 주소 검증 후 한 번만 확정한다.
ALTER TABLE payments
    DROP FOREIGN KEY fk_payments_address,
    ADD COLUMN shipping_recipient_name VARCHAR(50) NULL,
    ADD COLUMN shipping_recipient_phone VARCHAR(20) NULL,
    ADD COLUMN shipping_zipcode VARCHAR(5) NULL,
    ADD COLUMN shipping_address1 VARCHAR(255) NULL,
    ADD COLUMN shipping_address2 VARCHAR(255) NULL,
    ADD COLUMN shipping_delivery_request VARCHAR(100) NULL,
    ADD COLUMN prepare_delivery_request VARCHAR(100) NULL;

ALTER TABLE participations
    ADD COLUMN delivery_request VARCHAR(100) NULL;
