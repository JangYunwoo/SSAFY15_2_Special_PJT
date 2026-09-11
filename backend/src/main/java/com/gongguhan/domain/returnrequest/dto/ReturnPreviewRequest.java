package com.gongguhan.domain.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnPreviewRequest(
        @NotBlank String reasonType,
        @Size(max = 500) String detailReason
) {
}
