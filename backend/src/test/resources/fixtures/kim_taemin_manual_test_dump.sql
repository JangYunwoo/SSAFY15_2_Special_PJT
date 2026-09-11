-- 김태민 담당 기능 수동 점검용 데이터 dump
-- 대상: 로컬 gongguhan DB / Flyway V10 적용 상태
-- 비밀번호: 로컬 시연 문서에서 별도로 안내하는 공통 테스트 비밀번호의 BCrypt 해시
-- 안전성: 910000번대 이상의 고정 ID만 INSERT/UPDATE하며 다른 행을 삭제하지 않는다.

SET NAMES utf8mb4;
SET @fixture_now = NOW();

-- ---------------------------------------------------------------------
-- 사용자 3명: 판매자 1명, 구매자 2명
-- ---------------------------------------------------------------------
INSERT INTO users (
    user_id, email, password_hash, nickname, phone, phone_verified,
    ssafy_user_key, ssafy_account_no, wallet_address, is_seller, status,
    created_at, updated_at
) VALUES
    (910001, 'kim.fixture.seller@gongguhan.test',
     '$2a$10$lDwSPpezDZCCkqpGr4cRPuC4iMh8dIiglBSLcCd.rCajyCLrSPgsy',
     '김태민 점검 판매자', '01090000001', TRUE, NULL, NULL, NULL, TRUE, 'ACTIVE',
     @fixture_now, @fixture_now),
    (910002, 'kim.fixture.buyer@gongguhan.test',
     '$2a$10$lDwSPpezDZCCkqpGr4cRPuC4iMh8dIiglBSLcCd.rCajyCLrSPgsy',
     '김태민 점검 구매자', '01090000002', TRUE, NULL, NULL, NULL, FALSE, 'ACTIVE',
     @fixture_now, @fixture_now),
    (910003, 'kim.fixture.buyer2@gongguhan.test',
     '$2a$10$lDwSPpezDZCCkqpGr4cRPuC4iMh8dIiglBSLcCd.rCajyCLrSPgsy',
     '김태민 반품 구매자', '01090000003', TRUE, NULL, NULL, NULL, FALSE, 'ACTIVE',
     @fixture_now, @fixture_now)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    phone = VALUES(phone),
    phone_verified = VALUES(phone_verified),
    is_seller = VALUES(is_seller),
    status = VALUES(status),
    updated_at = @fixture_now;

INSERT INTO user_addresses (
    address_id, user_id, alias, recipient_name, recipient_phone, zipcode,
    address1, address2, delivery_request, is_default, created_at, updated_at
) VALUES
    (911001, 910001, '사업장', '점검 판매자', '01090000001', '34134',
     '대전광역시 유성구', 'SSAFY 테스트 판매자', '경비실에 맡겨주세요', TRUE, @fixture_now, @fixture_now),
    (911002, 910002, '집', '점검 구매자', '01090000002', '34134',
     '대전광역시 유성구', 'SSAFY 테스트 구매자', '문 앞에 놓아주세요', TRUE, @fixture_now, @fixture_now),
    (911003, 910003, '집', '반품 구매자', '01090000003', '34134',
     '대전광역시 유성구', 'SSAFY 테스트 반품', '직접 받을게요', TRUE, @fixture_now, @fixture_now)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id), alias = VALUES(alias), recipient_name = VALUES(recipient_name),
    recipient_phone = VALUES(recipient_phone), zipcode = VALUES(zipcode),
    address1 = VALUES(address1), address2 = VALUES(address2),
    delivery_request = VALUES(delivery_request), is_default = VALUES(is_default),
    updated_at = @fixture_now;

INSERT INTO seller_profiles (
    seller_id, business_name, business_phone, ssafy_account_no, account_holder,
    account_verified, return_zipcode, return_address1, return_address2,
    penalty_count, status, verified_at, created_at, updated_at
) VALUES (
    910001, '김태민 기능 점검 상점', '01090000001', NULL, '점검판매자',
    TRUE, '34134', '대전광역시 유성구', 'SSAFY 테스트 판매자',
    0, 'ACTIVE', @fixture_now, @fixture_now, @fixture_now
)
ON DUPLICATE KEY UPDATE
    business_name = VALUES(business_name), business_phone = VALUES(business_phone),
    account_holder = VALUES(account_holder), account_verified = VALUES(account_verified),
    return_zipcode = VALUES(return_zipcode), return_address1 = VALUES(return_address1),
    return_address2 = VALUES(return_address2), penalty_count = 0,
    status = 'ACTIVE', verified_at = @fixture_now, updated_at = @fixture_now;

-- ---------------------------------------------------------------------
-- 상품 4개: 취소 점검, 결제 점검, 배송·반품 점검, 신규 생성 점검
-- ---------------------------------------------------------------------
INSERT INTO products (
    product_id, seller_id, category_id, name, brand, model_name, color,
    description, price, ai_extracted, withdrawal_restricted,
    restriction_reason_id, restriction_evidence_url, restriction_review_status,
    restriction_reviewed_by, restriction_reviewed_at, status, created_at, updated_at
) VALUES
    (920001, 910001, 10001, '[FIXTURE] 무참여 취소용 무선 이어폰', 'GONGGUHAN', 'GGH-001', '화이트',
     '공동구매 생성·수정·무참여 취소를 반복 점검하는 상품', 100000, FALSE, FALSE,
     NULL, NULL, 'NONE', NULL, NULL, 'ACTIVE', @fixture_now, @fixture_now),
    (920002, 910001, 10004, '[FIXTURE] 카카오페이 결제용 생활용품', 'GONGGUHAN', 'GGH-002', '그레이',
     '결제 준비와 카카오페이 Ready 흐름을 점검하는 상품', 60000, FALSE, FALSE,
     NULL, NULL, 'NONE', NULL, NULL, 'ACTIVE', @fixture_now, @fixture_now),
    (920003, 910001, 10001, '[FIXTURE] 배송·반품용 소형가전', 'GONGGUHAN', 'GGH-003', '블랙',
     '배송 완료 이후 반품 승인·거절·이의 흐름을 점검하는 상품', 120000, FALSE, FALSE,
     NULL, NULL, 'NONE', NULL, NULL, 'ACTIVE', DATE_SUB(@fixture_now, INTERVAL 10 DAY), @fixture_now),
    (920004, 910001, 10020, '[FIXTURE] 신규 공구 생성용 상품', 'GONGGUHAN', 'GGH-004', '네이비',
     '상품 수정·삭제와 공동구매 신규 생성을 점검하는 미연결 상품', 80000, FALSE, FALSE,
     NULL, NULL, 'NONE', NULL, NULL, 'ACTIVE', @fixture_now, @fixture_now)
ON DUPLICATE KEY UPDATE
    seller_id = VALUES(seller_id), category_id = VALUES(category_id), name = VALUES(name),
    brand = VALUES(brand), model_name = VALUES(model_name), color = VALUES(color),
    description = VALUES(description), price = VALUES(price),
    ai_extracted = VALUES(ai_extracted), withdrawal_restricted = VALUES(withdrawal_restricted),
    restriction_reason_id = VALUES(restriction_reason_id),
    restriction_evidence_url = VALUES(restriction_evidence_url),
    restriction_review_status = VALUES(restriction_review_status),
    restriction_reviewed_by = NULL, restriction_reviewed_at = NULL,
    status = 'ACTIVE', updated_at = @fixture_now;

INSERT INTO product_images (image_id, product_id, image_url, sort_order, is_thumbnail, created_at) VALUES
    (921001, 920001, 'https://placehold.co/640x480?text=Fixture+Cancel', 0, TRUE, @fixture_now),
    (921002, 920002, 'https://placehold.co/640x480?text=Fixture+KakaoPay', 0, TRUE, @fixture_now),
    (921003, 920003, 'https://placehold.co/640x480?text=Fixture+Return', 0, TRUE, @fixture_now),
    (921004, 920004, 'https://placehold.co/640x480?text=Fixture+Create', 0, TRUE, @fixture_now)
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id), image_url = VALUES(image_url),
    sort_order = VALUES(sort_order), is_thumbnail = VALUES(is_thumbnail);

-- ---------------------------------------------------------------------
-- 공동구매 3개
-- 930001: 참여자 없는 수정·취소용
-- 930002: 구매자가 결제를 시작할 모집 중 건
-- 930003: 두 명 결제·배송 완료, 한 명은 반품 신청 중
-- ---------------------------------------------------------------------
INSERT INTO group_buys (
    group_buy_id, product_id, seller_id, product_price, target_participants,
    unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
    expected_platform_fee, expected_settlement_amount,
    recruit_start_at, recruit_end_at, current_participants, reserved_slots,
    fulfilled_at, shipping_deadline_at, status, version, created_at, updated_at
) VALUES
    (930001, 920001, 910001, 100000, 4, 25000, 3000, 0, 0.0500,
     5000, 107000, DATE_SUB(@fixture_now, INTERVAL 1 DAY), DATE_ADD(@fixture_now, INTERVAL 7 DAY),
     0, 0, NULL, NULL, 'RECRUITING', 0, @fixture_now, @fixture_now),
    (930002, 920002, 910001, 60000, 2, 30000, 3000, 0, 0.0500,
     3000, 63000, DATE_SUB(@fixture_now, INTERVAL 1 DAY), DATE_ADD(@fixture_now, INTERVAL 7 DAY),
     0, 0, NULL, NULL, 'RECRUITING', 0, @fixture_now, @fixture_now),
    (930003, 920003, 910001, 120000, 2, 60000, 3000, 0, 0.0500,
     6000, 120000, DATE_SUB(@fixture_now, INTERVAL 10 DAY), DATE_SUB(@fixture_now, INTERVAL 8 DAY),
     2, 0, DATE_SUB(@fixture_now, INTERVAL 8 DAY), DATE_SUB(@fixture_now, INTERVAL 5 DAY),
     'DELIVERED', 0, DATE_SUB(@fixture_now, INTERVAL 10 DAY), @fixture_now)
ON DUPLICATE KEY UPDATE
    product_id = VALUES(product_id), seller_id = VALUES(seller_id),
    product_price = VALUES(product_price), target_participants = VALUES(target_participants),
    unit_share_amount = VALUES(unit_share_amount), shipping_fee = VALUES(shipping_fee),
    rounding_diff = VALUES(rounding_diff), platform_fee_rate = VALUES(platform_fee_rate),
    expected_platform_fee = VALUES(expected_platform_fee),
    expected_settlement_amount = VALUES(expected_settlement_amount),
    recruit_start_at = VALUES(recruit_start_at), recruit_end_at = VALUES(recruit_end_at),
    current_participants = VALUES(current_participants), reserved_slots = VALUES(reserved_slots),
    fulfilled_at = VALUES(fulfilled_at), shipping_deadline_at = VALUES(shipping_deadline_at),
    status = VALUES(status), version = 0, updated_at = @fixture_now;

-- ---------------------------------------------------------------------
-- 배송 완료 참여 2건과 결제·카카오페이·에스크로 스냅샷
-- ---------------------------------------------------------------------
INSERT INTO participations (
    participation_id, group_buy_id, user_id, share_amount, shipping_fee, total_amount,
    recipient_name, recipient_phone, zipcode, address1, address2, status,
    joined_at, delivered_at, withdrawal_period_end_at, settlement_eligible_at,
    confirmed_at, confirm_type, confirm_notice_agreed, confirm_hold,
    settled, active_marker, updated_at
) VALUES
    (940001, 930003, 910002, 60000, 3000, 63000,
     '점검 구매자', '01090000002', '34134', '대전광역시 유성구', 'SSAFY 테스트 구매자',
     'RETURNING', DATE_SUB(@fixture_now, INTERVAL 8 DAY), DATE_SUB(@fixture_now, INTERVAL 2 DAY),
     DATE_ADD(@fixture_now, INTERVAL 5 DAY), DATE_ADD(@fixture_now, INTERVAL 5 DAY),
     NULL, NULL, FALSE, TRUE, FALSE, 'Y', @fixture_now),
    (940002, 930003, 910003, 60000, 3000, 63000,
     '반품 구매자', '01090000003', '34134', '대전광역시 유성구', 'SSAFY 테스트 반품',
     'DELIVERED', DATE_SUB(@fixture_now, INTERVAL 8 DAY), DATE_SUB(@fixture_now, INTERVAL 2 DAY),
     DATE_ADD(@fixture_now, INTERVAL 5 DAY), DATE_ADD(@fixture_now, INTERVAL 5 DAY),
     NULL, NULL, FALSE, FALSE, FALSE, 'Y', @fixture_now)
ON DUPLICATE KEY UPDATE
    group_buy_id = VALUES(group_buy_id), user_id = VALUES(user_id),
    share_amount = VALUES(share_amount), shipping_fee = VALUES(shipping_fee),
    total_amount = VALUES(total_amount), recipient_name = VALUES(recipient_name),
    recipient_phone = VALUES(recipient_phone), zipcode = VALUES(zipcode),
    address1 = VALUES(address1), address2 = VALUES(address2), status = VALUES(status),
    joined_at = VALUES(joined_at), delivered_at = VALUES(delivered_at),
    withdrawal_period_end_at = VALUES(withdrawal_period_end_at),
    settlement_eligible_at = VALUES(settlement_eligible_at), confirmed_at = NULL,
    confirm_type = NULL, confirm_notice_agreed = FALSE,
    confirm_hold = VALUES(confirm_hold), settled = FALSE, active_marker = 'Y',
    updated_at = @fixture_now;

INSERT INTO payments (
    payment_id, group_buy_id, user_id, address_id, participation_id, method,
    krw_amount, slot_expires_at, slot_marker, idempotency_key, status,
    approved_at, fail_reason, created_at, updated_at
) VALUES
    (950001, 930003, 910002, 911002, 940001, 'KAKAOPAY', 63000,
     DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, 'fixture-kim-paid-950001', 'PAID',
     DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, DATE_SUB(@fixture_now, INTERVAL 8 DAY), @fixture_now),
    (950002, 930003, 910003, 911003, 940002, 'KAKAOPAY', 63000,
     DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, 'fixture-kim-paid-950002', 'PAID',
     DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, DATE_SUB(@fixture_now, INTERVAL 8 DAY), @fixture_now)
ON DUPLICATE KEY UPDATE
    group_buy_id = VALUES(group_buy_id), user_id = VALUES(user_id),
    address_id = VALUES(address_id), participation_id = VALUES(participation_id),
    method = VALUES(method), krw_amount = VALUES(krw_amount),
    slot_expires_at = VALUES(slot_expires_at), slot_marker = NULL,
    idempotency_key = VALUES(idempotency_key), status = 'PAID',
    approved_at = VALUES(approved_at), fail_reason = NULL, updated_at = @fixture_now;

INSERT INTO kakaopay_payments (
    payment_id, tid, aid, partner_order_id, partner_user_id, total_amount,
    tax_free_amount, vat_amount, payment_method_type, next_redirect_pc_url,
    state_nonce, state_expires_at, state_consumed_at, raw_response
) VALUES
    (950001, 'T_FIXTURE_950001', 'A_FIXTURE_950001', 'payment-950001', 'user-910002',
     63000, 0, 5727, 'MONEY', NULL, NULL, NULL, @fixture_now, JSON_OBJECT('fixture', TRUE)),
    (950002, 'T_FIXTURE_950002', 'A_FIXTURE_950002', 'payment-950002', 'user-910003',
     63000, 0, 5727, 'MONEY', NULL, NULL, NULL, @fixture_now, JSON_OBJECT('fixture', TRUE))
ON DUPLICATE KEY UPDATE
    tid = VALUES(tid), aid = VALUES(aid), partner_order_id = VALUES(partner_order_id),
    partner_user_id = VALUES(partner_user_id), total_amount = VALUES(total_amount),
    tax_free_amount = 0, vat_amount = VALUES(vat_amount),
    payment_method_type = VALUES(payment_method_type), next_redirect_pc_url = NULL,
    state_nonce = NULL, state_expires_at = NULL, state_consumed_at = @fixture_now,
    raw_response = JSON_OBJECT('fixture', TRUE);

INSERT INTO escrows (
    escrow_id, participation_id, group_buy_id, amount, status,
    held_at, released_at, refunded_at
) VALUES
    (951001, 940001, 930003, 63000, 'HELD', DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, NULL),
    (951002, 940002, 930003, 63000, 'HELD', DATE_SUB(@fixture_now, INTERVAL 8 DAY), NULL, NULL)
ON DUPLICATE KEY UPDATE
    participation_id = VALUES(participation_id), group_buy_id = VALUES(group_buy_id),
    amount = VALUES(amount), status = 'HELD', held_at = VALUES(held_at),
    released_at = NULL, refunded_at = NULL;

INSERT INTO deliveries (
    delivery_id, participation_id, courier_code, tracking_number,
    shipped_at, delivered_at, status, updated_at
) VALUES
    (952001, 940001, 'CJ', 'FIXTURETRACK950001', DATE_SUB(@fixture_now, INTERVAL 4 DAY),
     DATE_SUB(@fixture_now, INTERVAL 2 DAY), 'DELIVERED', @fixture_now),
    (952002, 940002, 'CJ', 'FIXTURETRACK950002', DATE_SUB(@fixture_now, INTERVAL 4 DAY),
     DATE_SUB(@fixture_now, INTERVAL 2 DAY), 'DELIVERED', @fixture_now)
ON DUPLICATE KEY UPDATE
    participation_id = VALUES(participation_id), courier_code = VALUES(courier_code),
    tracking_number = VALUES(tracking_number), shipped_at = VALUES(shipped_at),
    delivered_at = VALUES(delivered_at), status = 'DELIVERED', updated_at = @fixture_now;

-- ---------------------------------------------------------------------
-- 구매자 910002의 판매자 처리 대기 반품 1건
-- 구매자 910003의 참여 940002는 preview 및 신규 신청 테스트용으로 비워 둔다.
-- ---------------------------------------------------------------------
INSERT INTO return_requests (
    return_id, participation_id, reason_type, reason_detail,
    return_shipping_fee, fee_bearer, refund_amount, post_confirm,
    admin_review_required, status, requested_at, auto_approve_at,
    auto_refund_due_at, approved_at, approve_type, picked_up_at,
    auto_refunded, forced_by, forced_at, completed_at, reject_reason,
    pickup_zipcode, pickup_address1, pickup_address2
) VALUES (
    960001, 940001, 'DEFECT', '전원이 켜지지 않습니다.',
    0, 'SELLER', 63000, FALSE, FALSE, 'REQUESTED',
    @fixture_now, DATE_ADD(@fixture_now, INTERVAL 3 DAY), DATE_ADD(@fixture_now, INTERVAL 7 DAY),
    NULL, NULL, NULL, FALSE, NULL, NULL, NULL, NULL,
    '34134', '대전광역시 유성구', 'SSAFY 테스트 구매자'
)
ON DUPLICATE KEY UPDATE
    participation_id = VALUES(participation_id), reason_type = 'DEFECT',
    reason_detail = VALUES(reason_detail), return_shipping_fee = 0,
    fee_bearer = 'SELLER', refund_amount = 63000, post_confirm = FALSE,
    admin_review_required = FALSE, status = 'REQUESTED', requested_at = @fixture_now,
    auto_approve_at = DATE_ADD(@fixture_now, INTERVAL 3 DAY),
    auto_refund_due_at = DATE_ADD(@fixture_now, INTERVAL 7 DAY),
    approved_at = NULL, approve_type = NULL, picked_up_at = NULL,
    auto_refunded = FALSE, forced_by = NULL, forced_at = NULL,
    completed_at = NULL, reject_reason = NULL,
    pickup_zipcode = VALUES(pickup_zipcode), pickup_address1 = VALUES(pickup_address1),
    pickup_address2 = VALUES(pickup_address2);

INSERT INTO return_request_histories (history_id, return_id, status, note, occurred_at) VALUES
    (961001, 960001, 'REQUESTED', '김태민 담당 수동 점검 fixture', @fixture_now)
ON DUPLICATE KEY UPDATE
    return_id = VALUES(return_id), status = 'REQUESTED',
    note = VALUES(note), occurred_at = @fixture_now;

SELECT 'kim-taemin fixture loaded' AS result,
       (SELECT COUNT(*) FROM users WHERE user_id BETWEEN 910001 AND 910003) AS users,
       (SELECT COUNT(*) FROM products WHERE product_id BETWEEN 920001 AND 920004) AS products,
       (SELECT COUNT(*) FROM group_buys WHERE group_buy_id BETWEEN 930001 AND 930003) AS group_buys,
       (SELECT COUNT(*) FROM participations WHERE participation_id BETWEEN 940001 AND 940002) AS participations,
       (SELECT COUNT(*) FROM return_requests WHERE return_id = 960001) AS return_requests;
