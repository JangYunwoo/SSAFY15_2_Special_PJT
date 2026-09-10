package com.gongguhan.domain.product.dto;

import com.gongguhan.domain.product.entity.RestrictionReviewStatus;
import jakarta.validation.constraints.NotNull;

public record WithdrawalRestrictionReviewRequest(@NotNull RestrictionReviewStatus status) {
}
