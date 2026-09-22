package com.gongguhan.domain.recommendation.repository;

import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.recommendation.dto.RecommendationEventItem;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RecommendationRepository {

    private static final String THUMBNAIL_SUBQUERY = """
            (SELECT pi.image_url
               FROM product_images pi
              WHERE pi.product_id = p.product_id
              ORDER BY pi.is_thumbnail DESC, pi.sort_order ASC, pi.image_id ASC
              LIMIT 1)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RecommendationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RecommendationCandidate> findCandidates(int limit) {
        return findCandidates(limit, OffsetDateTime.now(ZoneOffset.UTC));
    }

    List<RecommendationCandidate> findCandidates(int limit, OffsetDateTime now) {
        String sql = """
                SELECT gb.group_buy_id AS id,
                       p.product_id,
                       p.name AS product_name,
                       %s AS thumbnail_url,
                       sp.business_name AS seller_name,
                       p.brand,
                       c.category_id,
                       c.name AS category_name,
                       gb.status,
                       p.price AS list_price,
                       gb.product_price,
                       gb.unit_share_amount,
                       gb.shipping_fee,
                       gb.unit_share_amount + gb.shipping_fee AS personal_payment_amount,
                       gb.target_participants,
                       gb.current_participants,
                       gb.recruit_end_at,
                       gb.created_at
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                  JOIN seller_profiles sp ON sp.seller_id = gb.seller_id
             LEFT JOIN categories c ON c.category_id = p.category_id
                 WHERE gb.status = 'RECRUITING'
                   AND gb.recruit_start_at <= :now
                   AND gb.recruit_end_at > :now
                   AND p.status = 'ACTIVE'
                   AND sp.status <> 'SUSPENDED'
                 ORDER BY gb.created_at DESC, gb.group_buy_id DESC
                 LIMIT :limit
                """.formatted(THUMBNAIL_SUBQUERY);
        return jdbcTemplate.query(sql, Map.of("limit", limit, "now", timestamp(now)), this::mapCandidate);
    }

    public Map<Long, RecommendationCandidate> findCandidateSnapshots(Set<Long> groupBuyIds) {
        if (groupBuyIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
                SELECT gb.group_buy_id AS id,
                       p.product_id,
                       p.name AS product_name,
                       %s AS thumbnail_url,
                       sp.business_name AS seller_name,
                       p.brand,
                       c.category_id,
                       c.name AS category_name,
                       gb.status,
                       p.price AS list_price,
                       gb.product_price,
                       gb.unit_share_amount,
                       gb.shipping_fee,
                       gb.unit_share_amount + gb.shipping_fee AS personal_payment_amount,
                       gb.target_participants,
                       gb.current_participants,
                       gb.recruit_end_at,
                       gb.created_at
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                  JOIN seller_profiles sp ON sp.seller_id = gb.seller_id
             LEFT JOIN categories c ON c.category_id = p.category_id
                 WHERE gb.group_buy_id IN (:ids)
                """.formatted(THUMBNAIL_SUBQUERY);
        return jdbcTemplate.query(sql, Map.of("ids", groupBuyIds), this::mapCandidate).stream()
                .collect(Collectors.toMap(RecommendationCandidate::groupBuyId, Function.identity()));
    }

    public Optional<Long> findSessionOwner(String sessionId) {
        return jdbcTemplate.queryForList(
                        "SELECT user_id FROM recommendation_sessions WHERE session_id = :sessionId",
                        Map.of("sessionId", sessionId),
                        Long.class)
                .stream()
                .findFirst();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveEvents(
            long userId,
            String sessionId,
            List<RecommendationEventItem> events,
            Map<Long, RecommendationCandidate> snapshots) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO recommendation_sessions
                       (session_id, user_id, started_at, last_event_at)
                VALUES (:sessionId, :userId, :now, :now)
                ON DUPLICATE KEY UPDATE
                       last_event_at = VALUES(last_event_at),
                       ended_at = NULL,
                       end_reason = NULL
                """, Map.of("sessionId", sessionId, "userId", userId, "now", timestamp(now)));

        // 사전 SELECT만으로는 두 사용자의 동시 최초 생성이 모두 통과할 수 있다.
        // upsert가 확보한 행을 현재 읽기로 재확인한다. 불일치는 upsert까지 함께 롤백한다.
        Long ownerId = jdbcTemplate.queryForObject("""
                SELECT user_id FROM recommendation_sessions
                 WHERE session_id = :sessionId FOR UPDATE
                """, Map.of("sessionId", sessionId), Long.class);
        if (ownerId == null || ownerId.longValue() != userId) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }

        String sql = """
                INSERT INTO recommendation_events
                       (session_id, user_id, group_buy_id, event_type, source, position,
                        product_id_snapshot, brand_snapshot, category_id_snapshot, recruitment_rate,
                        discount_rate, deadline_urgency, occurred_at)
                VALUES (:sessionId, :userId, :groupBuyId, :eventType, :source, :position,
                        :productId, :brand, :categoryId, :recruitmentRate,
                        :discountRate, :deadlineUrgency, :occurredAt)
                """;
        MapSqlParameterSource[] batch = events.stream().map(event -> {
            RecommendationCandidate snapshot = snapshots.get(event.groupBuyId());
            return new MapSqlParameterSource()
                    .addValue("sessionId", sessionId)
                    .addValue("userId", userId)
                    .addValue("groupBuyId", event.groupBuyId())
                    .addValue("eventType", event.eventType())
                    .addValue("source", event.source() == null ? "UNKNOWN" : event.source())
                    .addValue("position", event.position())
                    .addValue("productId", snapshot.productId())
                    .addValue("brand", snapshot.brand())
                    .addValue("categoryId", snapshot.categoryId())
                    .addValue("recruitmentRate", snapshot.recruitmentRate())
                    .addValue("discountRate", snapshot.discountRate())
                    .addValue("deadlineUrgency", snapshot.deadlineUrgency())
                    .addValue("occurredAt", timestamp(now));
        }).toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
    }

    public void endSession(long userId, String sessionId) {
        jdbcTemplate.update("""
                UPDATE recommendation_sessions
                   SET ended_at = :endedAt,
                       end_reason = 'PAGEHIDE'
                 WHERE session_id = :sessionId
                   AND user_id = :userId
                """, Map.of(
                        "endedAt", timestamp(OffsetDateTime.now(ZoneOffset.UTC)),
                        "sessionId", sessionId,
                        "userId", userId));
    }

    public List<RecommendationTrainingEvent> findTrainingEvents(int limit) {
        List<RecommendationTrainingEvent> result = new ArrayList<>();
        // 진행 중 세션이 즉시 학습에 섞이지 않도록 1초 유예 컷오프를 둔다. DB 종속 함수
        // (MySQL DATE_SUB/UTC_TIMESTAMP) 대신 UTC 기준값을 계산해 바인딩한다(동작 동일, 이식성 확보).
        Timestamp cutoff = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        result.addAll(jdbcTemplate.query("""
                SELECT e.user_id,
                       e.group_buy_id,
                       e.event_type,
                       e.occurred_at,
                       e.product_id_snapshot AS product_id,
                       e.brand_snapshot AS brand,
                       e.category_id_snapshot AS category_id,
                       e.recruitment_rate,
                       e.discount_rate,
                       e.deadline_urgency
                  FROM recommendation_events e
                  JOIN recommendation_sessions s ON s.session_id = e.session_id
                 WHERE s.ended_at IS NOT NULL
                    OR s.last_event_at <= :cutoff
                 ORDER BY e.occurred_at DESC
                 LIMIT :limit
                """, Map.of("limit", limit, "cutoff", cutoff), this::mapStoredEvent));

        result.addAll(jdbcTemplate.query("""
                SELECT part.user_id,
                       part.group_buy_id,
                       part.joined_at AS occurred_at,
                       p.product_id,
                       p.brand,
                       p.category_id,
                       p.price AS list_price,
                       gb.product_price,
                       gb.target_participants,
                       (SELECT COUNT(*) FROM participations pp
                         WHERE pp.group_buy_id = part.group_buy_id
                           AND pp.joined_at <= part.joined_at
                           AND pp.status IN ('PAID', 'FULFILLED', 'SHIPPING', 'DELIVERED', 'CONFIRMED', 'SETTLED')
                       ) AS participants_at_join,
                       gb.recruit_end_at
                  FROM participations part
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                  JOIN products p ON p.product_id = gb.product_id
                 WHERE part.status IN ('PAID', 'FULFILLED', 'SHIPPING', 'DELIVERED', 'CONFIRMED', 'SETTLED')
                 ORDER BY part.joined_at DESC
                 LIMIT :limit
                """, Map.of("limit", limit), this::mapPurchaseEvent));
        return result.stream()
                .sorted(Comparator.comparing(RecommendationTrainingEvent::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private RecommendationCandidate mapCandidate(ResultSet resultSet, int rowNum) throws SQLException {
        int targetCount = resultSet.getInt("target_participants");
        int paidCount = resultSet.getInt("current_participants");
        long listPrice = resultSet.getLong("list_price");
        long productPrice = resultSet.getLong("product_price");
        OffsetDateTime recruitEndAt = utc(resultSet, "recruit_end_at");
        double recruitmentRate = ratio(paidCount, targetCount);
        double discountRate = listPrice <= 0 ? 0 : clamp((listPrice - productPrice) * 1.0 / listPrice);
        double deadlineUrgency = deadlineUrgency(OffsetDateTime.now(ZoneOffset.UTC), recruitEndAt);
        Long categoryId = nullableLong(resultSet, "category_id");
        GroupBuySummaryResponse summary = new GroupBuySummaryResponse(
                String.valueOf(resultSet.getLong("id")),
                String.valueOf(resultSet.getLong("product_id")),
                resultSet.getString("product_name"),
                resultSet.getString("thumbnail_url"),
                resultSet.getString("seller_name"),
                categoryId == null ? null : String.valueOf(categoryId),
                resultSet.getString("category_name"),
                resultSet.getString("status"),
                listPrice,
                productPrice,
                resultSet.getLong("unit_share_amount"),
                resultSet.getLong("shipping_fee"),
                resultSet.getLong("personal_payment_amount"),
                targetCount,
                paidCount,
                recruitmentRate,
                recruitEndAt,
                utc(resultSet, "created_at"));
        return new RecommendationCandidate(
                resultSet.getLong("id"), resultSet.getLong("product_id"),
                resultSet.getString("brand"), categoryId,
                recruitmentRate, discountRate, deadlineUrgency, summary);
    }

    private RecommendationTrainingEvent mapStoredEvent(ResultSet resultSet, int rowNum) throws SQLException {
        return new RecommendationTrainingEvent(
                resultSet.getLong("user_id"),
                resultSet.getLong("group_buy_id"),
                resultSet.getLong("product_id"),
                resultSet.getString("event_type"),
                utc(resultSet, "occurred_at"),
                resultSet.getString("brand"),
                nullableLong(resultSet, "category_id"),
                resultSet.getDouble("recruitment_rate"),
                resultSet.getDouble("discount_rate"),
                resultSet.getDouble("deadline_urgency"));
    }

    /**
     * 과거 구매(PURCHASE) 이벤트의 특징은 <b>구매 당시</b> 값이어야 학습 라벨이 왜곡되지 않는다.
     *
     * <p>모집률은 현재 누적 참여자수({@code gb.current_participants})가 아니라 {@code joined_at}
     * 기준으로 재집계한 <b>당시 참여수</b>({@code participants_at_join})를 쓴다. 이미 모집이 끝난
     * 공구의 모든 구매를 ~100% 참여율로 라벨하던 시점 불일치를 없앤다.
     *
     * <p>마감임박도는 이미 과거 {@code occurredAt}(=joined_at)과 마감시각으로 계산하므로 시점
     * 정합적이라 그대로 둔다. 할인율은 당시 정가({@code products.price})가 스냅샷으로 저장돼
     * 있지 않아(판매자가 정가를 바꾼 경우에만 왜곡되는 별개 엣지) 여기서 재구성하지 않는다 -
     * 저장되지 않은 당시값을 현재값으로 지어내지 않는다.
     */
    private RecommendationTrainingEvent mapPurchaseEvent(ResultSet resultSet, int rowNum) throws SQLException {
        long listPrice = resultSet.getLong("list_price");
        long productPrice = resultSet.getLong("product_price");
        OffsetDateTime occurredAt = utc(resultSet, "occurred_at");
        return new RecommendationTrainingEvent(
                resultSet.getLong("user_id"),
                resultSet.getLong("group_buy_id"),
                resultSet.getLong("product_id"),
                "PURCHASE",
                occurredAt,
                resultSet.getString("brand"),
                nullableLong(resultSet, "category_id"),
                ratio(resultSet.getInt("participants_at_join"), resultSet.getInt("target_participants")),
                listPrice <= 0 ? 0 : clamp((listPrice - productPrice) * 1.0 / listPrice),
                deadlineUrgency(occurredAt, utc(resultSet, "recruit_end_at")));
    }

    private OffsetDateTime utc(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.valueOf(value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0 : clamp(numerator * 1.0 / denominator);
    }

    private double deadlineUrgency(OffsetDateTime from, OffsetDateTime deadline) {
        if (deadline == null) {
            return 0;
        }
        double remainingHours = Duration.between(from, deadline).toMinutes() / 60.0;
        return clamp(1 - Math.max(0, Math.min(168, remainingHours)) / 168.0);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
