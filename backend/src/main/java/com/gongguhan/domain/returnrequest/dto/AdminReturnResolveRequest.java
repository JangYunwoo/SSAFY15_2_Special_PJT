package com.gongguhan.domain.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReturnResolveRequest(
        @NotBlank @Size(max = 10) String decision,
        @Size(max = 255) String reason
) {
}
