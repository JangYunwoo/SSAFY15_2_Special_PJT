package com.gongguhan.domain.participation.repository;

import com.gongguhan.domain.participation.entity.Participation;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByGroupBuyIdAndUserIdAndActiveMarker(Long groupBuyId, Long userId, String activeMarker);

    List<Participation> findByGroupBuyIdOrderByParticipationIdAsc(Long groupBuyId);

    List<Participation> findByGroupBuyIdAndStatus(Long groupBuyId, String status);

    List<Participation> findByStatusAndConfirmHoldFalseAndDeliveredAtBefore(
            String status, LocalDateTime threshold);
}
