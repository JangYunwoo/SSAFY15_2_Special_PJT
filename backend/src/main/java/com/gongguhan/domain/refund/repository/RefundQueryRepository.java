package com.gongguhan.domain.refund.repository;

import java.math.BigDecimal;
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
 * 환불 내역 읽기 전용 조회 (RFD-01~03).
 *
 * <p>{@link com.gongguhan.domain.participation.repository.ParticipationQueryRepository} 와
 * 같은 {@code NamedParameterJdbcTemplate} 패턴입니다.
 *
 * <p><b>왜 JPA 가 아니라 SQL 인가.</b> {@code refunds} 에는 {@code user_id} 가 없습니다.
 * 환불의 주인을 찾으려면 두 갈래를 모두 따라가야 합니다.
 *
 * <pre>
 *   참여 건 환불 : refunds.participation_id -> participations.user_id
 *   입금 환불    : refunds.deposit_id -> crypto_deposits.payment_id -> payments.user_id
 * </pre>
 *
 * <p>슬롯 만료·미매칭 입금 환불은 참여 건이 만들어지지 않아 첫 갈래로는 주인을 찾을
 * 수 없습니다. 그래서 {@code COALESCE} 로 두 경로를 합칩니다. 팀 규칙상 JPA 연관관계를
 * 쓰지 않으므로 엔티티 그래프로는 이 조인을 표현할 수 없습니다.
 */
@Repository
public class RefundQueryRepository {

    /**
     * 환불 한 건과 그 주인.
     *
     * <p>{@code owner_user_id} 가 소유권 검증의 기준입니다. URL 의 식별자가 아니라 이
     * 값과 인증 주체를 대조합니다(API 명세서 1.1).
     */
    private static final String SELECT_ROW = """
            SELECT r.refund_id,
                   r.participation_id,
                   r.return_id,
                   r.deposit_id,
                   r.amount,
                   r.reason_type,
                   r.refund_method,
                   r.institution_tx_no,
                   r.ssafy_transaction_no,
                   r.to_account_no,
                   r.to_address,
                   r.asset_amount,
                   r.tx_hash,
                   r.status,
                   r.fail_reason,
                   r.created_at,
                   r.processed_at,
                   COALESCE(pt.user_id, pay.user_id) AS owner_user_id,
                   pr.name AS product_name
              FROM refunds r
              LEFT JOIN participations pt ON pt.participation_id = r.participation_id
              LEFT JOIN group_buys gb ON gb.group_buy_id = pt.group_buy_id
              LEFT JOIN products pr ON pr.product_id = gb.product_id
              LEFT JOIN crypto_deposits cd ON cd.deposit_id = r.deposit_id
              LEFT JOIN payments pay ON pay.payment_id = cd.payment_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RefundQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** RFD-01 내 환불 목록. 최신순. */
    public Page<RefundRow> findMyRefunds(Long userId, String status, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("status", status)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String where = " WHERE COALESCE(pt.user_id, pay.user_id) = :userId"
                + (status == null ? "" : " AND r.status = :status");

        List<RefundRow> content = jdbcTemplate.query(
                SELECT_ROW + where
                        + " ORDER BY r.created_at DESC, r.refund_id DESC LIMIT :limit OFFSET :offset",
                parameters, this::mapRow);
        Long total = jdbcTemplate.queryForObject(countSql(where), parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /**
     * RFD-03 관리자 환불 목록. 실패 건 포함.
     *
     * <p>관리자는 모든 사용자의 환불을 봅니다. 상태 필터로 {@code FAILED} 만 뽑아
     * 재처리 대상을 찾는 것이 주된 쓰임입니다(FR-RFD-09).
     */
    public Page<RefundRow> findAll(String status, Pageable pageable) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String where = status == null ? "" : " WHERE r.status = :status";

        List<RefundRow> content = jdbcTemplate.query(
                SELECT_ROW + where
                        + " ORDER BY r.created_at DESC, r.refund_id DESC LIMIT :limit OFFSET :offset",
                parameters, this::mapRow);
        Long total = jdbcTemplate.queryForObject(countSql(where), parameters, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    /** RFD-02 환불 상세. 소유권 검증은 호출 측이 {@code ownerUserId} 로 합니다. */
    public Optional<RefundRow> findById(Long refundId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("refundId", refundId);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_ROW + " WHERE r.refund_id = :refundId", parameters, this::mapRow));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    /**
     * 목록 쿼리와 같은 조인을 유지합니다.
     *
     * <p>{@code COALESCE(pt.user_id, pay.user_id)} 조건이 조인에 의존하므로, count 에서
     * 조인을 빼면 소유자 필터가 동작하지 않아 전체 건수를 세게 됩니다.
     */
    private String countSql(String where) {
        return """
                SELECT COUNT(*)
                  FROM refunds r
                  LEFT JOIN participations pt ON pt.participation_id = r.participation_id
                  LEFT JOIN crypto_deposits cd ON cd.deposit_id = r.deposit_id
                  LEFT JOIN payments pay ON pay.payment_id = cd.payment_id
                """ + where;
    }

    private RefundRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RefundRow(
                rs.getLong("refund_id"),
                (Long) rs.getObject("participation_id"),
                (Long) rs.getObject("return_id"),
                (Long) rs.getObject("deposit_id"),
                rs.getLong("amount"),
                rs.getString("reason_type"),
                rs.getString("refund_method"),
                rs.getString("institution_tx_no"),
                (Long) rs.getObject("ssafy_transaction_no"),
                rs.getString("to_account_no"),
                rs.getString("to_address"),
                rs.getBigDecimal("asset_amount"),
                rs.getString("tx_hash"),
                rs.getString("status"),
                rs.getString("fail_reason"),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("processed_at")),
                (Long) rs.getObject("owner_user_id"),
                rs.getString("product_name"));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * 조회 결과 한 행.
     *
     * @param ownerUserId 환불의 주인. 참여 건 또는 입금을 거쳐 찾은 값이며, 두 경로 모두
     *                    끊긴 비정상 데이터면 {@code null} 입니다. 소유권 검증에서
     *                    {@code null} 은 "내 것이 아님" 으로 다뤄야 합니다.
     */
    public record RefundRow(
            Long refundId,
            Long participationId,
            Long returnId,
            Long depositId,
            long amount,
            String reasonType,
            String refundMethod,
            String institutionTxNo,
            Long ssafyTransactionNo,
            String toAccountNo,
            String toAddress,
            BigDecimal assetAmount,
            String txHash,
            String status,
            String failReason,
            LocalDateTime createdAt,
            LocalDateTime processedAt,
            Long ownerUserId,
            String productName
    ) {
    }
}
