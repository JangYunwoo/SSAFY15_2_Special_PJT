package com.gongguhan.domain.settlement.repository;

import com.gongguhan.domain.settlement.entity.Settlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    /**
     * 다음 회차 번호.
     *
     * <p>같은 공동구매의 참여자들이 동시에 구매확정하지 않아 정산이 여러 번 나뉩니다.
     * {@code UNIQUE(group_buy_id, round_no)} 가 걸려 있어 번호가 겹치면 DB 가
     * 거부합니다 — 이 조회는 번호를 정하기 위한 것이고, 경쟁 차단은 DB 가 합니다.
     */
    @Query("""
           select coalesce(max(s.roundNo), 0) + 1 from Settlement s
            where s.groupBuyId = :groupBuyId
           """)
    int nextRoundNo(@Param("groupBuyId") Long groupBuyId);

    /** STL-01 판매자 정산 목록. 최신순. */
    Page<Settlement> findBySellerIdOrderByCreatedAtDesc(Long sellerId, Pageable pageable);

    /** STL-03 관리자 전체 목록. */
    Page<Settlement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** STL-03 상태 필터. 보류 건이나 이체 실패 건만 추리는 것이 주된 쓰임이다. */
    Page<Settlement> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * 자동 재시도 대상 (STL-07).
     *
     * <p><b>{@code transfer_status = 'FAILED'} 만 고릅니다.</b> 응답을 받지 못한
     * {@code 'UNKNOWN'} 건은 이체가 이미 일어났을 수 있어 자동으로 다시 보내면
     * 판매자에게 두 번 지급됩니다. 그 건은 사람이 금융망 거래내역을 확인한 뒤 수동으로
     * 처리합니다.
     *
     * <p>보류({@code HOLD})·완료 건도 제외합니다 — 상태가 {@code PENDING} 인 것만
     * 실행 대상입니다.
     */
    @Query("""
           select s from Settlement s
            where s.status = 'PENDING'
              and s.transferStatus = 'FAILED'
            order by s.createdAt asc
           """)
    List<Settlement> findAutoRetryTargets(Pageable pageable);

    /** 공동구매의 정산 이력. 회차별로 얼마가 나갔는지 확인할 때 쓴다. */
    List<Settlement> findByGroupBuyIdOrderByRoundNoAsc(Long groupBuyId);

    /** 소유권 검증을 쿼리에서 끝낸다. 남의 정산은 애초에 조회되지 않는다. */
    Optional<Settlement> findBySettlementIdAndSellerId(Long settlementId, Long sellerId);

    /** 판매자에게 실제로 지급된 총액 (FR-STL-14). */
    @Query("""
           select coalesce(sum(s.settlementAmount), 0) from Settlement s
            where s.sellerId = :sellerId
              and s.status = 'COMPLETED'
           """)
    long sumCompletedAmount(@Param("sellerId") Long sellerId);
}
