-- 농산물(10021) 카테고리를 목록 16번째로 올린다 (AI 파트 요청).
-- 기존 sort_order 16 이상 항목(가공식품~기타)을 한 칸씩 뒤로 밀어 16 자리를 비운 뒤,
-- 농산물을 16으로 지정한다. sort_order 는 유니크 제약이 없어 +1 이동으로 충돌하지 않는다.
--
-- 변경 전:                        변경 후:
--   15 e쿠폰                        15 e쿠폰
--   16 가공식품                     16 농산물
--   17 건강기능식품                 17 가공식품
--   18 반려동물용품                 18 건강기능식품
--   19 식물                         19 반려동물용품
--   20 기타                         20 식물
--   21 농산물                       21 기타

UPDATE categories
   SET sort_order = sort_order + 1
 WHERE sort_order >= 16
   AND category_id <> 10021;

UPDATE categories
   SET sort_order = 16
 WHERE category_id = 10021;
