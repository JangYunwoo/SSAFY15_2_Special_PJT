package com.gongguhan.domain.participation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 구매자 참여 내역 읽기 전용 조회 (PAR-01·02).
 * {@link com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository}와 같은
 * NamedParameterJdbcTemplate 패턴 - 참여·공동구매·상품·판매자·에스크로·결제를 한 번에
 * 조인해야 해서 엔티티 그래프보다 SQL이 낫다.
 */
@Repository
public class ParticipationQueryRepository {

    private static final String SELECT_ROW = """
            SELECT pt.participation_id,
                   pt.group_buy_id,
                   pt.user_id,
                   p.name AS product_name,
                   sp.business_name AS seller_name,
                   pt.status,
                   gb.status AS group_buy_status,
                   e.status AS escrow_status,
                   pay.method,
                   pay.payment_id,
                   pt.share_amount,
                   pt.shipping_fee,
                   pt.total_amount,
                   pt.joined_at,
                   gb.target_participants,
                   gb.current_participants,
                   gb.recruit_end_at,
                   gb.fulfilled_at,
                   pt.delivered_at,
                   pt.withdrawal_period_end_at,
                   pt.confirmed_at,
                   pt.confirm_type,
                   pt.confirm_hold,
                   pt.recipient_name,
                   pt.recipient_phone,
                   pt.zipcode,
                   pt.address1,
                   pt.address2,
                   pt.delivery_request,
                   d.shipped_at
              FROM participations pt
              JOIN group_buys gb ON gb.group_buy_id = pt.group_buy_id
              JOIN products p ON p.product_id = gb.product_id
              JOIN seller_profiles sp ON sp.seller_id = gb.seller_id
         LEFT JOIN escrows e ON e.participation_id = pt.participation_id
         LEFT JOIN payments pay ON pay.participation_id = pt.participation_id
         LEFT JOIN deliveries d ON d.participation_id = pt.participation_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ParticipationQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<MyParticipationRow> findMyParticipations(Long userId, String status, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("status", status)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        String where = " WHERE pt.user_id = :userId" + (status == null ? "" : " AND pt.status = :status");
        String dataSql = SELECT_ROW + where
                + " ORDER BY pt.joined_at DESC, pt.participation_id DESC LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) FROM participations pt" + where;

        List<MyParticipationRow> content = jdbcTemplate.query(dataSql, parameters, this::mapRow);
        Long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public Optional<MyParticipationRow> findByParticipationId(Long participationId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("participationId", participationId);
        String sql = SELECT_ROW + " WHERE pt.participation_id = :participationId";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, parameters, this::mapRow));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private MyParticipationRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new MyParticipationRow(
                rs.getLong("participation_id"),
                rs.getLong("group_buy_id"),
                rs.getLong("user_id"),
                rs.getString("product_name"),
                rs.getString("seller_name"),
                rs.getString("status"),
                rs.getString("group_buy_status"),
                rs.getString("escrow_status"),
                rs.getString("method"),
                (Long) rs.getObject("payment_id"),
                rs.getLong("share_amount"),
                rs.getLong("shipping_fee"),
                rs.getLong("total_amount"),
                toLocalDateTime(rs.getTimestamp("joined_at")),
                rs.getInt("target_participants"),
                rs.getInt("current_participants"),
                toLocalDateTime(rs.getTimestamp("recruit_end_at")),
                toLocalDateTime(rs.getTimestamp("fulfilled_at")),
                toLocalDateTime(rs.getTimestamp("delivered_at")),
                toLocalDateTime(rs.getTimestamp("withdrawal_period_end_at")),
                toLocalDateTime(rs.getTimestamp("confirmed_at")),
                rs.getString("confirm_type"),
                rs.getBoolean("confirm_hold"),
                rs.getString("recipient_name"),
                rs.getString("recipient_phone"),
                rs.getString("zipcode"),
                rs.getString("address1"),
                rs.getString("address2"),
                rs.getString("delivery_request"),
                toLocalDateTime(rs.getTimestamp("shipped_at")));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /** 조인 결과 원본 행. 응답 조립(정책 시각 계산·이력 유도·가능 여부 판정)은 서비스 몫이다. */
    public record MyParticipationRow(
            Long participationId,
            Long groupBuyId,
            Long userId,
            String productName,
            String sellerName,
            String status,
            String groupBuyStatus,
            String escrowStatus,
            String method,
            Long paymentId,
            long shareAmount,
            long shippingFee,
            long totalAmount,
            LocalDateTime joinedAt,
            int targetParticipants,
            int currentParticipants,
            LocalDateTime recruitEndAt,
            LocalDateTime fulfilledAt,
            LocalDateTime deliveredAt,
            LocalDateTime withdrawalPeriodEndAt,
            LocalDateTime confirmedAt,
            String confirmType,
            boolean confirmHold,
            String recipientName,
            String recipientPhone,
            String zipcode,
            String address1,
            String address2,
            String deliveryRequest,
            LocalDateTime shippedAt
    ) {
    }
}
