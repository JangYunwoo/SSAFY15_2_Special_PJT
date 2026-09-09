-- =====================================================================
-- V3: payments.address_id 추가
--
-- 결제 시작(PAY-01) 시 구매자가 고른 배송지를 결제 건에 남긴다.
--
-- 왜 필요한가
--   승인 완료 시 participations 를 만들 때 배송지 스냅샷이 필요한데
--   (PaymentService.completePayment 의 ParticipationSnapshot),
--   지금은 PAY-01 이 addressId 를 검증만 하고 버려서 그 시점에 출처가 없다.
--
--   카드·카카오페이는 사용자의 승인 요청에 다시 담아 보낼 수 있지만,
--   코인 결제는 컨펌 5개 도달을 기다리는 스케줄러가 완료 처리하므로
--   그 시점에 HTTP 요청 컨텍스트가 아예 없다.
--
-- 왜 배송지 5개 컬럼을 복사하지 않고 addressId 만 두는가
--   user_addresses 는 setter 도 수정 메서드도 없는 불변 엔티티이고
--   주소 수정 API 도 없다. 따라서 완료 시점에 다시 읽어도 같은 값이 나온다.
--   나중에 주소 수정 기능이 생기면 그때 스냅샷 방식으로 바꿔야 한다.
--
-- NULL 허용인 이유
--   이미 존재하는 결제 행에는 채울 값이 없다. NOT NULL 로 걸면 마이그레이션이
--   실패한다. 신규 결제는 애플리케이션이 항상 채운다.
--
-- 담당 주의: payments 는 결제 공통(payment.common) 소유 테이블이다.
--            컬럼 추가만 하고 기존 컬럼·제약은 건드리지 않았다.
-- =====================================================================

ALTER TABLE payments
    ADD COLUMN address_id BIGINT NULL
        COMMENT '결제 시작 시 선택한 배송지. 승인 완료 시 참여 건 배송지의 출처'
        AFTER user_id,
    ADD KEY idx_payments_address (address_id),
    ADD CONSTRAINT fk_payments_address
        FOREIGN KEY (address_id) REFERENCES user_addresses (address_id);
