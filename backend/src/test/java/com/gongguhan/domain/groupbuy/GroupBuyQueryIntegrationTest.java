package com.gongguhan.domain.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.groupbuy.dto.GroupBuySearchCondition;
import com.gongguhan.domain.groupbuy.dto.GroupBuySort;
import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
class GroupBuyQueryIntegrationTest {

    private static final Long SELLER_ID = 10L;
    private static final Long OTHER_SELLER_ID = 11L;
    private static final Long BUYER_ID = 20L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupBuyQueryRepository groupBuyQueryRepository;

    @BeforeEach
    void setUp() {
        addGroupBuyQueryColumns();
        createReadSideTables();
        insertUsersAndSellers();
        insertCatalog();
        insertGroupBuys();
        insertParticipation();
    }

    @Test
    void sellerNameSearchReturnsProductsWhoseNamesDoNotContainSellerName() throws Exception {
        jdbcTemplate.update("UPDATE seller_profiles SET business_name='키친앤라이프' WHERE seller_id=?", SELLER_ID);
        mockMvc.perform(get("/group-buys").param("keyword", "키친앤라이프").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/group-buys").param("keyword", "키친앤라이프").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void sellerMatchDoesNotBypassCategoryDeletedProductOrGroupBuyStatus() throws Exception {
        jdbcTemplate.update("UPDATE seller_profiles SET business_name='Audit Shop' WHERE seller_id=?", SELLER_ID);
        mockMvc.perform(get("/group-buys").param("keyword", "audit shop").param("categoryId", "11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(2));
        jdbcTemplate.update("UPDATE products SET status='DELETED' WHERE product_id=301");
        jdbcTemplate.update("UPDATE group_buys SET status='FAILED' WHERE group_buy_id=1002");
        mockMvc.perform(get("/group-buys").param("keyword", "audit shop").param("categoryId", "11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void sellerMatchStillExcludesSuspendedSellers() throws Exception {
        jdbcTemplate.update("UPDATE seller_profiles SET business_name='Audit Shop', status='SUSPENDED' WHERE seller_id=?", SELLER_ID);
        mockMvc.perform(get("/group-buys").param("keyword", "Audit Shop"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void sellerSearchTreatsWildcardCharactersLiterallyAndSupportsOneCharacter() throws Exception {
        jdbcTemplate.update("UPDATE seller_profiles SET business_name='샵_Audit!50%' WHERE seller_id=?", SELLER_ID);
        mockMvc.perform(get("/group-buys").param("keyword", "샵_Audit!50%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(3));
        mockMvc.perform(get("/group-buys").param("keyword", "샵"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(3));
        mockMvc.perform(get("/group-buys").param("keyword", "샵%Audit"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void publicListFiltersByCategoryAndProductNameAndSortsByParticipationRate() throws Exception {
        mockMvc.perform(get("/group-buys")
                        .param("keyword", "키보드")
                        .param("categoryId", "11")
                        .param("sort", "popular")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1002))
                .andExpect(jsonPath("$.data[0].productName").value("휴대용 키보드"))
                .andExpect(jsonPath("$.data[0].personalPaymentAmount").value(54_000))
                .andExpect(jsonPath("$.data[1].id").value(1001));
    }

    @Test
    void deadlineSortUsesRecruitEndAtAndPaginates() throws Exception {
        mockMvc.perform(get("/group-buys")
                        .param("sort", "deadline")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.data[0].id").value(1002))
                .andExpect(jsonPath("$.data[1].id").value(1001));
    }

    @Test
    void publicDetailIncludesAmountsProgressAndWithdrawalRestriction() throws Exception {
        mockMvc.perform(get("/group-buys/{groupBuyId}", 1001L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("무선 키보드"))
                .andExpect(jsonPath("$.data.unitShareAmount").value(50_000))
                .andExpect(jsonPath("$.data.shippingFee").value(4_000))
                .andExpect(jsonPath("$.data.personalPaymentAmount").value(54_000))
                .andExpect(jsonPath("$.data.paidCount").value(5))
                .andExpect(jsonPath("$.data.targetCount").value(20))
                .andExpect(jsonPath("$.data.withdrawalRestricted").value(true))
                .andExpect(jsonPath("$.data.withdrawalRestrictionReason").value("주문 제작 상품"))
                .andExpect(jsonPath("$.data.images[0]").value("https://img/keyboard-main.jpg"));
    }

    @Test
    void sellerCanReadOwnGroupBuysAndParticipantStatus() throws Exception {
        UsernamePasswordAuthenticationToken seller = seller(SELLER_ID);

        mockMvc.perform(get("/sellers/group-buys")
                        .with(authentication(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(3))
                .andExpect(jsonPath("$.data[0].paidCount").isNumber());

        mockMvc.perform(get("/sellers/group-buys/{groupBuyId}/participants", 1001L)
                        .with(authentication(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.data[0].buyerName").value("구매자"))
                .andExpect(jsonPath("$.data[0].recipientName").value("김구매"))
                .andExpect(jsonPath("$.data[0].amount").value(54_000))
                .andExpect(jsonPath("$.data[0].status").value("PAID"));
    }

    @Test
    void sellerCannotReadAnotherSellersParticipants() throws Exception {
        mockMvc.perform(get("/sellers/group-buys/{groupBuyId}/participants", 1001L)
                        .with(authentication(seller(OTHER_SELLER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void normalListLoadRespondsWithinTwoSeconds() {
        jdbcTemplate.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, product_price, target_participants,
                    unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                    expected_platform_fee, expected_settlement_amount, recruit_start_at,
                    recruit_end_at, current_participants, reserved_slots, status, version,
                    created_at, updated_at
                )
                SELECT x + 10000, 301, 10, 1000000, 20,
                       50000, 4000, 0, 0.05, 50000, 1030000,
                       TIMESTAMP '2026-09-01 00:00:00', TIMESTAMP '2026-12-31 00:00:00',
                       MOD(x, 20), 0, 'RECRUITING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM SYSTEM_RANGE(1, 2000) AS generated(x)
                """);
        GroupBuySearchCondition condition =
                new GroupBuySearchCondition(null, null, "RECRUITING", GroupBuySort.DEADLINE);

        Instant startedAt = Instant.now();
        Page<GroupBuySummaryResponse> result =
                groupBuyQueryRepository.findPublicGroupBuys(condition, PageRequest.of(0, 20));
        Duration elapsed = Duration.between(startedAt, Instant.now());

        assertThat(result.getContent()).hasSize(20);
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void deadlineQueryExecutionPlanUsesStatusEndIndex() {
        String plan = jdbcTemplate.queryForObject("""
                EXPLAIN SELECT group_buy_id
                          FROM group_buys
                         WHERE status = 'RECRUITING'
                         ORDER BY recruit_end_at
                         LIMIT 20
                """, String.class);

        assertThat(plan).containsIgnoringCase("idx_group_buys_status_end");
    }

    private void createReadSideTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS seller_profiles (
                    seller_id BIGINT PRIMARY KEY,
                    status VARCHAR(20) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE seller_profiles
                ADD COLUMN IF NOT EXISTS business_name VARCHAR(100) DEFAULT '상점' NOT NULL
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS withdrawal_restriction_reasons (
                    reason_id BIGINT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS participations (
                    participation_id BIGINT PRIMARY KEY,
                    group_buy_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    share_amount BIGINT NOT NULL,
                    shipping_fee BIGINT NOT NULL,
                    total_amount BIGINT NOT NULL,
                    recipient_name VARCHAR(50) NOT NULL,
                    recipient_phone VARCHAR(20) NOT NULL,
                    zipcode CHAR(5) NOT NULL,
                    address1 VARCHAR(255) NOT NULL,
                    address2 VARCHAR(255),
                    status VARCHAR(20) NOT NULL,
                    joined_at TIMESTAMP NOT NULL,
                    delivered_at TIMESTAMP,
                    withdrawal_period_end_at TIMESTAMP,
                    confirmed_at TIMESTAMP,
                    confirm_type VARCHAR(10)
                )
                """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_participation_group ON participations(group_buy_id, status)");
    }

    private void insertUsersAndSellers() {
        insertUser(SELLER_ID, "seller@test.com", "판매자");
        insertUser(OTHER_SELLER_ID, "other@test.com", "다른 판매자");
        insertUser(BUYER_ID, "buyer@test.com", "구매자");
        jdbcTemplate.update(
                "MERGE INTO seller_profiles (seller_id, business_name, status, created_at, updated_at) "
                        + "KEY(seller_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SELLER_ID, "키보드 상점", "ACTIVE");
        jdbcTemplate.update(
                "MERGE INTO seller_profiles (seller_id, business_name, status, created_at, updated_at) "
                        + "KEY(seller_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                OTHER_SELLER_ID, "다른 상점", "ACTIVE");
    }

    private void insertUser(Long id, String email, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    user_id, email, password_hash, nickname, phone_verified,
                    is_seller, status, created_at, updated_at
                ) VALUES (?, ?, 'password', ?, FALSE, FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, email, nickname);
    }

    private void insertCatalog() {
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, name, depth, sort_order) VALUES (11, '전자제품', 1, 1)");
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, name, depth, sort_order) VALUES (12, '생활', 1, 2)");
        jdbcTemplate.update("""
                INSERT INTO withdrawal_restriction_reasons
                    (reason_id, code, name, requires_evidence, active, sort_order)
                VALUES (1, 'CUSTOM_MADE', '주문 제작 상품', TRUE, TRUE, 1)
                """);
        insertProduct(301L, 11L, "무선 키보드", true);
        insertProduct(302L, 11L, "휴대용 키보드", false);
        insertProduct(303L, 12L, "캠핑 테이블", false);
        jdbcTemplate.update("""
                INSERT INTO product_images (
                    image_id, product_id, image_url, sort_order, is_thumbnail, created_at
                ) VALUES (1, 301, 'https://img/keyboard-main.jpg', 0, TRUE, CURRENT_TIMESTAMP)
                """);
    }

    private void insertProduct(Long productId, Long categoryId, String name, boolean restricted) {
        jdbcTemplate.update("""
                INSERT INTO products (
                    product_id, seller_id, category_id, name, description, price,
                    ai_extracted, withdrawal_restricted, restriction_reason_id,
                    restriction_review_status, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, '상품 설명', 120000, FALSE, ?, ?, ?, 'ACTIVE',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                productId,
                SELLER_ID,
                categoryId,
                name,
                restricted,
                restricted ? 1L : null,
                restricted ? "APPROVED" : "NONE");
    }

    private void insertGroupBuys() {
        insertGroupBuy(1001L, 301L, 20, 5, "2026-09-10 00:00:00");
        insertGroupBuy(1002L, 302L, 10, 8, "2026-09-09 00:00:00");
        insertGroupBuy(1003L, 303L, 20, 15, "2026-09-11 00:00:00");
    }

    private void insertGroupBuy(Long id, Long productId, int targetCount, int paidCount, String recruitEndAt) {
        jdbcTemplate.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, product_price, target_participants,
                    unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                    expected_platform_fee, expected_settlement_amount, recruit_start_at,
                    recruit_end_at, current_participants, reserved_slots, status, version,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 1000000, ?, 50000, 4000, 0, 0.05,
                          50000, 1030000, TIMESTAMP '2026-09-01 00:00:00', ?, ?, 0,
                          'RECRUITING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, productId, SELLER_ID, targetCount, recruitEndAt, paidCount);
    }

    private void insertParticipation() {
        jdbcTemplate.update("""
                INSERT INTO participations (
                    participation_id, group_buy_id, user_id, share_amount, shipping_fee,
                    total_amount, recipient_name, recipient_phone, zipcode, address1,
                    address2, status, joined_at
                ) VALUES (5001, 1001, 20, 50000, 4000, 54000, '김구매', '01012345678',
                          '06236', '서울시 강남구', '101호', 'PAID', TIMESTAMP '2026-09-02 00:00:00')
                """);
    }

    private void addGroupBuyQueryColumns() {
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS product_price BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS rounding_diff BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS platform_fee_rate DECIMAL(5, 4)");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS expected_platform_fee BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS expected_settlement_amount BIGINT");
        jdbcTemplate.execute("ALTER TABLE group_buys ADD COLUMN IF NOT EXISTS recruit_start_at TIMESTAMP");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_group_buys_status_end ON group_buys(status, recruit_end_at)");
    }

    private UsernamePasswordAuthenticationToken seller(Long sellerId) {
        return new UsernamePasswordAuthenticationToken(
                sellerId, null, List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
    }
}
