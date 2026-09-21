package com.gongguhan.domain.participation.repository;

import com.gongguhan.domain.participation.entity.Participation;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByGroupBuyIdAndUserIdAndActiveMarker(Long groupBuyId, Long userId, String activeMarker);

    List<Participation> findByGroupBuyIdOrderByParticipationIdAsc(Long groupBuyId);

    @Query("SELECT p.participationId FROM Participation p WHERE p.groupBuyId = :groupBuyId ORDER BY p.participationId")
    List<Long> findIdsByGroupBuyIdOrderByParticipationIdAsc(@Param("groupBuyId") Long groupBuyId);

    List<Participation> findByGroupBuyIdAndStatus(Long groupBuyId, String status);

    List<Participation> findByStatusAndConfirmHoldFalseAndDeliveredAtBefore(
            String status, LocalDateTime threshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Participation p WHERE p.participationId = :participationId")
    Optional<Participation> findWithLockByParticipationId(
            @Param("participationId") Long participationId);
}
