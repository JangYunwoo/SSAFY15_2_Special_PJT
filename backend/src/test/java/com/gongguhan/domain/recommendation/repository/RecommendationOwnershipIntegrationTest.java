package com.gongguhan.domain.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.recommendation.dto.RecommendationEventItem;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class RecommendationOwnershipIntegrationTest {
    private JdbcTemplate jdbc;
    private RecommendationRepository repository;
    private TransactionTemplate transaction;
    private final String session = UUID.randomUUID().toString();
    private final List<RecommendationEventItem> events = List.of(new RecommendationEventItem(1L, "CLICK", "HOME", 0));
    private final Map<Long, RecommendationCandidate> snapshots = Map.of(1L,
            new RecommendationCandidate(1, 11, null, 1L, .2, .1, .1, null));

    @BeforeEach void isolatedDatabase() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;NON_KEYWORDS=POSITION", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(ds);
        repository = new RecommendationRepository(new NamedParameterJdbcTemplate(ds));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.execute("""
                CREATE TABLE recommendation_sessions (
                  session_id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL,
                  started_at TIMESTAMP, last_event_at TIMESTAMP, ended_at TIMESTAMP, end_reason VARCHAR(20))
                """);
        jdbc.execute("""
                CREATE TABLE recommendation_events (
                  event_id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id VARCHAR(36), user_id BIGINT,
                  group_buy_id BIGINT, event_type VARCHAR(20), source VARCHAR(30), position INT,
                  product_id_snapshot BIGINT, brand_snapshot VARCHAR(100), category_id_snapshot BIGINT,
                  recruitment_rate DOUBLE, discount_rate DOUBLE, deadline_urgency DOUBLE, occurred_at TIMESTAMP)
                """);
    }

    @AfterEach void shutdown() { jdbc.execute("SHUTDOWN"); }

    private void save(long userId) {
        transaction.executeWithoutResult(status -> repository.saveEvents(userId, session, events, snapshots));
    }

    @Test void sameOwnerCanAppendEvents() {
        save(10); save(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_events", Integer.class)).isEqualTo(2);
    }

    @Test void wrongOwnerCannotAppendAndDoesNotReopenClosedSession() {
        save(10);
        jdbc.update("UPDATE recommendation_sessions SET ended_at=TIMESTAMP '2026-09-21 00:00:00', end_reason='PAGEHIDE'");
        assertThatThrownBy(() -> save(20)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT end_reason FROM recommendation_sessions", String.class)).isEqualTo("PAGEHIDE");
    }

    @RepeatedTest(5) void simultaneousFirstRequestsCannotMixOwners() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> raceSave(10, ready, start));
            var second = executor.submit(() -> raceSave(20, ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { start.countDown(); }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SAVED", "DENIED");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recommendation_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM recommendation_events e JOIN recommendation_sessions s
                ON s.session_id=e.session_id WHERE e.user_id<>s.user_id
                """, Integer.class)).isZero();
    }

    private String raceSave(long userId, CountDownLatch ready, CountDownLatch start) {
        try {
            transaction.executeWithoutResult(status -> {
                // 둘 다 서비스의 사전 소유자 조회를 통과한 최악의 순서를 고정한다.
                assertThat(repository.findSessionOwner(session)).isEmpty();
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                repository.saveEvents(userId, session, events, snapshots);
            });
            return "SAVED";
        } catch (BusinessException expected) {
            assertThat(expected.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_ACCESS_DENIED);
            return "DENIED";
        }
    }
}
