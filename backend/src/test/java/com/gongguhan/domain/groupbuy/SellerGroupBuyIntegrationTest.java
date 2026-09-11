package com.gongguhan.domain.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
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
class SellerGroupBuyIntegrationTest {

    private static final Long SELLER_ID = 10L;
    private static final Long PRODUCT_ID = 301L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupBuyRepository groupBuyRepository;

    @BeforeEach
    void setUp() {
        addGroupBuyCreationColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS seller_profiles (
                    seller_id BIGINT PRIMARY KEY,
                    status VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.update(
                "MERGE INTO seller_profiles (seller_id, status, created_at, updated_at) "
                        + "KEY (seller_id) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SELLER_ID,
                "ACTIVE");
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, name, depth, sort_order) VALUES (?, ?, ?, ?)",
                1L,
                "가전",
                1,
                1);
        jdbcTemplate.update("""
                        INSERT INTO products (
                            product_id, seller_id, category_id, name, price,
                            ai_extracted, withdrawal_restricted, restriction_review_status,
                            status, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                PRODUCT_ID,
                SELLER_ID,
                1L,
                "테스트 상품",
                1_000_001L,
                false,
                false,
                "NONE",
                "ACTIVE");
    }

    @Test
    void sellerCanPreviewAndCreateGroupBuy() throws Exception {
        UsernamePasswordAuthenticationToken seller = new UsernamePasswordAuthenticationToken(
                SELLER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")));

        mockMvc.perform(post("/sellers/group-buys/preview")
                        .with(authentication(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 301,
                                  "productPrice": 1000001,
                                  "targetCount": 20,
                                  "shippingFee": 4000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unitShareAmount").value(50_001))
                .andExpect(jsonPath("$.data.roundingSurplus").value(19))
                .andExpect(jsonPath("$.data.estimatedPlatformFee").value(50_001))
                .andExpect(jsonPath("$.data.estimatedSettlementAmount").value(1_030_019));

        mockMvc.perform(post("/sellers/group-buys")
                        .with(authentication(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 301,
                                  "productPrice": 1000001,
                                  "targetCount": 20,
                                  "shippingFee": 4000,
                                  "recruitStartAt": "2026-09-08T01:00:00Z",
                                  "recruitEndAt": "2026-09-15T01:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.groupBuyId").isNumber())
                .andExpect(jsonPath("$.data.recruitStartAt").value("2026-09-08T01:00:00Z"))
                .andExpect(jsonPath("$.data.status").value(GroupBuy.STATUS_RECRUITING));

        List<GroupBuy> saved = groupBuyRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(saved.getFirst().getUnitShareAmount()).isEqualTo(50_001L);
        assertThat(saved.getFirst().getCreatedAt()).isNotNull();
        assertThat(saved.getFirst().getUpdatedAt()).isNotNull();
        Long expectedSettlementAmount = jdbcTemplate.queryForObject(
                "SELECT expected_settlement_amount FROM group_buys WHERE group_buy_id = ?",
                Long.class,
                saved.getFirst().getGroupBuyId());
        assertThat(expectedSettlementAmount).isEqualTo(1_030_019L);
    }

    private void addGroupBuyCreationColumns() {
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS product_price BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS rounding_diff BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS platform_fee_rate DECIMAL(5, 4)");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS expected_platform_fee BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS expected_settlement_amount BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS recruit_start_at TIMESTAMP");
    }
}
