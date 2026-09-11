package com.gongguhan.domain.payment.kakaopay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KakaoPayApproveRequest(
        @NotNull Long paymentId,
        @NotBlank String pgToken,
        @NotBlank String state
) {
}
