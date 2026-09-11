package com.gongguhan.domain.delivery.dto;

import com.gongguhan.domain.delivery.entity.Delivery;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 배송 단건 응답 (DLV-02·03 처리 결과, DLV-05 사용자 조회). */
public record DeliveryResponse(
        Long deliveryId,
        Long participationId,
        String status,
        String carrierCode,
        String trackingNumber,
        OffsetDateTime shippedAt,
        OffsetDateTime deliveredAt
) {

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getDeliveryId(),
                delivery.getParticipationId(),
                delivery.getStatus(),
                delivery.getCourierCode(),
                delivery.getTrackingNumber(),
                toUtc(delivery.getShippedAt()),
                toUtc(delivery.getDeliveredAt()));
    }

    static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
