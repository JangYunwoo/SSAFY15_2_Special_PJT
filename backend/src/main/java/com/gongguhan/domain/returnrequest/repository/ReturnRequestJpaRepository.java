package com.gongguhan.domain.returnrequest.repository;

import com.gongguhan.domain.returnrequest.entity.ReturnRequest;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReturnRequestJpaRepository extends JpaRepository<ReturnRequest, Long> {

    @Query("SELECT r.participation.participationId FROM ReturnRequest r WHERE r.returnId = :returnId")
    Optional<Long> findParticipationId(@Param("returnId") Long returnId);

    boolean existsByParticipationParticipationIdAndStatusNotIn(
            Long participationId, Collection<String> terminalStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "participation")
    @Query("SELECT r FROM ReturnRequest r WHERE r.returnId = :returnId")
    Optional<ReturnRequest> findWithLockByReturnId(@Param("returnId") Long returnId);

    @EntityGraph(attributePaths = "participation")
    @Query("""
            SELECT r FROM ReturnRequest r
             WHERE r.returnId = :returnId AND r.participation.userId = :userId
            """)
    Optional<ReturnRequest> findBuyerDetail(
            @Param("userId") Long userId, @Param("returnId") Long returnId);

    @EntityGraph(attributePaths = "participation")
    @Query("""
            SELECT r FROM ReturnRequest r, GroupBuy g
             WHERE r.returnId = :returnId
               AND r.participation.groupBuyId = g.groupBuyId
               AND g.sellerId = :sellerId
            """)
    Optional<ReturnRequest> findSellerDetail(
            @Param("sellerId") Long sellerId, @Param("returnId") Long returnId);

    @Query(value = """
            SELECT r.returnId AS id,
                   p.participationId AS participationId,
                   p.groupBuyId AS groupBuyId,
                   product.name AS productName,
                   buyer.nickname AS buyerName,
                   r.reasonType AS reasonType,
                   r.reasonDetail AS detailReason,
                   r.status AS status,
                   r.refundAmount AS refundAmount,
                   r.returnShippingFee AS returnShippingFee,
                   r.feeBearer AS shippingFeeBearer,
                   r.requestedAt AS requestedAt,
                   r.autoApproveAt AS autoApproveAt,
                   r.postConfirm AS postConfirm,
                   r.adminReviewRequired AS adminReviewRequired
              FROM ReturnRequest r, Participation p, GroupBuy g, Product product, User buyer
             WHERE r.participation.userId = :userId
               AND r.participation.participationId = p.participationId
               AND p.groupBuyId = g.groupBuyId
               AND g.productId = product.productId
               AND p.userId = buyer.userId
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR r.participation.groupBuyId = :groupBuyId)
            """,
            countQuery = """
            SELECT COUNT(r)
              FROM ReturnRequest r
             WHERE r.participation.userId = :userId
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR r.participation.groupBuyId = :groupBuyId)
            """)
    Page<ReturnSummaryView> findBuyerList(
            @Param("userId") Long userId, @Param("status") String status,
            @Param("groupBuyId") Long groupBuyId, Pageable pageable);

    @Query(value = """
            SELECT r.returnId AS id,
                   p.participationId AS participationId,
                   p.groupBuyId AS groupBuyId,
                   product.name AS productName,
                   buyer.nickname AS buyerName,
                   r.reasonType AS reasonType,
                   r.reasonDetail AS detailReason,
                   r.status AS status,
                   r.refundAmount AS refundAmount,
                   r.returnShippingFee AS returnShippingFee,
                   r.feeBearer AS shippingFeeBearer,
                   r.requestedAt AS requestedAt,
                   r.autoApproveAt AS autoApproveAt,
                   r.postConfirm AS postConfirm,
                   r.adminReviewRequired AS adminReviewRequired
              FROM ReturnRequest r, Participation p, GroupBuy g, Product product, User buyer
             WHERE r.participation.participationId = p.participationId
               AND p.groupBuyId = g.groupBuyId
               AND g.productId = product.productId
               AND p.userId = buyer.userId
               AND g.sellerId = :sellerId
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR r.participation.groupBuyId = :groupBuyId)
            """,
            countQuery = """
            SELECT COUNT(r)
              FROM ReturnRequest r, GroupBuy g
             WHERE r.participation.groupBuyId = g.groupBuyId
               AND g.sellerId = :sellerId
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR r.participation.groupBuyId = :groupBuyId)
            """)
    Page<ReturnSummaryView> findSellerList(
            @Param("sellerId") Long sellerId, @Param("status") String status,
            @Param("groupBuyId") Long groupBuyId, Pageable pageable);

    @Query(value = """
            SELECT r.returnId AS id,
                   p.participationId AS participationId,
                   p.groupBuyId AS groupBuyId,
                   product.name AS productName,
                   buyer.nickname AS buyerName,
                   r.reasonType AS reasonType,
                   r.reasonDetail AS detailReason,
                   r.status AS status,
                   r.refundAmount AS refundAmount,
                   r.returnShippingFee AS returnShippingFee,
                   r.feeBearer AS shippingFeeBearer,
                   r.requestedAt AS requestedAt,
                   r.autoApproveAt AS autoApproveAt,
                   r.postConfirm AS postConfirm,
                   r.adminReviewRequired AS adminReviewRequired
              FROM ReturnRequest r, Participation p, GroupBuy g, Product product, User buyer
             WHERE r.participation.participationId = p.participationId
               AND p.groupBuyId = g.groupBuyId
               AND g.productId = product.productId
               AND p.userId = buyer.userId
               AND r.adminReviewRequired = true
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR p.groupBuyId = :groupBuyId)
            """,
            countQuery = """
            SELECT COUNT(r)
              FROM ReturnRequest r
             WHERE r.adminReviewRequired = true
               AND (:status IS NULL OR r.status = :status)
               AND (:groupBuyId IS NULL OR r.participation.groupBuyId = :groupBuyId)
            """)
    Page<ReturnSummaryView> findAdminReviewList(
            @Param("status") String status, @Param("groupBuyId") Long groupBuyId,
            Pageable pageable);

    @EntityGraph(attributePaths = "participation")
    Optional<ReturnRequest> findDetailByReturnId(Long returnId);

    @Query("""
            SELECT r.returnId FROM ReturnRequest r
             WHERE r.status = 'REQUESTED'
               AND r.adminReviewRequired = false
               AND r.autoApproveAt <= :now
             ORDER BY r.autoApproveAt, r.returnId
            """)
    List<Long> findAutoApprovalCandidates(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            SELECT r.returnId FROM ReturnRequest r
             WHERE r.status = 'APPROVED' AND r.autoRefundDueAt <= :now
             ORDER BY r.autoRefundDueAt, r.returnId
            """)
    List<Long> findAutoRefundCandidates(@Param("now") LocalDateTime now, Pageable pageable);
}
