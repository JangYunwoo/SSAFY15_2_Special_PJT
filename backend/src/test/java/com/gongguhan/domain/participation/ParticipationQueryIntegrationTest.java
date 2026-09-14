package com.gongguhan.domain.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.participation.dto.ParticipationDetailResponse;
import com.gongguhan.domain.participation.dto.ParticipationSummaryResponse;
import com.gongguhan.domain.participation.service.ParticipationQueryService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAR-01·02 조인 SQL이 실제로 실행되는지 H2(MODE=MySQL)에서 검증한다.
 * 스캐폴딩은 {@link com.gongguhan.domain.groupbuy.GroupBuyQueryIntegrationTest}와 같은 방식.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParticipationQueryIntegrationTest {

    private static final Long SELLER_ID = 10L;
    private static final Long BUYER_ID = 20L;
    private static final Long OTHER_BUYER_ID = 21L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ParticipationQueryService participationQueryService;

    @BeforeEach
    void setUp() {
        seedPolicy();
        createSellerProfiles();
        insertUsers();
        insertCatalogAndGroupBuy();
        insertDeliveredParticipation();
        insertPaidParticipation();
    }

    /** 테스트 프로필은 flyway가 꺼져 있어 V1 시드가 없다 - 조회에 필요한 정책만 넣는다. */
    private void seedPolicy() {
        jdbcTemplate.update("""
                MERGE INTO platform_policies (policy_key, policy_value, updated_at)
                KEY(policy_key) VALUES ('AUTO_CONFIRM_DAYS', '7', CURRENT_TIMESTAMP)
                """);
    }

    @Test
    void listReturnsMyParticipationsWithJoinedFields() {
        Page<ParticipationSummaryResponse> page =
                participationQueryService.getMyParticipations(BUYER_ID, null, 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(2);
        // joined_at 내림차순 - 최근 참여(PAID, 5002)가 먼저 온다.
        ParticipationSummaryResponse paid = page.getContent().get(0);
        assertThat(paid.id()).isEqualTo(5002L);
        assertThat(paid.status()).isEqualTo("PAID");

        ParticipationSummaryResponse delivered = page.getContent().get(1);
        assertThat(delivered.id()).isEqualTo(5001L);
        assertThat(delivered.productName()).isEqualTo("무선 키보드");
        assertThat(delivered.sellerName()).isEqualTo("키보드 상점");
        assertThat(delivered.escrowStatus()).isEqualTo("HELD");
        assertThat(delivered.method()).isEqualTo("CARD");
        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(delivered.deliveredAt()).isNotNull();
        assertThat(delivered.autoConfirmAt()).isEqualTo(delivered.deliveredAt().plusDays(7));
        assertThat(delivered.autoConfirmed()).isFalse();
    }

    @Test
    void listFiltersByStatus() {
        Page<ParticipationSummaryResponse> page =
                participationQueryService.getMyParticipations(BUYER_ID, "DELIVERED", 0, 20);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(5001L);
    }

    @Test
    void listExcludesOtherUsersParticipations() {
        Page<ParticipationSummaryResponse> page =
                participationQueryService.getMyParticipations(OTHER_BUYER_ID, null, 0, 20);

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void detailIncludesRecipientHistoryAndActionFlags() {
        ParticipationDetailResponse detail =
                participationQueryService.getMyParticipationDetail(BUYER_ID, 5001L);

        assertThat(detail.paymentId()).isEqualTo(9001L);
        assertThat(detail.recipientName()).isEqualTo("김구매");
        assertThat(detail.history()).extracting("status")
                .containsExactly("PAID", "FULFILLED", "SHIPPING", "DELIVERED");
        assertThat(detail.canCancel()).isFalse();
        assertThat(detail.canConfirm()).isTrue();
        assertThat(detail.canRequestReturn()).isTrue();
    }

    @Test
    void detailDeniedForOtherUser() {
        assertThatThrownBy(() -> participationQueryService.getMyParticipationDetail(OTHER_BUYER_ID, 5001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_ACCESS_DENIED));
    }

    private void createSellerProfiles() {
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
        jdbcTemplate.update(
                "MERGE INTO seller_profiles (seller_id, business_name, status, created_at, updated_at) "
                        + "KEY(seller_id) VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                SELLER_ID, "키보드 상점", "ACTIVE");
    }

    private void insertUsers() {
        insertUser(SELLER_ID, "seller@test.com", "판매자");
        insertUser(BUYER_ID, "buyer@test.com", "구매자");
        insertUser(OTHER_BUYER_ID, "other-buyer@test.com", "다른 구매자");
    }

    private void insertUser(Long id, String email, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO users (
                    user_id, email, password_hash, nickname, phone_verified,
                    is_seller, status, created_at, updated_at
                ) VALUES (?, ?, 'password', ?, FALSE, FALSE, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, email, nickname);
    }

    private void insertCatalogAndGroupBuy() {
        jdbcTemplate.update(
                "INSERT INTO categories (category_id, name, depth, sort_order) VALUES (11, '전자제품', 1, 1)");
        jdbcTemplate.update("""
                INSERT INTO products (
                    product_id, seller_id, category_id, name, description, price,
                    ai_extracted, withdrawal_restricted, restriction_review_status,
                    status, created_at, updated_at
                ) VALUES (301, ?, 11, '무선 키보드', '상품 설명', 120000,
                          FALSE, FALSE, 'NONE', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, SELLER_ID);
        jdbcTemplate.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, target_participants, unit_share_amount,
                    shipping_fee, current_participants, reserved_slots, recruit_end_at,
                    fulfilled_at, status, version, created_at, updated_at
                ) VALUES (1001, 301, ?, 2, 50000, 4000, 2, 0, TIMESTAMP '2026-09-05 00:00:00',
                          TIMESTAMP '2026-09-05 00:00:00', 'FULFILLED', 0,
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, SELLER_ID);
    }

    /** 배송 완료까지 진행된 참여: 에스크로 HELD, 카드 결제, 송장·배송 이력 포함. */
    private void insertDeliveredParticipation() {
        jdbcTemplate.update("""
                INSERT INTO participations (
                    participation_id, group_buy_id, user_id, share_amount, shipping_fee,
                    total_amount, recipient_name, recipient_phone, zipcode, address1, address2,
                    status, joined_at, delivered_at, withdrawal_period_end_at,
                    confirm_notice_agreed, confirm_hold
                ) VALUES (5001, 1001, ?, 50000, 4000, 54000, '김구매', '01012345678',
                          '06236', '서울시 강남구', '101호', 'DELIVERED',
                          TIMESTAMP '2026-09-02 00:00:00', TIMESTAMP '2026-09-08 00:00:00',
                          TIMESTAMP '2099-09-15 00:00:00', FALSE, FALSE)
                """, BUYER_ID);
        jdbcTemplate.update("""
                INSERT INTO escrows (escrow_id, participation_id, group_buy_id, amount, status, held_at)
                VALUES (7001, 5001, 1001, 54000, 'HELD', TIMESTAMP '2026-09-02 00:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO payments (
                    payment_id, group_buy_id, user_id, participation_id, method, krw_amount,
                    slot_expires_at, idempotency_key, status, created_at, updated_at
                ) VALUES (9001, 1001, ?, 5001, 'CARD', 54000,
                          TIMESTAMP '2026-09-02 00:05:00', 'idem-5001', 'PAID',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, BUYER_ID);
        jdbcTemplate.update("""
                INSERT INTO deliveries (
                    delivery_id, participation_id, courier_code, tracking_number,
                    shipped_at, delivered_at, status
                ) VALUES (8001, 5001, 'CJ', '123456789012',
                          TIMESTAMP '2026-09-06 00:00:00', TIMESTAMP '2026-09-08 00:00:00', 'DELIVERED')
                """);
    }

    /** 결제 직후 상태의 참여: 에스크로·배송 행 없음(LEFT JOIN null 경로). */
    private void insertPaidParticipation() {
        jdbcTemplate.update("""
                INSERT INTO participations (
                    participation_id, group_buy_id, user_id, share_amount, shipping_fee,
                    total_amount, recipient_name, recipient_phone, zipcode, address1,
                    status, joined_at, confirm_notice_agreed, confirm_hold
                ) VALUES (5002, 1001, ?, 50000, 4000, 54000, '김구매', '01012345678',
                          '06236', '서울시 강남구', 'PAID',
                          TIMESTAMP '2026-09-03 00:00:00', FALSE, FALSE)
                """, BUYER_ID);
    }
}
