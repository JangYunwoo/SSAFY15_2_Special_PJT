package com.gongguhan.domain.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** DLV-02 송장 등록 요청. shippedAt이 없으면 서버 시각(UTC)으로 기록한다. */
public record DeliveryInvoiceRequest(
        @NotBlank @Size(max = 20) String carrierCode,
        @NotBlank @Size(max = 50) String trackingNumber,
        OffsetDateTime shippedAt
) {
}
