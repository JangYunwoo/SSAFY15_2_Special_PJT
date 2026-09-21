package com.gongguhan.domain.delivery.repository;

import com.gongguhan.domain.delivery.entity.Delivery;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @Query("SELECT d.participationId FROM Delivery d WHERE d.deliveryId = :deliveryId")
    Optional<Long> findParticipationIdByDeliveryId(@Param("deliveryId") Long deliveryId);

    Optional<Delivery> findByParticipationId(Long participationId);

    List<Delivery> findByParticipationIdIn(Collection<Long> participationIds);

    /** FAKE_INVOICE 감지(S15P21B205-77): 발송 처리 후 기한이 지나도록 배송완료가 안 된 건. */
    List<Delivery> findByStatusAndShippedAtBefore(String status, LocalDateTime threshold);

    boolean existsByCourierCodeAndTrackingNumberAndDeliveryIdNot(
            String courierCode, String trackingNumber, Long deliveryId);
}
