package com.gongguhan.domain.delivery.repository;

import com.gongguhan.domain.delivery.entity.Delivery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByParticipationId(Long participationId);

    List<Delivery> findByParticipationIdIn(Collection<Long> participationIds);

    boolean existsByCourierCodeAndTrackingNumberAndDeliveryIdNot(
            String courierCode, String trackingNumber, Long deliveryId);
}
