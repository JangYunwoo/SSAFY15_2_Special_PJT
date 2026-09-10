package com.gongguhan.domain.product;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WithdrawalRestrictionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO categories (category_id, name, depth, sort_order) VALUES (1, '식품', 1, 1)");
        jdbcTemplate.update("""
                INSERT INTO withdrawal_restriction_reasons
                    (reason_id, code, name, description, requires_evidence, active, sort_order)
                VALUES (1, 'PERISHABLE', '변질 우려 상품', '신선식품', TRUE, TRUE, 2),
                       (2, 'DIGITAL', '디지털 콘텐츠', '즉시 이용', FALSE, TRUE, 1),
                       (3, 'INACTIVE', '비활성', '노출 금지', FALSE, FALSE, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO products
                    (product_id, seller_id, category_id, name, price, ai_extracted,
                     withdrawal_restricted, restriction_reason_id, restriction_evidence_url,
                     restriction_review_status, status, created_at, updated_at)
                VALUES (10, 20, 1, '신선 상품', 10000, FALSE, TRUE, 1,
                        'https://evidence.example/item', 'PENDING', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    @Test
    void sellerCanReadOnlyActiveReasonsInConfiguredOrder() throws Exception {
        mockMvc.perform(get("/withdrawal-restriction-reasons")
                        .with(authentication(auth(20L, "ROLE_SELLER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("DIGITAL"))
                .andExpect(jsonPath("$.data[1].requiresEvidence").value(true));
    }

    @Test
    void adminCanListAndApprovePendingRestriction() throws Exception {
        var admin = authentication(auth(99L, "ROLE_ADMIN"));

        mockMvc.perform(get("/admin/products/withdrawal-restrictions").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productId").value("10"))
                .andExpect(jsonPath("$.data[0].withdrawalRestrictionReason").value("변질 우려 상품"))
                .andExpect(jsonPath("$.data[0].restrictionReviewStatus").value("PENDING"));

        mockMvc.perform(post("/admin/products/10/withdrawal-restriction/review")
                        .with(authentication(auth(99L, "ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restrictionReviewStatus").value("APPROVED"));

        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT restriction_reviewed_by FROM products WHERE product_id = 10", Long.class))
                .isEqualTo(99L);
    }

    private UsernamePasswordAuthenticationToken auth(Long id, String role) {
        return new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
    }
}
