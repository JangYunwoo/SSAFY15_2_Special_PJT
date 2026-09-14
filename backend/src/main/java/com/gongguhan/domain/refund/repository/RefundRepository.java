package com.gongguhan.domain.refund.repository;

import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
               AND r.status <> com.gongguhan.domain.refund.entity.RefundStatus.FAILED
            """)
    Optional<Refund> findActiveByParticipationId(@Param("participationId") Long participationId);

    /** 해당 입금에 진행 중이거나 완료된 환불을 찾습니다. 하나의 입금은 한 번만 돌려보냅니다. */
    @Query("""
            SELECT r FROM Refund r
             WHERE r.depositId = :depositId
               AND r.status <> com.gongguhan.domain.refund.entity.RefundStatus.FAILED
            """)
    Optional<Refund> findActiveByDepositId(@Param("depositId") Long depositId);

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
