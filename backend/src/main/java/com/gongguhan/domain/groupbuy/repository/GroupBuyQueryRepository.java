package com.gongguhan.domain.groupbuy.repository;

import com.gongguhan.domain.groupbuy.dto.GroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuySearchCondition;
import com.gongguhan.domain.groupbuy.dto.GroupBuySort;
import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuySummaryResponse;
import com.gongguhan.domain.groupbuy.dto.SellerGroupBuyDetailResponse;
import com.gongguhan.domain.groupbuy.dto.SellerParticipantResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GroupBuyQueryRepository {

    private static final String THUMBNAIL_SUBQUERY = """
            (SELECT pi.image_url
               FROM product_images pi
              WHERE pi.product_id = p.product_id
              ORDER BY pi.is_thumbnail DESC, pi.sort_order ASC, pi.image_id ASC
              LIMIT 1)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final boolean mysql;

    public GroupBuyQueryRepository(NamedParameterJdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.mysql = isMySql(dataSource);
    }

    public Page<GroupBuySummaryResponse> findPublicGroupBuys(
            GroupBuySearchCondition condition, Pageable pageable) {
        MapSqlParameterSource parameters = publicParameters(condition, pageable);
        String where = publicWhere(condition);
        String select = """
                SELECT gb.group_buy_id AS id,
                       p.product_id,
                       p.name AS product_name,
                       %s AS thumbnail_url,
                       sp.business_name AS seller_name,
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
                """.formatted(THUMBNAIL_SUBQUERY);
        String dataSql = select + where + orderBy(condition.sort()) + " LIMIT :limit OFFSET :offset";
        String countSql = """
                SELECT COUNT(*)
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                  JOIN seller_profiles sp ON sp.seller_id = gb.seller_id
                """ + where;

        List<GroupBuySummaryResponse> content = jdbcTemplate.query(dataSql, parameters, this::mapSummary);
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public Optional<GroupBuyDetailResponse> findPublicDetail(Long groupBuyId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("groupBuyId", groupBuyId);
        String sql = """
                SELECT gb.group_buy_id AS id,
                       p.product_id,
                       p.name AS product_name,
                       %s AS thumbnail_url,
                       sp.business_name AS seller_name,
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
                       gb.created_at,
                       p.description,
                       p.brand,
                       p.model_name,
                       p.color,
                       gb.rounding_diff,
                       gb.recruit_start_at,
                       p.withdrawal_restricted,
                       CASE WHEN p.withdrawal_restricted = TRUE
                                  AND p.restriction_review_status = 'APPROVED'
                            THEN wrr.name ELSE NULL END AS withdrawal_restriction_reason,
                       CASE WHEN sp.status = 'SUSPENDED' THEN TRUE ELSE FALSE END AS seller_suspended
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                  JOIN seller_profiles sp ON sp.seller_id = gb.seller_id
             LEFT JOIN categories c ON c.category_id = p.category_id
             LEFT JOIN withdrawal_restriction_reasons wrr ON wrr.reason_id = p.restriction_reason_id
                 WHERE gb.group_buy_id = :groupBuyId
                   AND p.status = 'ACTIVE'
                """.formatted(THUMBNAIL_SUBQUERY);

        Optional<GroupBuyDetailResponse> detail =
                jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> mapDetail(resultSet, List.of()))
                        .stream()
                        .findFirst();
        if (detail.isEmpty()) {
            return Optional.empty();
        }

        List<String> images = jdbcTemplate.queryForList("""
                SELECT image_url
                  FROM product_images
                 WHERE product_id = :productId
                 ORDER BY sort_order ASC, image_id ASC
                """, Map.of("productId", detail.get().productId()), String.class);
        return Optional.of(detail.get().withImages(images));
    }

    public Page<SellerGroupBuySummaryResponse> findSellerGroupBuys(Long sellerId, Pageable pageable) {
        MapSqlParameterSource parameters = pageParameters(pageable).addValue("sellerId", sellerId);
        String fromWhere = """
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                 WHERE gb.seller_id = :sellerId
                """;
        String sql = """
                SELECT gb.group_buy_id AS id,
                       p.product_id,
                       p.name AS product_name,
                       gb.status,
                       gb.target_participants,
                       gb.current_participants,
                       (SELECT COUNT(*)
                          FROM participations part
                         WHERE part.group_buy_id = gb.group_buy_id
                           AND part.status IN ('CONFIRMED', 'SETTLED')) AS confirmed_count,
                       gb.product_price,
                       gb.unit_share_amount,
                       gb.shipping_fee,
                       gb.recruit_end_at
                """ + fromWhere
                + " ORDER BY gb.created_at DESC, gb.group_buy_id DESC LIMIT :limit OFFSET :offset";

        List<SellerGroupBuySummaryResponse> content =
                jdbcTemplate.query(sql, parameters, this::mapSellerGroupBuy);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromWhere, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * 판매자 운영 상세는 목록·에스크로·배송·반품 저장소를 순서대로 호출하지 않고
     * 한 번의 읽기 모델 쿼리로 반환한다. 집계 서브쿼리는 모두 FK 인덱스를 사용한다.
     */
    public Optional<SellerGroupBuyDetailResponse> findSellerDetail(Long sellerId, Long groupBuyId) {
        String sql = """
                SELECT gb.group_buy_id AS id,
                       gb.product_id,
                       p.name AS product_name,
                       gb.status,
                       gb.target_participants,
                       gb.current_participants,
                       (SELECT COUNT(*)
                          FROM participations part
                         WHERE part.group_buy_id = gb.group_buy_id
                           AND part.status IN ('CONFIRMED', 'SETTLED')) AS confirmed_count,
                       gb.product_price,
                       gb.unit_share_amount,
                       gb.shipping_fee,
                       gb.recruit_end_at,
                       (SELECT CASE WHEN COUNT(*) = 0 THEN NULL
                                    ELSE SUM(CASE WHEN d.tracking_number IS NOT NULL THEN 1 ELSE 0 END) * 1.0
                                         / COUNT(*) END
                          FROM deliveries d
                          JOIN participations dp ON dp.participation_id = d.participation_id
                         WHERE dp.group_buy_id = gb.group_buy_id) AS invoice_registered_rate,
                       gb.recruit_start_at,
                       gb.rounding_diff,
                       gb.platform_fee_rate,
                       (SELECT COALESCE(SUM(e.amount), 0)
                          FROM escrows e
                         WHERE e.group_buy_id = gb.group_buy_id
                           AND e.status = 'HELD') AS escrow_held_amount,
                       gb.expected_settlement_amount,
                       (SELECT COUNT(*)
                          FROM return_requests rr
                          JOIN participations rp ON rp.participation_id = rr.participation_id
                         WHERE rp.group_buy_id = gb.group_buy_id) AS return_count,
                       CASE WHEN gb.status = 'RECRUITING'
                                  AND gb.current_participants = 0
                                  AND gb.reserved_slots = 0
                            THEN TRUE ELSE FALSE END AS can_edit,
                       CASE WHEN gb.status = 'RECRUITING'
                                  AND gb.current_participants = 0
                                  AND gb.reserved_slots = 0
                            THEN TRUE ELSE FALSE END AS can_cancel
                  FROM group_buys gb
                  JOIN products p ON p.product_id = gb.product_id
                 WHERE gb.group_buy_id = :groupBuyId
                   AND gb.seller_id = :sellerId
                """;
        return jdbcTemplate.query(
                        sql,
                        Map.of("sellerId", sellerId, "groupBuyId", groupBuyId),
                        this::mapSellerGroupBuyDetail)
                .stream()
                .findFirst();
    }

    public boolean existsOwnedGroupBuy(Long sellerId, Long groupBuyId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM group_buys
                 WHERE group_buy_id = :groupBuyId
                   AND seller_id = :sellerId
                """, Map.of("groupBuyId", groupBuyId, "sellerId", sellerId), Long.class);
        return count != null && count > 0;
    }

    public Page<SellerParticipantResponse> findSellerParticipants(
            Long sellerId, Long groupBuyId, Pageable pageable) {
        MapSqlParameterSource parameters = pageParameters(pageable)
                .addValue("sellerId", sellerId)
                .addValue("groupBuyId", groupBuyId);
        String fromWhere = """
                  FROM participations part
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                  JOIN users u ON u.user_id = part.user_id
                 WHERE gb.group_buy_id = :groupBuyId
                   AND gb.seller_id = :sellerId
                   AND part.status <> 'CANCELED'
                """;
        String sql = """
                SELECT part.participation_id,
                       u.nickname AS buyer_name,
                       part.recipient_name,
                       part.zipcode,
                       part.address1,
                       part.address2,
                       part.joined_at AS paid_at,
                       part.total_amount AS amount,
                       part.status
                """ + fromWhere
                + " ORDER BY part.joined_at ASC, part.participation_id ASC LIMIT :limit OFFSET :offset";

        List<SellerParticipantResponse> content = jdbcTemplate.query(sql, parameters, this::mapSellerParticipant);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromWhere, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private String publicWhere(GroupBuySearchCondition condition) {
        StringBuilder where = new StringBuilder("""
                 WHERE gb.status = :status
                   AND p.status = 'ACTIVE'
                   AND sp.status <> 'SUSPENDED'
                """);
        if (condition.categoryId() != null) {
            where.append(" AND p.category_id = :categoryId\n");
        }
        if (condition.keyword() != null) {
            if (mysql && condition.keyword().length() >= 2) {
                where.append(" AND MATCH(p.name) AGAINST(:keyword IN NATURAL LANGUAGE MODE)\n");
            } else {
                where.append(" AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))\n");
            }
        }
        return where.toString();
    }

    private MapSqlParameterSource publicParameters(GroupBuySearchCondition condition, Pageable pageable) {
        MapSqlParameterSource parameters = pageParameters(pageable).addValue("status", condition.status());
        if (condition.categoryId() != null) {
            parameters.addValue("categoryId", condition.categoryId());
        }
        if (condition.keyword() != null) {
            parameters.addValue("keyword", condition.keyword());
        }
        return parameters;
    }

    private String orderBy(GroupBuySort sort) {
        return switch (sort) {
            case POPULAR -> " ORDER BY (gb.current_participants * 1.0 / gb.target_participants) DESC, gb.group_buy_id DESC";
            case DEADLINE -> " ORDER BY gb.recruit_end_at ASC, gb.group_buy_id ASC";
            case NEWEST -> " ORDER BY gb.created_at DESC, gb.group_buy_id DESC";
            case OLDEST -> " ORDER BY gb.created_at ASC, gb.group_buy_id ASC";
            case PRICE_ASC -> " ORDER BY gb.unit_share_amount ASC, gb.group_buy_id ASC";
            case PRICE_DESC -> " ORDER BY gb.unit_share_amount DESC, gb.group_buy_id DESC";
        };
    }

    private MapSqlParameterSource pageParameters(Pageable pageable) {
        return new MapSqlParameterSource()
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
    }

    private GroupBuySummaryResponse mapSummary(ResultSet resultSet, int rowNum) throws SQLException {
        int targetCount = resultSet.getInt("target_participants");
        int paidCount = resultSet.getInt("current_participants");
        return new GroupBuySummaryResponse(
                resultSet.getLong("id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getString("thumbnail_url"),
                resultSet.getString("seller_name"),
                nullableLong(resultSet, "category_id"),
                resultSet.getString("category_name"),
                resultSet.getString("status"),
                resultSet.getLong("list_price"),
                resultSet.getLong("product_price"),
                resultSet.getLong("unit_share_amount"),
                resultSet.getLong("shipping_fee"),
                resultSet.getLong("personal_payment_amount"),
                targetCount,
                paidCount,
                participationRate(paidCount, targetCount),
                utc(resultSet, "recruit_end_at"),
                utc(resultSet, "created_at"));
    }

    private GroupBuyDetailResponse mapDetail(ResultSet resultSet, List<String> images) throws SQLException {
        int targetCount = resultSet.getInt("target_participants");
        int paidCount = resultSet.getInt("current_participants");
        return new GroupBuyDetailResponse(
                resultSet.getLong("id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getString("thumbnail_url"),
                resultSet.getString("seller_name"),
                nullableLong(resultSet, "category_id"),
                resultSet.getString("category_name"),
                resultSet.getString("status"),
                resultSet.getLong("list_price"),
                resultSet.getLong("product_price"),
                resultSet.getLong("unit_share_amount"),
                resultSet.getLong("shipping_fee"),
                resultSet.getLong("personal_payment_amount"),
                targetCount,
                paidCount,
                participationRate(paidCount, targetCount),
                utc(resultSet, "recruit_end_at"),
                utc(resultSet, "created_at"),
                resultSet.getString("description"),
                images,
                resultSet.getString("brand"),
                resultSet.getString("model_name"),
                resultSet.getString("color"),
                resultSet.getLong("rounding_diff"),
                utc(resultSet, "recruit_start_at"),
                resultSet.getBoolean("withdrawal_restricted"),
                resultSet.getString("withdrawal_restriction_reason"),
                resultSet.getBoolean("seller_suspended"));
    }

    private SellerGroupBuySummaryResponse mapSellerGroupBuy(ResultSet resultSet, int rowNum) throws SQLException {
        return new SellerGroupBuySummaryResponse(
                resultSet.getLong("id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getString("status"),
                resultSet.getInt("target_participants"),
                resultSet.getInt("current_participants"),
                resultSet.getInt("confirmed_count"),
                resultSet.getLong("product_price"),
                resultSet.getLong("unit_share_amount"),
                resultSet.getLong("shipping_fee"),
                utc(resultSet, "recruit_end_at"),
                null);
    }

    private SellerGroupBuyDetailResponse mapSellerGroupBuyDetail(
            ResultSet resultSet, int rowNum) throws SQLException {
        return new SellerGroupBuyDetailResponse(
                resultSet.getLong("id"),
                resultSet.getLong("product_id"),
                resultSet.getString("product_name"),
                resultSet.getString("status"),
                resultSet.getInt("target_participants"),
                resultSet.getInt("current_participants"),
                resultSet.getInt("confirmed_count"),
                resultSet.getLong("product_price"),
                resultSet.getLong("unit_share_amount"),
                resultSet.getLong("shipping_fee"),
                utc(resultSet, "recruit_end_at"),
                resultSet.getBigDecimal("invoice_registered_rate"),
                utc(resultSet, "recruit_start_at"),
                resultSet.getLong("rounding_diff"),
                resultSet.getBigDecimal("platform_fee_rate"),
                resultSet.getLong("escrow_held_amount"),
                resultSet.getLong("expected_settlement_amount"),
                resultSet.getInt("return_count"),
                resultSet.getBoolean("can_edit"),
                resultSet.getBoolean("can_cancel"));
    }

    private SellerParticipantResponse mapSellerParticipant(ResultSet resultSet, int rowNum) throws SQLException {
        return new SellerParticipantResponse(
                resultSet.getLong("participation_id"),
                resultSet.getString("buyer_name"),
                resultSet.getString("recipient_name"),
                resultSet.getString("zipcode"),
                resultSet.getString("address1"),
                resultSet.getString("address2"),
                utc(resultSet, "paid_at"),
                resultSet.getLong("amount"),
                resultSet.getString("status"));
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private OffsetDateTime utc(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime().atOffset(ZoneOffset.UTC);
    }

    private double participationRate(int paidCount, int targetCount) {
        return targetCount == 0 ? 0 : (double) paidCount / targetCount;
    }

    private boolean isMySql(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (SQLException exception) {
            throw new IllegalStateException("데이터베이스 종류를 확인할 수 없습니다.", exception);
        }
    }
}
