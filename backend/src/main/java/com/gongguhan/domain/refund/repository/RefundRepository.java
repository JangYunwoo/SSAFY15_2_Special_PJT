package com.gongguhan.domain.refund.repository;

import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundStatus;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

/**
 * 환불 원장 저장소.
 *
 * <p>조회 API(RFD-01~03)용 목록 쿼리는 여기 두지 않습니다. 사용자별 환불 목록은
 * {@code refunds} 에 {@code user_id} 가 없어 참여 건 또는 입금을 거쳐 결제로
 * 올라가야 하고, 그 조인은 QueryDSL 로 따로 다룹니다. 이 인터페이스는 원장을
 * 쓰고 되짚는 데 필요한 만큼만 가집니다.
 */
public interface RefundRepository extends JpaRepository<Refund, Long> {
    /** 결제 이력 한 페이지의 참여 환불을 한 번에 읽는다. */
    List<Refund> findByParticipationIdIn(Collection<Long> participationIds);

    @Query("""
            SELECT r.refundId FROM Refund r
             WHERE r.participationId IS NOT NULL
               AND r.status = com.gongguhan.domain.refund.entity.RefundStatus.PROCESSING
               AND r.executionClaimed = false
               AND r.refundMethod <> com.gongguhan.domain.refund.entity.RefundMethod.CARD_CANCEL
               AND r.createdAt <= :cutoff
             ORDER BY r.refundId
            """)
    List<Long> findUnsent(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);

    @Query("""
            SELECT r.refundId FROM Refund r, Participation p
             WHERE r.participationId = p.participationId
               AND r.status = com.gongguhan.domain.refund.entity.RefundStatus.COMPLETED
               AND p.status <> 'REFUNDED'
             ORDER BY r.refundId
            """)
    List<Long> findUnfinalized(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Refund r WHERE r.refundId = :refundId")
    Optional<Refund> findForUpdate(@Param("refundId") Long refundId);

    /**
     * 해당 참여 건에 진행 중이거나 완료된 환불을 찾습니다.
     *
     * <p>{@code FAILED} 를 제외하는 이유는 실패한 환불이 재처리 대상으로 남아 있을
     * 뿐 자금 흐름을 차지하지 않기 때문입니다. 같은 조건이
     * {@code UNIQUE(participation_id, refund_marker)} 로 DB 에도 걸려 있어, 이 조회는
     * 사용자에게 이유를 알려주기 위한 것이고 경쟁 차단은 DB 가 합니다.
     */
    @Query("""
            SELECT r FROM Refund r
             WHERE r.participationId = :participationId
               AND (r.status <> com.gongguhan.domain.refund.entity.RefundStatus.FAILED OR r.executionClaimed = true)
            """)
    Optional<Refund> findActiveByParticipationId(@Param("participationId") Long participationId);

    /** 해당 입금에 진행 중이거나 완료된 환불을 찾습니다. 하나의 입금은 한 번만 돌려보냅니다. */
    @Query("""
            SELECT r FROM Refund r
             WHERE r.depositId = :depositId
               AND (r.status <> com.gongguhan.domain.refund.entity.RefundStatus.FAILED OR r.executionClaimed = true)
            """)
    Optional<Refund> findActiveByDepositId(@Param("depositId") Long depositId);

    /** 해당 결제에 진행 중이거나 완료된(참여 없이 결제 단위로 개시된) 환불을 찾습니다. */
    @Query("""
            SELECT r FROM Refund r
             WHERE r.paymentId = :paymentId
               AND (r.status <> com.gongguhan.domain.refund.entity.RefundStatus.FAILED OR r.executionClaimed = true)
            """)
    Optional<Refund> findActiveByPaymentId(@Param("paymentId") Long paymentId);

    /** 결제 단위(참여 없음) 환불 중 아직 송신하지 않은 건을 복구 스캔이 재개 대상으로 가져온다. */
    @Query("""
            SELECT r.refundId FROM Refund r
             WHERE r.paymentId IS NOT NULL
               AND r.status = com.gongguhan.domain.refund.entity.RefundStatus.PROCESSING
               AND r.executionClaimed = false
               AND r.refundMethod <> com.gongguhan.domain.refund.entity.RefundMethod.CARD_CANCEL
               AND r.createdAt <= :cutoff
             ORDER BY r.refundId
            """)
    List<Long> findUnsentStrandedPayment(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);

    /**
     * 온체인 해시로 환불을 찾습니다.
     *
     * <p>송금은 브로드캐스트된 뒤 응답을 못 받는 경우가 있습니다. 그때 "이 해시가 이미
     * 우리 원장에 있는가" 를 확인해 이중 송금을 피하는 데 씁니다.
     */
    Optional<Refund> findByTxHash(String txHash);

    /** 재처리 배치·관리자 목록에서 쓰는 상태별 조회. */
    List<Refund> findByStatusOrderByCreatedAtAsc(RefundStatus status, Pageable pageable);

    /** 반품 한 건에서 시작된 환불. 반품 상세에 처리 결과를 붙일 때 씁니다. */
    Optional<Refund> findByReturnId(Long returnId);
}
