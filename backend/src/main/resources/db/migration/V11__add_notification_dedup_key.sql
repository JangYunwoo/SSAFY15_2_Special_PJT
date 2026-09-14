-- S15P21B205-110: 판매자 제재 이벤트 소비 시 동일 제재에 대한 중복 알림을 막기 위한 멱등 키.
-- 이벤트가 재발행·중복 소비되어도 같은 알림이 두 번 쌓이지 않게 유니크 인덱스를 건다.
-- dedup이 필요 없는 알림은 NULL로 두며, MySQL 유니크 인덱스는 다중 NULL을 허용한다.
ALTER TABLE notifications
    ADD COLUMN dedup_key VARCHAR(150) NULL COMMENT '이벤트 중복 알림 방지 키' AFTER read_at,
    ADD UNIQUE KEY uk_notifications_dedup (dedup_key);
