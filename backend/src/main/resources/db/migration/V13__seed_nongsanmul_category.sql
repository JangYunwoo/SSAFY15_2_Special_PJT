-- AI 상품 분류 카탈로그에 '농산물' 카테고리 추가.
-- 카테고리는 이름(name)으로 AI 추출 결과와 매칭되므로(AiExtractionService.mapCategory,
-- findFirstByNameIgnoreCaseAndActiveTrue) 이름을 AI 팀 카탈로그와 정확히 일치시킨다.
-- V8이 시드한 10001~10020(디지털기기~기타) 뒤에 10021로 붙인다.
--
-- NOTE: DevelopV1에 V11 중복(add_notification_dedup_key / add_refund_marker)이 있어
-- V12는 그 재넘버링 몫으로 비워두고 농산물은 V13으로 둔다.
INSERT INTO categories
    (category_id, parent_id, name, depth, sort_order, active)
VALUES
    (10021, NULL, '농산물', 1, 21, TRUE)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    depth = VALUES(depth),
    sort_order = VALUES(sort_order),
    active = VALUES(active);
