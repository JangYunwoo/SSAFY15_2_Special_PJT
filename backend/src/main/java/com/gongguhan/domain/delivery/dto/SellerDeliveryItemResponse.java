package com.gongguhan.domain.delivery.dto;

import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.participation.entity.Participation;
import java.time.OffsetDateTime;

/**
 * DLV-01 참여자별 배송 행. 모집 완료 직후 배송 행이 아직 없는 참여(이론상 경계)도
 * 목록에서 빠지지 않도록 delivery 쪽 필드는 null을 허용한다.
 */
public record SellerDeliveryItemResponse(
        Long deliveryId,
        Long participationId,
        String recipientName,
        String recipientPhone,
        String zipcode,
        String address1,
        String address2,
        String participationStatus,
        String deliveryStatus,
        String carrierCode,
        String trackingNumber,
        OffsetDateTime shippedAt,
        OffsetDateTime deliveredAt
) {

    public static SellerDeliveryItemResponse of(Participation participation, Delivery delivery) {
        return new SellerDeliveryItemResponse(
                delivery == null ? null : delivery.getDeliveryId(),
                participation.getParticipationId(),
                participation.getRecipientName(),
                participation.getRecipientPhone(),
                participation.getZipcode(),
                participation.getAddress1(),
                participation.getAddress2(),
                participation.getStatus(),
                delivery == null ? null : delivery.getStatus(),
                delivery == null ? null : delivery.getCourierCode(),
                delivery == null ? null : delivery.getTrackingNumber(),
                delivery == null ? null : DeliveryResponse.toUtc(delivery.getShippedAt()),
                delivery == null ? null : DeliveryResponse.toUtc(delivery.getDeliveredAt()));
    }
}
