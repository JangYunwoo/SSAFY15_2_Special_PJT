package com.gongguhan.domain.returnrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReturnDisputeRequest(@NotBlank @Size(max = 500) String detailReason) {
}
