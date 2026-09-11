package com.gongguhan.domain.returnrequest.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
class ReturnRequestRepositoryTest {

    private static final long BUYER_ID = 930_010L;
    private static final long SELLER_ID = 930_020L;
    private static final long PARTICIPATION_ID = 930_030L;
    private static final long GROUP_BUY_ID = 930_031L;
    private static final long PRODUCT_ID = 930_040L;
    private static final long PAYMENT_ID = 930_050L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReturnRequestRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        insertFixture();
    }

    @Test
    void readsReturnContextThroughJpaAssociations() {
        var context = repository.findContext(PARTICIPATION_ID, true).orElseThrow();

        assertThat(context.userId()).isEqualTo(BUYER_ID);
        assertThat(context.sellerId()).isEqualTo(SELLER_ID);
        assertThat(context.returnShippingFee()).isEqualTo(6_000L);
        assertThat(context.withdrawalRestricted()).isFalse();
    }

    @Test
    void createsRequestAndReturnsFrontendContractDetailWithHistory() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 11, 5, 0);
        Long returnId = createReturn(requestedAt, "CHANGE_OF_MIND", 44_000L);
        repository.holdConfirmation(PARTICIPATION_ID);
        repository.addHistory(returnId, "REQUESTED", null, requestedAt);

        var detail = repository.findBuyerDetail(BUYER_ID, returnId).orElseThrow();

        assertThat(detail.productName()).isEqualTo("공동구매 상품");
        assertThat(detail.buyerName()).isEqualTo("구매자");
        assertThat(detail.pickupZipcode()).isEqualTo("06236");
        assertThat(detail.history()).extracting("status").containsExactly("REQUESTED");
        assertThat(jdbc.queryForObject(
                "SELECT confirm_hold FROM participations WHERE participation_id = ?",
                Boolean.class, PARTICIPATION_ID)).isTrue();
    }

    @Test
    void automaticApprovalIsConditionalAndMovesParticipationToReturning() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = createReturn(requestedAt, "DEFECT", 53_000L);

        assertThat(repository.approveAutomatically(returnId, requestedAt.plusDays(4))).isTrue();
        assertThat(repository.approveAutomatically(returnId, requestedAt.plusDays(5))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM participations WHERE participation_id = ?",
                String.class, PARTICIPATION_ID)).isEqualTo("RETURNING");
    }

    @Test
    void refundTransitionLinksPaymentContextAndParticipationStatus() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = createReturn(requestedAt, "DEFECT", 53_000L);
        repository.approveAutomatically(returnId, requestedAt.plusDays(4));

        var context = repository.findSellerRefundContext(SELLER_ID, returnId).orElseThrow();
        assertThat(context.paymentMethod()).isEqualTo("KAKAOPAY");
        assertThat(context.paymentId()).isEqualTo(PAYMENT_ID);

        assertThat(repository.markRefundRequested(returnId, false, requestedAt.plusDays(5))).isTrue();
        assertThat(repository.markRefunded(returnId, requestedAt.plusDays(5))).isTrue();
        entityManager.flush();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM participations WHERE participation_id = ?",
                String.class, PARTICIPATION_ID)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT confirm_hold FROM participations WHERE participation_id = ?",
                Boolean.class, PARTICIPATION_ID)).isFalse();
    }

    @Test
    void listProjectionKeepsQueryCountConstant() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        for (int i = 0; i < 25; i++) {
            createReturn(requestedAt.plusMinutes(i), "DEFECT", 53_000L);
        }
        entityManager.flush();
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        var page = repository.findBuyerList(
                BUYER_ID, null, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(20);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).allSatisfy(summary -> {
            assertThat(summary.productName()).isEqualTo("공동구매 상품");
            assertThat(summary.buyerName()).isEqualTo("구매자");
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    }

    @Test
    void disputeRestoresConfirmationHoldUntilAdminDecision() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = createReturn(requestedAt, "DEFECT", 53_000L);
        repository.holdConfirmation(PARTICIPATION_ID);
        repository.reject(returnId, "증빙 부족");
        repository.dispute(returnId);
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT confirm_hold FROM participations WHERE participation_id = ?",
                Boolean.class, PARTICIPATION_ID)).isTrue();
        assertThat(repository.findAdminReviewList(null, null,
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent())
                .extracting("id").contains(returnId);
    }

    @Test
    void adminResolutionRecordsForcedActorAndUpdatesParticipation() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = repository.create(new CreateCommand(
                PARTICIPATION_ID, "DEFECT", "구매확정 후 하자", 6_000,
                "SELLER", 53_000L, true, true, requestedAt, null,
                "06236", "서울시 강남구", "101호"));
        repository.holdConfirmation(PARTICIPATION_ID);

        assertThat(repository.approveByAdmin(returnId, 77L, requestedAt.plusDays(1))).isTrue();
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT forced_by FROM return_requests WHERE return_id = ?",
                Long.class, returnId)).isEqualTo(77L);
        assertThat(jdbc.queryForObject(
                "SELECT admin_review_required FROM return_requests WHERE return_id = ?",
                Boolean.class, returnId)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM participations WHERE participation_id = ?",
                String.class, PARTICIPATION_ID)).isEqualTo("RETURNING");
    }

    @Test
    void adminReviewEndpointsEnforceRoleAndResolveRequest() throws Exception {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = repository.create(new CreateCommand(
                PARTICIPATION_ID, "DEFECT", "구매확정 후 하자", 6_000,
                "SELLER", 53_000L, true, true, requestedAt, null,
                "06236", "서울시 강남구", "101호"));
        repository.holdConfirmation(PARTICIPATION_ID);
        repository.addHistory(returnId, "REQUESTED", "관리자 검토 필요", requestedAt);
        var admin = authentication(new UsernamePasswordAuthenticationToken(
                77L, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        var seller = authentication(new UsernamePasswordAuthenticationToken(
                SELLER_ID, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));

        mockMvc.perform(get("/admin/returns/reviews").with(seller))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/returns/reviews").with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(returnId))
                .andExpect(jsonPath("$.data[0].adminReviewRequired").value(true));

        mockMvc.perform(post("/admin/returns/{returnId}/resolve", returnId)
                        .with(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","reason":"하자 증빙 확인"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.adminReviewRequired").value(false));
    }

    private Long createReturn(LocalDateTime requestedAt, String reason, long refundAmount) {
        return repository.create(new CreateCommand(
                PARTICIPATION_ID, reason, "상세 사유", 6_000,
                "CHANGE_OF_MIND".equals(reason) ? "BUYER" : "SELLER", refundAmount,
                false, false, requestedAt, requestedAt.plusDays(3),
                "06236", "서울시 강남구", "101호"));
    }

    private void insertFixture() {
        insertUser(BUYER_ID, "return-buyer@test.com", "구매자");
        insertUser(SELLER_ID, "return-seller@test.com", "판매자");
        jdbc.update("""
                INSERT INTO categories (category_id, name, depth, sort_order)
                VALUES (930001, '반품 테스트', 1, 1)
                """);
        jdbc.update("""
                INSERT INTO products (
                    product_id, seller_id, category_id, name, price, ai_extracted,
                    withdrawal_restricted, restriction_review_status, status,
                    created_at, updated_at)
                VALUES (?, ?, 930001, '공동구매 상품', 50000, FALSE, FALSE, 'NONE',
                        'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PRODUCT_ID, SELLER_ID);
        jdbc.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, target_participants,
                    unit_share_amount, shipping_fee, current_participants, reserved_slots,
                    recruit_end_at, status, version, created_at, updated_at)
                VALUES (?, ?, ?, 2, 50000, 3000, 2, 0,
                        TIMESTAMP '2026-09-01 00:00:00', 'FULFILLED', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, GROUP_BUY_ID, PRODUCT_ID, SELLER_ID);
        jdbc.update("""
                INSERT INTO participations (
                    participation_id, group_buy_id, user_id, share_amount, shipping_fee,
                    total_amount, recipient_name, recipient_phone, zipcode, address1,
                    status, joined_at, withdrawal_period_end_at, settlement_eligible_at,
                    confirm_notice_agreed, confirm_hold, active_marker)
                VALUES (?, ?, ?, 50000, 3000, 53000, '김구매', '01012345678', '06236',
                        '서울시 강남구', 'DELIVERED', TIMESTAMP '2026-09-01 00:00:00',
                        TIMESTAMP '2026-09-18 05:00:00', TIMESTAMP '2026-09-19 05:00:00',
                        FALSE, FALSE, 'Y')
                """, PARTICIPATION_ID, GROUP_BUY_ID, BUYER_ID);
        jdbc.update("""
                INSERT INTO payments (
                    payment_id, group_buy_id, user_id, participation_id, method, krw_amount,
                    slot_expires_at, idempotency_key, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'KAKAOPAY', 53000,
                        TIMESTAMP '2026-09-01 00:05:00', 'return-repository-test', 'PAID',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PAYMENT_ID, GROUP_BUY_ID, BUYER_ID, PARTICIPATION_ID);
    }

    private void insertUser(long id, String email, String nickname) {
        jdbc.update("""
                INSERT INTO users (
                    user_id, email, password_hash, nickname, phone_verified,
                    is_seller, status, created_at, updated_at)
                VALUES (?, ?, 'password', ?, FALSE, FALSE, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, email, nickname);
    }
}
