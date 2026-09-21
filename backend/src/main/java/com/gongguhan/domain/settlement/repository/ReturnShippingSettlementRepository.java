package com.gongguhan.domain.settlement.repository;

import com.gongguhan.domain.returnrequest.entity.ReturnRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 반품 원장·결제 스냅샷·에스크로로 배송비임이 증명되는 잔액만 조회한다. */
public interface ReturnShippingSettlementRepository extends Repository<ReturnRequest, Long> {
    @Query("""
            SELECT p.participationId AS participationId, p.groupBuyId AS groupBuyId,
                   e.amount - e.refundedAmount AS shippingFee, g.sellerId AS sellerId,
                   g.roundingDiff AS roundingDiff, g.platformFeeRate AS platformFeeRate
              FROM ReturnRequest r, Participation p, GroupBuy g, Escrow e, Refund refund
             WHERE r.participation.participationId = p.participationId
               AND p.groupBuyId = g.groupBuyId AND e.participationId = p.participationId
               AND refund.returnId = r.returnId AND refund.participationId = p.participationId
               AND refund.status = com.gongguhan.domain.refund.entity.RefundStatus.COMPLETED
               AND refund.reasonType = com.gongguhan.domain.refund.entity.RefundReason.RETURN
               AND r.status = 'REFUNDED' AND p.status = 'REFUNDED' AND p.confirmHold = false
               AND r.reasonType = 'CHANGE_OF_MIND' AND r.feeBearer = 'BUYER'
               AND r.adminReviewRequired = false
               AND p.settlementEligibleAt IS NOT NULL AND p.settlementEligibleAt <= :now
               AND e.status = com.gongguhan.domain.escrow.entity.EscrowStatus.HELD
               AND p.shareAmount >= 0 AND p.shippingFee >= 0 AND r.returnShippingFee >= 0
               AND p.totalAmount = p.shareAmount + p.shippingFee AND e.amount = p.totalAmount
               AND r.refundAmount = CASE WHEN p.shareAmount > r.returnShippingFee THEN p.shareAmount - r.returnShippingFee ELSE 0 END
               AND refund.amount = r.refundAmount AND e.refundedAmount = r.refundAmount
               AND e.amount > e.refundedAmount
               AND (:participationId IS NULL OR p.participationId = :participationId)
               AND NOT EXISTS (SELECT si.itemId FROM SettlementItem si WHERE si.participationId = p.participationId)
               AND NOT EXISTS (SELECT other.returnId FROM ReturnRequest other WHERE other.participation.participationId = p.participationId AND other.status NOT IN ('REFUNDED','REJECTED'))
             ORDER BY p.groupBuyId, p.participationId
            """)
    List<ShippingTarget> findTargets(@Param("now") LocalDateTime now, @Param("participationId") Long participationId);

    interface ShippingTarget {
        Long getParticipationId(); Long getGroupBuyId(); long getShippingFee(); Long getSellerId();
        long getRoundingDiff(); BigDecimal getPlatformFeeRate();
    }
}
