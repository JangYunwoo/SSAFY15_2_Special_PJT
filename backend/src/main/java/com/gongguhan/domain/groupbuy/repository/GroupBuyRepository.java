package com.gongguhan.domain.groupbuy.repository;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupBuy g WHERE g.groupBuyId = :groupBuyId")
    Optional<GroupBuy> findWithLockByGroupBuyId(@Param("groupBuyId") Long groupBuyId);

    /**
     * Redis 슬롯 게이트의 사전 체크 전용. 반드시 프로젝션으로 조회한다 - 관리 엔티티를
     * 반환하면(예: findById) 영속성 컨텍스트에 특정 버전으로 캐시되고, 그 직후 같은
     * 트랜잭션에서 findWithLockByGroupBuyId로 다시 읽을 때 동시에 다른 트랜잭션이 그
     * 행을 커밋해버리면 ObjectOptimisticLockingFailureException이 발생한다.
     */
    @Query("SELECT g.targetParticipants AS targetParticipants, g.reservedSlots AS reservedSlots, "
            + "g.currentParticipants AS currentParticipants FROM GroupBuy g WHERE g.groupBuyId = :groupBuyId")
    Optional<SlotCounts> findSlotCounts(@Param("groupBuyId") Long groupBuyId);

    interface SlotCounts {
        int getTargetParticipants();

        int getReservedSlots();

        int getCurrentParticipants();
    }
}
