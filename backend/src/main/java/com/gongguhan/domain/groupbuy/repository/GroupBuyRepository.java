package com.gongguhan.domain.groupbuy.repository;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupBuyRepository extends JpaRepository<GroupBuy, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GroupBuy g WHERE g.groupBuyId = :groupBuyId")
    Optional<GroupBuy> findWithLockByGroupBuyId(@Param("groupBuyId") Long groupBuyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT g
              FROM GroupBuy g
             WHERE g.status = 'RECRUITING'
               AND (g.currentParticipants >= g.targetParticipants OR g.recruitEndAt <= :judgedAt)
             ORDER BY g.recruitEndAt, g.groupBuyId
            """)
    List<GroupBuy> findJudgementCandidates(
            @Param("judgedAt") LocalDateTime judgedAt, Pageable pageable);
}
