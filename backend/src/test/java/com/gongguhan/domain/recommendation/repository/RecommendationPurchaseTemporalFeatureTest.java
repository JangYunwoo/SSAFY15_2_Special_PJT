package com.gongguhan.domain.recommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * §9 검증: 과거 구매(PURCHASE) 학습 이벤트의 모집률이 <b>현재 누적 참여자수</b>가 아니라
 * <b>구매 당시 참여수</b>(joined_at 기준 재집계)로 계산되는지 검증한다.
 *
 * <p>수정 전에는 findTrainingEvents가 gb.current_participants(현재값)를 써서, 이미 모집이
 * 끝난 공구의 모든 구매를 ~100% 참여율로 라벨했다. 수정 후에는 각 구매 시점의 누적 참여수를
 * 쓴다. group_buys.current_participants를 최종 누적값(8)으로 두어 "현재값 오염"과 대비한다.
 *
 * <p>기존 추천 repository 테스트와 같이 경량 raw H2를 쓴다(스냅샷 소스 세션은 비운다).
 */
class RecommendationPurchaseTemporalFeatureTest {
    private JdbcTemplate jdbc;
    private RecommendationRepository repository;
    private final OffsetDateTime base = OffsetDateTime.parse("2026-09-01T00:00:00Z");

    @BeforeEach void database() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + java.util.UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=POSITION", "sa", "");
        ds.setDriverClassName("org.h2.Driver");
        jdbc = new JdbcTemplate(ds);
        repository = new RecommendationRepository(new NamedParameterJdbcTemplate(ds));
        jdbc.execute("CREATE TABLE products (product_id BIGINT PRIMARY KEY, brand VARCHAR(30), category_id BIGINT, price BIGINT)");
        jdbc.execute("""
                CREATE TABLE group_buys (group_buy_id BIGINT PRIMARY KEY, product_id BIGINT,
                product_price BIGINT, target_participants INT, current_participants INT, recruit_end_at TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE participations (participation_id BIGINT PRIMARY KEY, user_id BIGINT,
                group_buy_id BIGINT, status VARCHAR(30), joined_at TIMESTAMP)
                """);
        jdbc.execute("CREATE TABLE recommendation_sessions (session_id VARCHAR(64) PRIMARY KEY, user_id BIGINT, started_at TIMESTAMP, last_event_at TIMESTAMP, ended_at TIMESTAMP, end_reason VARCHAR(30))");
        jdbc.execute("""
                CREATE TABLE recommendation_events (event_id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id VARCHAR(64),
                user_id BIGINT, group_buy_id BIGINT, event_type VARCHAR(20), source VARCHAR(30), position INT,
                product_id_snapshot BIGINT, brand_snapshot VARCHAR(100), category_id_snapshot BIGINT,
                recruitment_rate DOUBLE, discount_rate DOUBLE, deadline_urgency DOUBLE, occurred_at TIMESTAMP)
                """);
        // target=10, current_participants=8(최종 누적) - 수정 전이면 모든 구매가 8/10=0.8로 라벨됨.
        jdbc.update("INSERT INTO products VALUES (1,'브랜드',1,10000)");
        jdbc.update("INSERT INTO group_buys VALUES (1,1,9000,10,8,?)", ts(base.plusDays(5)));
    }

    @AfterEach void close() { jdbc.execute("SHUTDOWN"); }

    private Timestamp ts(OffsetDateTime value) {
        return Timestamp.valueOf(value.toLocalDateTime());
    }

    void participate(long userId, int hours) {
        jdbc.update("INSERT INTO participations VALUES (?,?,1,'PAID',?)", userId, userId, ts(base.plusHours(hours)));
    }

    Map<Long, RecommendationTrainingEvent> purchaseEventsByUser() {
        return repository.findTrainingEvents(200).stream()
                .filter(e -> "PURCHASE".equals(e.eventType()))
                .collect(Collectors.toMap(RecommendationTrainingEvent::userId, Function.identity()));
    }

    @Test void purchaseRecruitmentRateReflectsParticipantsAtJoinTimeNotCurrentTotal() {
        participate(100, 1);   // 당시 누적 1
        participate(101, 2);   // 2
        participate(102, 3);   // 3
        participate(103, 4);   // 4
        participate(104, 5);   // 5

        Map<Long, RecommendationTrainingEvent> byUser = purchaseEventsByUser();
        System.out.printf("AUDIT temporal target=10 currentTotal=8 firstBuyer=%.3f lastBuyer=%.3f%n",
                byUser.get(100L).recruitmentRate(), byUser.get(104L).recruitmentRate());

        // 당시 참여율 1/10..5/10. 현재 누적 오염값(0.8)이 아니다.
        assertThat(byUser.get(100L).recruitmentRate()).isEqualTo(0.1);
        assertThat(byUser.get(101L).recruitmentRate()).isEqualTo(0.2);
        assertThat(byUser.get(102L).recruitmentRate()).isEqualTo(0.3);
        assertThat(byUser.get(103L).recruitmentRate()).isEqualTo(0.4);
        assertThat(byUser.get(104L).recruitmentRate()).isEqualTo(0.5);
        assertThat(byUser.values()).noneMatch(e -> e.recruitmentRate() == 0.8);
    }

    @Test void positiveControlSingleBuyerSeesOwnJoinAsFirstParticipant() {
        participate(200, 1);
        assertThat(purchaseEventsByUser().get(200L).recruitmentRate()).isEqualTo(0.1);
    }
}
