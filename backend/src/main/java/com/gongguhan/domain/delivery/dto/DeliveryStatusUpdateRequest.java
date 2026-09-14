package com.gongguhan.domain.delivery.dto;

import jakarta.validation.constraints.NotBlank;

/** DLV-03·DLV-04 배송 상태 변경 요청. 허용값: SHIPPING, DELIVERED. */
public record DeliveryStatusUpdateRequest(
        @NotBlank String status
) {
}
