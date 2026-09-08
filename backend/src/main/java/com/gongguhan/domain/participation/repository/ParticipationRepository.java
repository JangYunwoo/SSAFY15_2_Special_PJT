package com.gongguhan.domain.participation.repository;

import com.gongguhan.domain.participation.entity.Participation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    boolean existsByGroupBuyIdAndUserIdAndActiveMarker(Long groupBuyId, Long userId, String activeMarker);
}
