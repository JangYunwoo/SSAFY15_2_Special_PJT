package com.gongguhan.domain.payment.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentPrepareRequest(
        @NotBlank String method,
        @NotNull Long addressId,
        Long cryptoAssetId
) {
}
