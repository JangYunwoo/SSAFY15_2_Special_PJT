package com.gongguhan.domain.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReturnCreateRequest(
        @NotBlank String reasonType,
        @Size(max = 500) String detailReason,
        @NotBlank @Pattern(regexp = "\\d{5}") String pickupZipcode,
        @NotBlank @Size(max = 255) String pickupAddress1,
        @Size(max = 255) String pickupAddress2
) {
}
