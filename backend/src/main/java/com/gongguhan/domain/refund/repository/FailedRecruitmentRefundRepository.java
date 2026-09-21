package com.gongguhan.domain.refund.repository;

import com.gongguhan.domain.participation.entity.Participation;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** FAILED 자체를 영속 작업 근거로 사용한다. 메모리 이벤트가 없어도 다음 스캔이 발견한다. */
public interface FailedRecruitmentRefundRepository extends Repository<Participation, Long> {
    @Query("""
            SELECT p.participationId FROM Participation p, GroupBuy g, Payment payment
             WHERE p.groupBuyId = g.groupBuyId AND payment.participationId = p.participationId
               AND g.status = 'FAILED' AND p.status = 'PAID' AND payment.status = 'PAID'
               AND payment.method IN ('CARD', 'KAKAOPAY', 'CRYPTO')
               AND (:groupBuyId IS NULL OR g.groupBuyId = :groupBuyId)
               AND NOT EXISTS (SELECT r.refundId FROM Refund r WHERE r.participationId = p.participationId)
             ORDER BY p.participationId
            """)
    List<Long> findCandidates(@Param("groupBuyId") Long groupBuyId, Pageable pageable);
}
