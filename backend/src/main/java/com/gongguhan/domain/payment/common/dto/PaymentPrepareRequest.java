package com.gongguhan.domain.payment.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentPrepareRequest(
        @NotBlank String method,
        @NotNull Long addressId,
        Long cryptoAssetId,
        @Size(max = 100) String deliveryRequest
) {
    public PaymentPrepareRequest(String method, Long addressId, Long cryptoAssetId) {
        this(method, addressId, cryptoAssetId, null);
    }
}
