package com.gongguhan.domain.returnrequest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReturnRequestRepositoryTest {

    private JdbcTemplate jdbc;
    private ReturnRequestRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:return-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        recreateSchema();
        repository = new ReturnRequestRepository(new NamedParameterJdbcTemplate(dataSource));
        insertFixture();
    }

    @Test
    void readsReturnContextWithoutDependingOnOtherDomainEntities() {
        var context = repository.findContext(30L, true).orElseThrow();

        assertThat(context.userId()).isEqualTo(10L);
        assertThat(context.sellerId()).isEqualTo(20L);
        assertThat(context.returnShippingFee()).isEqualTo(6_000L);
        assertThat(context.withdrawalRestricted()).isFalse();
    }

    @Test
    void createsRequestAndReturnsFrontendContractDetailWithHistory() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 11, 5, 0);
        Long returnId = repository.create(new CreateCommand(
                30L, "CHANGE_OF_MIND", "단순 변심", 6_000, "BUYER", 44_000,
                false, false, requestedAt, requestedAt.plusDays(5),
                "06236", "서울시 강남구", "101호"));
        repository.holdConfirmation(30L);
        repository.addHistory(returnId, "REQUESTED", null, requestedAt);

        var detail = repository.findBuyerDetail(10L, returnId).orElseThrow();

        assertThat(detail.productName()).isEqualTo("공동구매 상품");
        assertThat(detail.buyerName()).isEqualTo("구매자");
        assertThat(detail.pickupZipcode()).isEqualTo("06236");
        assertThat(detail.history()).extracting("status").containsExactly("REQUESTED");
        assertThat(jdbc.queryForObject(
                "SELECT confirm_hold FROM participations WHERE participation_id = 30",
                Boolean.class)).isTrue();
    }

    @Test
    void automaticApprovalIsConditionalAndMovesParticipationToReturning() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = repository.create(new CreateCommand(
                30L, "DEFECT", "파손", 6_000, "SELLER", 53_000,
                false, false, requestedAt, requestedAt.plusDays(3),
                "06236", "서울시 강남구", null));

        assertThat(repository.approveAutomatically(returnId, requestedAt.plusDays(4))).isTrue();
        assertThat(repository.approveAutomatically(returnId, requestedAt.plusDays(5))).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM participations WHERE participation_id = 30", String.class))
                .isEqualTo("RETURNING");
    }

    @Test
    void refundTransitionLinksPaymentContextAndParticipationStatus() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 9, 1, 5, 0);
        Long returnId = repository.create(new CreateCommand(
                30L, "DEFECT", "파손", 6_000, "SELLER", 53_000,
                false, false, requestedAt, requestedAt.plusDays(3),
                "06236", "서울시 강남구", null));
        jdbc.update("UPDATE return_requests SET status = 'APPROVED' WHERE return_id = ?", returnId);

        var context = repository.findSellerRefundContext(20L, returnId).orElseThrow();
        assertThat(context.paymentMethod()).isEqualTo("KAKAOPAY");
        assertThat(context.paymentId()).isEqualTo(50L);

        assertThat(repository.markRefundRequested(returnId, false, requestedAt.plusDays(1))).isTrue();
        assertThat(repository.markRefunded(returnId, requestedAt.plusDays(1))).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM participations WHERE participation_id = 30", String.class))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT confirm_hold FROM participations WHERE participation_id = 30", Boolean.class))
                .isFalse();
    }

    private void recreateSchema() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY, nickname VARCHAR(50) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE products (
                    product_id BIGINT PRIMARY KEY, name VARCHAR(200) NOT NULL,
                    withdrawal_restricted BOOLEAN NOT NULL,
                    restriction_review_status VARCHAR(20) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE group_buys (
                    group_buy_id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL,
                    seller_id BIGINT NOT NULL, shipping_fee BIGINT NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE participations (
                    participation_id BIGINT PRIMARY KEY, group_buy_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL, share_amount BIGINT NOT NULL,
                    shipping_fee BIGINT NOT NULL, total_amount BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL, withdrawal_period_end_at DATETIME,
                    settlement_eligible_at DATETIME, confirm_hold BOOLEAN NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE return_requests (
                    return_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    participation_id BIGINT NOT NULL, reason_type VARCHAR(20) NOT NULL,
                    reason_detail VARCHAR(500), return_shipping_fee BIGINT NOT NULL,
                    fee_bearer VARCHAR(10) NOT NULL, refund_amount BIGINT NOT NULL,
                    post_confirm BOOLEAN NOT NULL, admin_review_required BOOLEAN NOT NULL,
                    status VARCHAR(20) NOT NULL, requested_at DATETIME NOT NULL,
                    auto_approve_at DATETIME, auto_refund_due_at DATETIME,
                    approved_at DATETIME, approve_type VARCHAR(10),
                    picked_up_at DATETIME, auto_refunded BOOLEAN DEFAULT FALSE,
                    completed_at DATETIME,
                    reject_reason VARCHAR(255), pickup_zipcode CHAR(5),
                    pickup_address1 VARCHAR(255), pickup_address2 VARCHAR(255))
                """);
        jdbc.execute("""
                CREATE TABLE payments (
                    payment_id BIGINT PRIMARY KEY, participation_id BIGINT,
                    method VARCHAR(20) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE return_request_histories (
                    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    return_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL,
                    note VARCHAR(500), occurred_at DATETIME NOT NULL)
                """);
    }

    private void insertFixture() {
        jdbc.update("INSERT INTO users VALUES (10, '구매자')");
        jdbc.update("INSERT INTO products VALUES (40, '공동구매 상품', FALSE, 'NONE')");
        jdbc.update("INSERT INTO group_buys VALUES (31, 40, 20, 3000)");
        jdbc.update("""
                INSERT INTO participations VALUES (
                    30, 31, 10, 50000, 3000, 53000, 'DELIVERED',
                    '2026-09-18 05:00:00', '2026-09-19 05:00:00', FALSE)
                """);
        jdbc.update("INSERT INTO payments VALUES (50, 30, 'KAKAOPAY')");
    }
}
