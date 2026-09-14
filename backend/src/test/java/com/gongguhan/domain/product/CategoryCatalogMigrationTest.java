package com.gongguhan.domain.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CategoryCatalogMigrationTest {

    private static final List<String> EXPECTED_CATEGORIES = List.of(
            "디지털기기", "생활가전", "가구/인테리어", "생활/주방", "유아동",
            "유아도서", "여성의류", "여성잡화", "남성패션/잡화", "뷰티/미용",
            "스포츠/레저", "취미/게임/음반", "도서", "티켓/교환권", "e쿠폰",
            "가공식품", "건강기능식품", "반려동물용품", "식물", "기타");

    @Test
    void migrationContainsTheCanonicalCategoryCatalogInDisplayOrder() throws Exception {
        String migration = new ClassPathResource(
                "db/migration/V8__seed_product_categories.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        Pattern row = Pattern.compile(
                "\\(\\d+,\\s*NULL,\\s*'([^']+)',\\s*1,\\s*\\d+,\\s*TRUE\\)");
        List<String> actual = row.matcher(migration).results()
                .map(result -> result.group(1))
                .toList();

        assertEquals(EXPECTED_CATEGORIES, actual);
        assertTrue(migration.matches("(?s).*UPDATE\\s+categories\\s+SET\\s+active\\s*=\\s*FALSE.*"));
    }
}
