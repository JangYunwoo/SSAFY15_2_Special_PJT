package com.gongguhan.domain.returnrequest.repository;

import com.gongguhan.domain.returnrequest.dto.ReturnDetailResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnHistoryResponse;
import com.gongguhan.domain.returnrequest.dto.ReturnSummaryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReturnRequestRepository {

    private static final String SUMMARY_SELECT = """
            SELECT rr.return_id AS id, rr.participation_id, part.group_buy_id,
                   p.name AS product_name, u.nickname AS buyer_name,
                   rr.reason_type, rr.reason_detail, rr.status, rr.refund_amount,
                   rr.return_shipping_fee, rr.fee_bearer, rr.requested_at,
                   rr.auto_approve_at, rr.post_confirm, rr.admin_review_required,
                   rr.pickup_zipcode, rr.pickup_address1, rr.pickup_address2,
                   rr.reject_reason
              FROM return_requests rr
              JOIN participations part ON part.participation_id = rr.participation_id
              JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
              JOIN products p ON p.product_id = gb.product_id
              JOIN users u ON u.user_id = part.user_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReturnRequestRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ReturnContext> findContext(Long participationId, boolean forUpdate) {
        String sql = """
                SELECT part.participation_id, part.group_buy_id, part.user_id,
                       part.share_amount, part.shipping_fee, part.total_amount,
                       part.status AS participation_status, part.withdrawal_period_end_at,
                       part.settlement_eligible_at, part.confirm_hold,
                       gb.seller_id, gb.shipping_fee * 2 AS return_shipping_fee,
                       p.name AS product_name,
                       CASE WHEN p.withdrawal_restricted = TRUE
                                  AND p.restriction_review_status = 'APPROVED'
                            THEN TRUE ELSE FALSE END AS withdrawal_restricted
                  FROM participations part
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                  JOIN products p ON p.product_id = gb.product_id
                 WHERE part.participation_id = :participationId
                """ + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, Map.of("participationId", participationId),
                (rs, rowNum) -> mapContext(rs)).stream().findFirst();
    }

    public boolean existsOpenReturn(Long participationId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM return_requests
                 WHERE participation_id = :participationId
                   AND status NOT IN ('REFUNDED', 'REJECTED')
                """, Map.of("participationId", participationId), Long.class);
        return count != null && count > 0;
    }

    public Long create(CreateCommand command) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("participationId", command.participationId())
                .addValue("reasonType", command.reasonType())
                .addValue("reasonDetail", command.reasonDetail())
                .addValue("returnShippingFee", command.returnShippingFee())
                .addValue("feeBearer", command.feeBearer())
                .addValue("refundAmount", command.refundAmount())
                .addValue("postConfirm", command.postConfirm())
                .addValue("adminReviewRequired", command.adminReviewRequired())
                .addValue("requestedAt", command.requestedAt())
                .addValue("autoApproveAt", command.autoApproveAt())
                .addValue("pickupZipcode", command.pickupZipcode())
                .addValue("pickupAddress1", command.pickupAddress1())
                .addValue("pickupAddress2", command.pickupAddress2());
        jdbcTemplate.update("""
                INSERT INTO return_requests (
                    participation_id, reason_type, reason_detail, return_shipping_fee,
                    fee_bearer, refund_amount, post_confirm, admin_review_required,
                    status, requested_at, auto_approve_at, pickup_zipcode,
                    pickup_address1, pickup_address2)
                VALUES (:participationId, :reasonType, :reasonDetail, :returnShippingFee,
                    :feeBearer, :refundAmount, :postConfirm, :adminReviewRequired,
                    'REQUESTED', :requestedAt, :autoApproveAt, :pickupZipcode,
                    :pickupAddress1, :pickupAddress2)
                """, params, keys, new String[]{"return_id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("반품 신청 식별자를 생성하지 못했습니다.");
        }
        return key.longValue();
    }

    public void holdConfirmation(Long participationId) {
        jdbcTemplate.update("""
                UPDATE participations SET confirm_hold = TRUE
                 WHERE participation_id = :participationId
                """, Map.of("participationId", participationId));
    }

    public void addHistory(Long returnId, String status, String note, LocalDateTime occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO return_request_histories (return_id, status, note, occurred_at)
                VALUES (:returnId, :status, :note, :occurredAt)
                """, new MapSqlParameterSource()
                .addValue("returnId", returnId).addValue("status", status)
                .addValue("note", note).addValue("occurredAt", occurredAt));
    }

    public Optional<ReturnDetailResponse> findBuyerDetail(Long userId, Long returnId) {
        return findDetail("rr.return_id = :returnId AND part.user_id = :ownerId", userId, returnId);
    }

    public Optional<ReturnDetailResponse> findSellerDetail(Long sellerId, Long returnId) {
        return findDetail("rr.return_id = :returnId AND gb.seller_id = :ownerId", sellerId, returnId);
    }

    public Page<ReturnSummaryResponse> findBuyerList(Long userId, String status,
                                                     Long groupBuyId, Pageable pageable) {
        return findList("part.user_id = :ownerId", userId, status, groupBuyId, pageable);
    }

    public Page<ReturnSummaryResponse> findSellerList(Long sellerId, String status,
                                                      Long groupBuyId, Pageable pageable) {
        return findList("gb.seller_id = :ownerId", sellerId, status, groupBuyId, pageable);
    }

    public Optional<ReturnLock> findSellerLock(Long sellerId, Long returnId) {
        return jdbcTemplate.query("""
                SELECT rr.return_id, rr.participation_id, rr.status,
                       rr.admin_review_required
                  FROM return_requests rr
                  JOIN participations part ON part.participation_id = rr.participation_id
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                 WHERE rr.return_id = :returnId AND gb.seller_id = :sellerId
                 FOR UPDATE
                """, Map.of("returnId", returnId, "sellerId", sellerId),
                (rs, rowNum) -> new ReturnLock(rs.getLong("return_id"),
                        rs.getLong("participation_id"), rs.getString("status"),
                        rs.getBoolean("admin_review_required"))).stream().findFirst();
    }

    public Optional<ReturnLock> findBuyerLock(Long userId, Long returnId) {
        return jdbcTemplate.query("""
                SELECT rr.return_id, rr.participation_id, rr.status,
                       rr.admin_review_required
                  FROM return_requests rr
                  JOIN participations part ON part.participation_id = rr.participation_id
                 WHERE rr.return_id = :returnId AND part.user_id = :userId
                 FOR UPDATE
                """, Map.of("returnId", returnId, "userId", userId),
                (rs, rowNum) -> new ReturnLock(rs.getLong("return_id"),
                        rs.getLong("participation_id"), rs.getString("status"),
                        rs.getBoolean("admin_review_required"))).stream().findFirst();
    }

    public void approve(Long returnId, String approveType, LocalDateTime approvedAt) {
        jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'APPROVED', approved_at = :approvedAt,
                       approve_type = :approveType, auto_refund_due_at = :autoRefundDueAt
                 WHERE return_id = :returnId AND status = 'REQUESTED'
                """, new MapSqlParameterSource("returnId", returnId)
                .addValue("approvedAt", approvedAt).addValue("approveType", approveType)
                .addValue("autoRefundDueAt", approvedAt.plusDays(7)));
        jdbcTemplate.update("""
                UPDATE participations SET status = 'RETURNING'
                 WHERE participation_id = (
                    SELECT participation_id FROM return_requests WHERE return_id = :returnId)
                """, Map.of("returnId", returnId));
    }

    public void reject(Long returnId, String reason) {
        jdbcTemplate.update("""
                UPDATE return_requests SET status = 'REJECTED', reject_reason = :reason
                 WHERE return_id = :returnId AND status = 'REQUESTED'
                """, Map.of("returnId", returnId, "reason", reason));
        releaseConfirmationHold(returnId);
    }

    public void dispute(Long returnId) {
        jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'DISPUTED', admin_review_required = TRUE
                 WHERE return_id = :returnId AND status = 'REJECTED'
                """, Map.of("returnId", returnId));
    }

    public List<Long> findAutoApprovalCandidates(LocalDateTime now, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT return_id FROM return_requests
                 WHERE status = 'REQUESTED'
                   AND admin_review_required = FALSE
                   AND auto_approve_at <= :now
                 ORDER BY auto_approve_at, return_id
                 LIMIT :limit
                """, new MapSqlParameterSource("now", now).addValue("limit", limit), Long.class);
    }

    public boolean approveAutomatically(Long returnId, LocalDateTime approvedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'APPROVED', approved_at = :approvedAt,
                       approve_type = 'AUTO', auto_refund_due_at = :autoRefundDueAt
                 WHERE return_id = :returnId AND status = 'REQUESTED'
                   AND admin_review_required = FALSE AND auto_approve_at <= :approvedAt
                """, new MapSqlParameterSource("returnId", returnId)
                .addValue("approvedAt", approvedAt)
                .addValue("autoRefundDueAt", approvedAt.plusDays(7)));
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE participations SET status = 'RETURNING'
                     WHERE participation_id = (
                        SELECT participation_id FROM return_requests WHERE return_id = :returnId)
                    """, Map.of("returnId", returnId));
        }
        return updated == 1;
    }

    public Optional<RefundContext> findSellerRefundContext(Long sellerId, Long returnId) {
        return findRefundContext("rr.return_id = :returnId AND gb.seller_id = :ownerId",
                sellerId, returnId, true);
    }

    public Optional<RefundContext> findAutoRefundContext(Long returnId) {
        return findRefundContext("rr.return_id = :returnId", null, returnId, true);
    }

    public List<Long> findAutoRefundCandidates(LocalDateTime now, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT return_id FROM return_requests
                 WHERE status = 'APPROVED' AND auto_refund_due_at <= :now
                 ORDER BY auto_refund_due_at, return_id
                 LIMIT :limit
                """, new MapSqlParameterSource("now", now).addValue("limit", limit), Long.class);
    }

    public boolean markRefundRequested(Long returnId, boolean automatic,
                                       LocalDateTime requestedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'REFUND_REQUESTED',
                       picked_up_at = CASE WHEN :automatic = TRUE THEN picked_up_at ELSE :requestedAt END,
                       auto_refunded = :automatic
                 WHERE return_id = :returnId AND status = 'APPROVED'
                """, new MapSqlParameterSource("returnId", returnId)
                .addValue("automatic", automatic).addValue("requestedAt", requestedAt));
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE participations SET status = 'REFUNDING'
                     WHERE participation_id = (
                        SELECT participation_id FROM return_requests WHERE return_id = :returnId)
                    """, Map.of("returnId", returnId));
        }
        return updated == 1;
    }

    public boolean markRefunded(Long returnId, LocalDateTime completedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE return_requests
                   SET status = 'REFUNDED', completed_at = :completedAt
                 WHERE return_id = :returnId AND status = 'REFUND_REQUESTED'
                """, Map.of("returnId", returnId, "completedAt", completedAt));
        if (updated == 1) {
            jdbcTemplate.update("""
                    UPDATE participations SET status = 'REFUNDED', confirm_hold = FALSE
                     WHERE participation_id = (
                        SELECT participation_id FROM return_requests WHERE return_id = :returnId)
                    """, Map.of("returnId", returnId));
        }
        return updated == 1;
    }

    public boolean markRefundFailed(Long returnId) {
        return jdbcTemplate.update("""
                UPDATE return_requests SET status = 'REFUND_FAILED'
                 WHERE return_id = :returnId AND status = 'REFUND_REQUESTED'
                """, Map.of("returnId", returnId)) == 1;
    }

    private Optional<RefundContext> findRefundContext(String predicate, Long ownerId,
                                                      Long returnId, boolean forUpdate) {
        MapSqlParameterSource params = new MapSqlParameterSource("returnId", returnId);
        if (ownerId != null) {
            params.addValue("ownerId", ownerId);
        }
        String sql = """
                SELECT rr.return_id, rr.participation_id, rr.refund_amount, rr.status,
                       rr.auto_refund_due_at, gb.seller_id, pay.payment_id,
                       pay.method AS payment_method
                  FROM return_requests rr
                  JOIN participations part ON part.participation_id = rr.participation_id
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                  JOIN payments pay ON pay.participation_id = part.participation_id
                 WHERE %s
                """.formatted(predicate) + (forUpdate ? " FOR UPDATE" : "");
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new RefundContext(
                rs.getLong("return_id"), rs.getLong("participation_id"),
                rs.getLong("seller_id"), rs.getLong("payment_id"),
                rs.getString("payment_method"), rs.getLong("refund_amount"),
                rs.getString("status"), local(rs, "auto_refund_due_at")))
                .stream().findFirst();
    }

    private void releaseConfirmationHold(Long returnId) {
        jdbcTemplate.update("""
                UPDATE participations SET confirm_hold = FALSE
                 WHERE participation_id = (
                    SELECT participation_id FROM return_requests WHERE return_id = :returnId)
                """, Map.of("returnId", returnId));
    }

    private Optional<ReturnDetailResponse> findDetail(String predicate, Long ownerId, Long returnId) {
        Map<String, ?> params = Map.of("ownerId", ownerId, "returnId", returnId);
        String sql = SUMMARY_SELECT + " WHERE " + predicate;
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            ReturnSummaryResponse summary = mapSummary(rs);
            List<ReturnHistoryResponse> history = findHistory(returnId);
            return ReturnDetailResponse.from(summary, rs.getString("pickup_zipcode"),
                    rs.getString("pickup_address1"), rs.getString("pickup_address2"),
                    rs.getString("reject_reason"), history);
        }).stream().findFirst();
    }

    private List<ReturnHistoryResponse> findHistory(Long returnId) {
        return jdbcTemplate.query("""
                SELECT status, note, occurred_at FROM return_request_histories
                 WHERE return_id = :returnId ORDER BY occurred_at, history_id
                """, Map.of("returnId", returnId), (rs, rowNum) -> new ReturnHistoryResponse(
                rs.getString("status"), utc(rs, "occurred_at"), rs.getString("note")));
    }

    private Page<ReturnSummaryResponse> findList(String ownerPredicate, Long ownerId,
                                                 String status, Long groupBuyId,
                                                 Pageable pageable) {
        StringBuilder where = new StringBuilder(" WHERE ").append(ownerPredicate);
        MapSqlParameterSource params = new MapSqlParameterSource("ownerId", ownerId)
                .addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        if (status != null && !status.isBlank()) {
            where.append(" AND rr.status = :status");
            params.addValue("status", status);
        }
        if (groupBuyId != null) {
            where.append(" AND part.group_buy_id = :groupBuyId");
            params.addValue("groupBuyId", groupBuyId);
        }
        List<ReturnSummaryResponse> content = jdbcTemplate.query(
                SUMMARY_SELECT + where + " ORDER BY rr.requested_at DESC, rr.return_id DESC LIMIT :limit OFFSET :offset",
                params, (rs, rowNum) -> mapSummary(rs));
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM return_requests rr
                  JOIN participations part ON part.participation_id = rr.participation_id
                  JOIN group_buys gb ON gb.group_buy_id = part.group_buy_id
                """ + where, params, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private ReturnContext mapContext(ResultSet rs) throws SQLException {
        return new ReturnContext(rs.getLong("participation_id"), rs.getLong("group_buy_id"),
                rs.getLong("user_id"), rs.getLong("seller_id"), rs.getString("product_name"),
                rs.getLong("share_amount"), rs.getLong("shipping_fee"),
                rs.getLong("total_amount"), rs.getLong("return_shipping_fee"),
                rs.getString("participation_status"), local(rs, "withdrawal_period_end_at"),
                local(rs, "settlement_eligible_at"), rs.getBoolean("confirm_hold"),
                rs.getBoolean("withdrawal_restricted"));
    }

    private ReturnSummaryResponse mapSummary(ResultSet rs) throws SQLException {
        return new ReturnSummaryResponse(rs.getLong("id"), rs.getLong("participation_id"),
                rs.getLong("group_buy_id"), rs.getString("product_name"),
                rs.getString("buyer_name"), rs.getString("reason_type"),
                rs.getString("reason_detail"), rs.getString("status"),
                rs.getLong("refund_amount"), rs.getLong("return_shipping_fee"),
                rs.getString("fee_bearer"), utc(rs, "requested_at"),
                utc(rs, "auto_approve_at"), rs.getBoolean("post_confirm"),
                rs.getBoolean("admin_review_required"));
    }

    private LocalDateTime local(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private OffsetDateTime utc(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = local(rs, column);
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    public record ReturnContext(Long participationId, Long groupBuyId, Long userId,
                                Long sellerId, String productName, long shareAmount,
                                long originalShippingFee, long totalAmount,
                                long returnShippingFee, String participationStatus,
                                LocalDateTime withdrawalDeadline,
                                LocalDateTime settlementEligibleAt,
                                boolean confirmationHeld, boolean withdrawalRestricted) {
    }

    public record CreateCommand(Long participationId, String reasonType, String reasonDetail,
                                long returnShippingFee, String feeBearer, long refundAmount,
                                boolean postConfirm, boolean adminReviewRequired,
                                LocalDateTime requestedAt, LocalDateTime autoApproveAt,
                                String pickupZipcode, String pickupAddress1,
                                String pickupAddress2) {
    }

    public record ReturnLock(Long returnId, Long participationId, String status,
                             boolean adminReviewRequired) {
    }

    public record RefundContext(Long returnId, Long participationId, Long sellerId,
                                Long paymentId, String paymentMethod, long refundAmount,
                                String status, LocalDateTime autoRefundDueAt) {
    }
}
