package com.gongguhan.domain.settlement.repository;

import com.gongguhan.domain.settlement.entity.SettlementItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    /** STL-02 상세. 이 정산에 포함된 참여 건들. */
    List<SettlementItem> findBySettlementId(Long settlementId);

    /**
     * 이미 어느 정산에든 포함된 참여 건인지.
     *
     * <p>배치가 대상을 고를 때 거르는 용도입니다. 동시 실행 경쟁은 이 조회로 막을 수
     * 없고 {@code UNIQUE(participation_id)} 가 최종 방어선입니다.
     */
    boolean existsByParticipationId(Long participationId);

    /** 여러 참여 건을 한 번에 확인한다. 배치가 건별로 조회하면 N+1 이 된다. */
    List<SettlementItem> findByParticipationIdIn(List<Long> participationIds);
}
