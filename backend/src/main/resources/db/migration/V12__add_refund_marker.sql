-- 환불 중복 방지 마커 (S15P21B205-80)
--
-- refunds 에는 "같은 참여 건에 환불 행이 두 개 생기는 것" 을 막는 제약이 없었다.
-- 모집 실패 배치나 반품 수거 완료 처리가 재실행되면 같은 참여 건에 환불이 두 번
-- 만들어지고, 그만큼 실제 돈이 두 번 나간다. 애플리케이션에서 먼저 조회해 막는
-- 방식은 두 요청이 동시에 조회를 통과하면 둘 다 INSERT 되므로 경쟁에서 안전하지 않다.
--
-- payments.slot_marker 와 같은 패턴을 쓴다. 활성(PROCESSING/COMPLETED) 동안만 'Y' 를
-- 채우고 FAILED 가 되면 NULL 로 비운다. MySQL 의 UNIQUE 인덱스는 NULL 을 서로 다른
-- 값으로 취급하므로, 실패한 환불은 유일성 검사에서 빠져 재시도·대체가 가능해진다.
ALTER TABLE refunds
    ADD COLUMN refund_marker CHAR(1) NULL
        COMMENT 'PROCESSING/COMPLETED일 때 Y, FAILED면 NULL (중복 환불 차단)'
        AFTER status;

-- participation_id 가 NULL 인 건(슬롯 만료 입금·미매칭 입금)은 이 인덱스에 걸리지
-- 않는다. NULL 끼리는 충돌하지 않기 때문이다. 그 건들은 아래 deposit 인덱스가 막는다.
ALTER TABLE refunds
    ADD UNIQUE KEY uk_refunds_active_participation (participation_id, refund_marker);

-- 하나의 입금은 한 번만 돌려보낸다. 미매칭 입금 환불을 두 번 만들면 우리 지갑에서
-- 토큰이 두 번 나간다.
ALTER TABLE refunds
    ADD UNIQUE KEY uk_refunds_active_deposit (deposit_id, refund_marker);
