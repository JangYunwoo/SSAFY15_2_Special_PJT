package com.gongguhan.domain.settlement.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 정산 대상 조회 (FR-STL-01~05).
 *
 * <p>참여·공동구매·에스크로·정산항목을 한 번에 봐야 해서 SQL 로 씁니다. 팀 규칙상 JPA
 * 연관관계를 쓰지 않아 엔티티 그래프로는 이 조인을 표현할 수 없고, 배치가 건별로
 * 조회하면 N+1 이 됩니다. {@code ParticipationQueryRepository} 와 같은 패턴입니다.
 *
 * <p>다른 도메인 테이블은 <b>읽기만</b> 합니다.
 */
@Repository
public class SettlementTargetRepository {

    /**
     * 정산 대상 참여 건.
     *
     * <p>거르는 조건과 이유:
     *
     * <table border="1">
     *   <caption>조건</caption>
     *   <tr><th>조건</th><th>이유</th></tr>
     *   <tr><td>{@code status = 'CONFIRMED'}</td>
     *       <td>구매확정된 건만 정산한다 (DR-33·34). 배송완료만으로는 안 된다</td></tr>
     *   <tr><td>{@code settlement_eligible_at <= now}</td>
     *       <td>배송완료 + 7일. 이 기간에 반품이 들어올 수 있다</td></tr>
     *   <tr><td>정산 항목에 없음</td>
     *       <td>이미 정산된 건 제외. 최종 방어선은 {@code UNIQUE(participation_id)}</td></tr>
     *   <tr><td>에스크로 {@code HELD}</td>
     *       <td>이미 해제·환불된 건은 보관 중인 돈이 없다</td></tr>
     *   <tr><td>{@code refunded_amount = 0}</td>
     *       <td><b>부분 환불 건은 자동 배치에서 뺀다</b> — 아래 설명</td></tr>
     * </table>
     *
     * <p><b>부분 환불 건을 제외하는 이유.</b> 54,000원(분담금 50,000 + 배송비 4,000) 중
     * 42,000원을 환불하면 남은 12,000원은 원배송비와 반품배송비이지 분담금이 아닙니다.
     * 플랫폼 수수료는 분담금에만 매기므로, 남은 금액을 분담금과 배송비로 어떻게 나눌지
     * 정해야 수수료를 계산할 수 있습니다. <b>그 배분 규칙이 명세에 없습니다.</b>
     * 임의로 정하면 판매자가 받을 돈이 조용히 달라지므로, 자동 배치에서 빼고 제외 사유로
     * 드러냅니다({@link #findExcluded}). 규칙이 정해지면 이 조건을 풀면 됩니다.
     */
    private static final String SELECT_TARGET = """
            SELECT p.participation_id,
                   p.group_buy_id,
                   p.share_amount,
                   p.shipping_fee,
                   gb.seller_id,
                   gb.rounding_diff,
                   gb.platform_fee_rate
              FROM participations p
              JOIN group_buys gb ON gb.group_buy_id = p.group_buy_id
              JOIN escrows e ON e.participation_id = p.participation_id
             WHERE p.status = 'CONFIRMED'
               AND p.settlement_eligible_at IS NOT NULL
               AND p.settlement_eligible_at <= :now
               AND e.status = 'HELD'
               AND e.refunded_amount = 0
               AND NOT EXISTS (
                   SELECT 1 FROM settlement_items si
                    WHERE si.participation_id = p.participation_id
               )
             ORDER BY p.group_buy_id, p.participation_id
            """;

    /**
     * 정산에서 빠진 참여 건과 사유 (STL-02, FR-STL-07~08).
     *
     * <p>판매자가 "왜 이 건은 안 들어왔나" 를 묻지 않게 하려면 제외를 보여줘야 합니다.
     * 제외를 {@code settlement_items} 에 행으로 남기지 않는 이유는, 나중에 그 건이
     * 정산될 때 {@code UNIQUE(participation_id)} 와 충돌하기 때문입니다. 조회 시점에
     * 계산합니다.
     */
    private static final String SELECT_EXCLUDED = """
            SELECT p.participation_id,
                   p.status,
                   p.settlement_eligible_at,
                   COALESCE(e.status, 'NONE') AS escrow_status,
                   COALESCE(e.refunded_amount, 0) AS refunded_amount
              FROM participations p
              LEFT JOIN escrows e ON e.participation_id = p.participation_id
             WHERE p.group_buy_id = :groupBuyId
               AND NOT EXISTS (
                   SELECT 1 FROM settlement_items si
                    WHERE si.participation_id = p.participation_id
               )
             ORDER BY p.participation_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SettlementTargetRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 정산 가능한 참여 건을 공동구매 순으로 모아 돌려줍니다. */
    public List<SettlementTarget> findTargets(LocalDateTime now) {
        return jdbcTemplate.query(SELECT_TARGET,
                new MapSqlParameterSource("now", now), this::mapTarget);
    }

    /** 해당 공동구매에서 아직 정산되지 않은 참여 건과 그 사유. */
    public List<ExcludedParticipation> findExcluded(Long groupBuyId) {
        return jdbcTemplate.query(SELECT_EXCLUDED,
                new MapSqlParameterSource("groupBuyId", groupBuyId), this::mapExcluded);
    }

    private SettlementTarget mapTarget(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementTarget(
                rs.getLong("participation_id"),
                rs.getLong("group_buy_id"),
                rs.getLong("share_amount"),
                rs.getLong("shipping_fee"),
                rs.getLong("seller_id"),
                rs.getLong("rounding_diff"),
                rs.getBigDecimal("platform_fee_rate"));
    }

    private ExcludedParticipation mapExcluded(ResultSet rs, int rowNum) throws SQLException {
        String participationStatus = rs.getString("status");
        String escrowStatus = rs.getString("escrow_status");
        long refunded = rs.getLong("refunded_amount");
        java.sql.Timestamp eligibleAt = rs.getTimestamp("settlement_eligible_at");

        return new ExcludedParticipation(
                rs.getLong("participation_id"),
                participationStatus,
                resolveReason(participationStatus, escrowStatus, refunded, eligibleAt));
    }

    /**
     * 제외 사유를 정합니다.
     *
     * <p>순서가 있습니다 — 반품된 건은 "확정 안 됨" 이기도 하지만, 판매자에게는
     * <b>반품</b>이라고 말해야 이해됩니다. 더 구체적인 사유를 먼저 봅니다.
     */
    private String resolveReason(String participationStatus, String escrowStatus,
                                 long refundedAmount, java.sql.Timestamp eligibleAt) {
        if (refundedAmount > 0) {
            return ExcludedReason.PARTIAL_REFUND;
        }
        if ("REFUNDED".equals(escrowStatus)) {
            return ExcludedReason.REFUNDED;
        }
        if ("RETURNING".equals(participationStatus) || "RETURNED".equals(participationStatus)) {
            return ExcludedReason.RETURNED;
        }
        if (!"CONFIRMED".equals(participationStatus)) {
            return ExcludedReason.NOT_CONFIRMED;
        }
        if (eligibleAt == null || eligibleAt.toLocalDateTime().isAfter(LocalDateTime.now())) {
            return ExcludedReason.NOT_ELIGIBLE_YET;
        }
        return ExcludedReason.NOT_CONFIRMED;
    }

    /** 제외 사유 값. 프론트가 문구로 바꿔 보여줍니다. */
    public static final class ExcludedReason {

        /** 아직 구매확정되지 않음. */
        public static final String NOT_CONFIRMED = "NOT_CONFIRMED";
        /** 확정됐지만 정산 가능 시점(배송완료 + 7일) 전. */
        public static final String NOT_ELIGIBLE_YET = "NOT_ELIGIBLE_YET";
        /** 반품 진행 중이거나 완료됨. */
        public static final String RETURNED = "RETURNED";
        /** 전액 환불됨. 보관 중인 돈이 없음. */
        public static final String REFUNDED = "REFUNDED";
        /** 부분 환불됨. 잔액 배분 규칙이 정해지면 정산 대상이 됨. */
        public static final String PARTIAL_REFUND = "PARTIAL_REFUND";

        private ExcludedReason() {
        }
    }

    /**
     * 정산 대상 한 건.
     *
     * @param roundingDiff    공동구매의 절상 차액. 회차마다 중복 가산하지 않도록 주의
     * @param platformFeeRate 공동구매에 확정된 수수료율
     */
    public record SettlementTarget(
            Long participationId,
            Long groupBuyId,
            long shareAmount,
            long shippingFee,
            Long sellerId,
            long roundingDiff,
            BigDecimal platformFeeRate
    ) {
    }

    /** 정산에서 빠진 참여 건. */
    public record ExcludedParticipation(
            Long participationId,
            String participationStatus,
            String excludedReason
    ) {
    }
}
