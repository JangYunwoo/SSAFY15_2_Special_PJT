package com.gongguhan.domain.returnrequest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
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
