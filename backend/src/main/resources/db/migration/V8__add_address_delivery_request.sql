-- S15P21B205-63: 배송지 CRUD(USR-03~06) 구현.
-- FE 계약(entities/address/model.ts)의 deliveryRequest(배송 요청사항)를 저장할 컬럼.
-- alias(별칭)와는 다른 의미라 재사용하지 않고 새로 둔다.
ALTER TABLE user_addresses
    ADD COLUMN delivery_request VARCHAR(100) NULL COMMENT '배송 요청사항' AFTER address2;
