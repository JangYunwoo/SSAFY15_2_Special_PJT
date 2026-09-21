package com.gongguhan.domain.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RecommendationCandidateWindowTest {
    private JdbcTemplate jdbc;
    private RecommendationRepository repository;
    private final OffsetDateTime now = OffsetDateTime.parse("2026-09-21T06:00:00Z");

    @BeforeEach void database() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(ds);
        repository = new RecommendationRepository(new NamedParameterJdbcTemplate(ds));
        jdbc.execute("CREATE TABLE categories (category_id BIGINT PRIMARY KEY, name VARCHAR(30))");
        jdbc.execute("CREATE TABLE seller_profiles (seller_id BIGINT PRIMARY KEY, business_name VARCHAR(30), status VARCHAR(30))");
        jdbc.execute("CREATE TABLE products (product_id BIGINT PRIMARY KEY, name VARCHAR(30), brand VARCHAR(30), category_id BIGINT, price BIGINT, status VARCHAR(30))");
        jdbc.execute("CREATE TABLE product_images (image_id BIGINT, product_id BIGINT, image_url VARCHAR(200), is_thumbnail BOOLEAN, sort_order INT)");
        jdbc.execute("""
                CREATE TABLE group_buys (group_buy_id BIGINT PRIMARY KEY, product_id BIGINT, seller_id BIGINT,
                status VARCHAR(30), product_price BIGINT, unit_share_amount BIGINT, shipping_fee BIGINT,
                target_participants INT, current_participants INT, recruit_start_at TIMESTAMP,
                recruit_end_at TIMESTAMP, created_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO categories VALUES (1,'전자기기')");
        jdbc.update("INSERT INTO seller_profiles VALUES (1,'판매자','APPROVED')");
        jdbc.update("INSERT INTO products VALUES (1,'상품','브랜드',1,10000,'ACTIVE')");
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    private void insert(long id, OffsetDateTime start, OffsetDateTime end) {
        jdbc.update("""
                INSERT INTO group_buys VALUES (?,1,1,'RECRUITING',9000,9000,0,5,1,?,?,?)
                """, id, Timestamp.valueOf(start.toLocalDateTime()), Timestamp.valueOf(end.toLocalDateTime()),
                Timestamp.valueOf(now.toLocalDateTime()));
    }

    @Test void startIsInclusiveAndEndIsExclusive() {
        insert(1, now.minusDays(1), now.plusDays(1));
        insert(2, now, now.plusDays(1));
        insert(3, now.plusSeconds(1), now.plusDays(1));
        insert(4, now.minusDays(1), now);
        insert(5, now.minusDays(2), now.minusSeconds(1));
        assertThat(repository.findCandidates(10, now)).extracting(RecommendationCandidate::groupBuyId)
                .containsExactly(2L, 1L);
    }

    @Test void filteringHappensBeforeLimit() {
        insert(1, now.minusDays(1), now.plusDays(1));
        insert(2, now.plusDays(1), now.plusDays(2));
        assertThat(repository.findCandidates(1, now)).extracting(RecommendationCandidate::groupBuyId)
                .containsExactly(1L);
    }

    @Test void closedItemSnapshotsRemainAvailableForHistoricalEvents() {
        insert(1, now.minusDays(2), now.minusDays(1));
        assertThat(repository.findCandidates(10, now)).isEmpty();
        assertThat(repository.findCandidateSnapshots(Set.of(1L))).containsKey(1L);
    }

    @Test void existingVisibilityRulesStillApply() {
        insert(1, now.minusDays(1), now.plusDays(1));
        jdbc.update("UPDATE products SET status='DELETED'");
        assertThat(repository.findCandidates(10, now)).isEmpty();
        jdbc.update("UPDATE products SET status='ACTIVE'");
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED'");
        assertThat(repository.findCandidates(10, now)).isEmpty();
        jdbc.update("UPDATE seller_profiles SET status='APPROVED'");
        jdbc.update("UPDATE group_buys SET status='SUCCESS'");
        assertThat(repository.findCandidates(10, now)).isEmpty();
    }
}
