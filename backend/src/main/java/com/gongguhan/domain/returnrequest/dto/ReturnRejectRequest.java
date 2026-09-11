package com.gongguhan.domain.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnRejectRequest(@NotBlank @Size(max = 255) String reason) {
}
