-- Load test seed/reset script for the payment slot reservation benchmark
-- (Redis fast-fail gate portfolio exercise). Idempotent: safe to re-run before
-- every k6 execution (used for both the "before" and "after" runs).
--
-- Reserved ID range: 9000000+ so this never collides with real/dev data.
--   users/seller_profiles/products : 9000000
--   group_buys                     : 9000001
--   buyer users + addresses        : 9001001 .. 9001000+USER_COUNT (default 900)

SET SESSION cte_max_recursion_depth = 2000;

-- 1) Cleanup leftovers from a previous run (escrows -> payments/participations,
--    FK-safe order). group_buys/products/users/seller_profiles are reused, not
--    deleted, across runs.
DELETE FROM escrows WHERE group_buy_id = 9000001;
DELETE FROM payments WHERE group_buy_id = 9000001;
DELETE FROM participations WHERE group_buy_id = 9000001;

-- 2) Seller + product + group buy (upsert)
INSERT INTO users (user_id, email, password_hash, nickname, is_seller, status)
VALUES (9000000, 'loadtest-seller@test.local', 'x', 'loadtest-seller', TRUE, 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO seller_profiles (seller_id, business_name, account_verified, status, verified_at)
VALUES (9000000, 'Loadtest Seller', TRUE, 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO products (product_id, seller_id, name, price, status)
VALUES (9000000, 9000000, 'Loadtest Product', 50000, 'ACTIVE')
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

-- target_participants = 50: small enough, relative to the ~900 concurrent
-- identities below, to force real contention (most requests get rejected).
INSERT INTO group_buys (
    group_buy_id, product_id, seller_id, product_price, target_participants,
    unit_share_amount, shipping_fee, expected_platform_fee, expected_settlement_amount,
    recruit_start_at, recruit_end_at, current_participants, reserved_slots, status, version
) VALUES (
    9000001, 9000000, 9000000, 50000, 50,
    50000, 3000, 2500, 47500,
    NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 0, 0, 'RECRUITING', 0
)
ON DUPLICATE KEY UPDATE
    -- This is the important part on re-runs: reset counters polluted by the previous k6 run.
    current_participants = 0,
    reserved_slots = 0,
    status = 'RECRUITING';

-- 3) ~900 buyer identities (one per k6 iteration, never reused within a run so
--    no request can fail with DUPLICATE_PARTICIPATION instead of GROUP_BUY_FULL).
INSERT INTO users (user_id, email, password_hash, nickname, status)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 900
)
SELECT 9001000 + n, CONCAT('loadtest-buyer-', n, '@test.local'), 'x', CONCAT('lt-buyer-', n), 'ACTIVE'
FROM seq
ON DUPLICATE KEY UPDATE status = 'ACTIVE';

INSERT INTO user_addresses (address_id, user_id, recipient_name, recipient_phone, zipcode, address1, is_default)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 900
)
SELECT 9001000 + n, 9001000 + n, 'Loadtest Buyer', '01000000000', '06236', 'Seoul Gangnam-gu', TRUE
FROM seq
ON DUPLICATE KEY UPDATE recipient_name = recipient_name;

-- 4) Sanity check (eyeball before running k6)
SELECT
    (SELECT COUNT(*) FROM users WHERE user_id BETWEEN 9001001 AND 9001900) AS buyer_users,
    (SELECT COUNT(*) FROM user_addresses WHERE address_id BETWEEN 9001001 AND 9001900) AS buyer_addresses,
    (SELECT target_participants FROM group_buys WHERE group_buy_id = 9000001) AS target_participants,
    (SELECT reserved_slots FROM group_buys WHERE group_buy_id = 9000001) AS reserved_slots,
    (SELECT current_participants FROM group_buys WHERE group_buy_id = 9000001) AS current_participants;
