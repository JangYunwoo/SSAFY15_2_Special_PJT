-- S15P21B205-77: 배송 기한 초과 감지 배치가 쓰는 정책값.
-- 송장 등록(발송) 후 이 일수 안에 배송완료로 전환되지 않으면 FAKE_INVOICE 위반 후보로 등록한다.
INSERT INTO platform_policies (policy_key, policy_value, description)
VALUES ('FAKE_INVOICE_DETECT_DAYS', '7', '송장 등록 후 배송완료 미전환 감지 기한');
