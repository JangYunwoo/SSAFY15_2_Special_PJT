package com.gongguhan.domain.delivery.dto;

import java.util.List;

/** DLV-01 응답. invoiceRegistrationRate는 0~100(%) 소수 첫째 자리까지. */
public record SellerDeliveryListResponse(
        int totalCount,
        int invoiceRegisteredCount,
        double invoiceRegistrationRate,
        List<SellerDeliveryItemResponse> deliveries
) {

    public static SellerDeliveryListResponse of(List<SellerDeliveryItemResponse> deliveries) {
        int total = deliveries.size();
        int registered = (int) deliveries.stream()
                .filter(item -> item.trackingNumber() != null)
                .count();
        double rate = total == 0 ? 0.0 : Math.round(registered * 1000.0 / total) / 10.0;
        return new SellerDeliveryListResponse(total, registered, rate, deliveries);
    }
}
