-- 기존 상품이 참조하는 카테고리는 이력 보존을 위해 삭제하지 않는다.
-- 새 등록·검색 필터에는 아래 표준 카탈로그만 노출한다.
ALTER TABLE categories
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER sort_order;

UPDATE categories
   SET active = FALSE;

INSERT INTO categories
    (category_id, parent_id, name, depth, sort_order, active)
VALUES
    (10001, NULL, '디지털기기',       1,  1, TRUE),
    (10002, NULL, '생활가전',         1,  2, TRUE),
    (10003, NULL, '가구/인테리어',    1,  3, TRUE),
    (10004, NULL, '생활/주방',        1,  4, TRUE),
    (10005, NULL, '유아동',           1,  5, TRUE),
    (10006, NULL, '유아도서',         1,  6, TRUE),
    (10007, NULL, '여성의류',         1,  7, TRUE),
    (10008, NULL, '여성잡화',         1,  8, TRUE),
    (10009, NULL, '남성패션/잡화',    1,  9, TRUE),
    (10010, NULL, '뷰티/미용',        1, 10, TRUE),
    (10011, NULL, '스포츠/레저',      1, 11, TRUE),
    (10012, NULL, '취미/게임/음반',   1, 12, TRUE),
    (10013, NULL, '도서',             1, 13, TRUE),
    (10014, NULL, '티켓/교환권',      1, 14, TRUE),
    (10015, NULL, 'e쿠폰',            1, 15, TRUE),
    (10016, NULL, '가공식품',         1, 16, TRUE),
    (10017, NULL, '건강기능식품',     1, 17, TRUE),
    (10018, NULL, '반려동물용품',     1, 18, TRUE),
    (10019, NULL, '식물',             1, 19, TRUE),
    (10020, NULL, '기타',             1, 20, TRUE)
ON DUPLICATE KEY UPDATE
    parent_id = VALUES(parent_id),
    name = VALUES(name),
    depth = VALUES(depth),
    sort_order = VALUES(sort_order),
    active = VALUES(active);
