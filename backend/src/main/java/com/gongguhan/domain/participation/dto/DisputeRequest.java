package com.gongguhan.domain.participation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** CFM-02 이의 제기 요청 (FE DisputeRequest 계약). type: NOT_DELIVERED | ITEM_MISMATCH. */
public record DisputeRequest(
        @NotBlank String type,
        @NotBlank @Size(max = 400) String detailReason
) {
}
