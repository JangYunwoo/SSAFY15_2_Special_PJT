-- GRP-01 상품명 검색이 테이블 전체 스캔으로 퇴화하지 않도록 한글 n-gram 전문 인덱스를 사용한다.
ALTER TABLE products
    ADD FULLTEXT INDEX ft_products_name (name) WITH PARSER ngram;
