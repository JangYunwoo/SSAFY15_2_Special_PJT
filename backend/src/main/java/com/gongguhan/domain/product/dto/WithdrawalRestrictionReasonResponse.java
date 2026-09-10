package com.gongguhan.domain.product.dto;

import com.gongguhan.domain.product.entity.WithdrawalRestrictionReason;

public record WithdrawalRestrictionReasonResponse(
        String id,
        String code,
        String name,
        String description,
        boolean requiresEvidence) {

    public static WithdrawalRestrictionReasonResponse from(WithdrawalRestrictionReason reason) {
        return new WithdrawalRestrictionReasonResponse(
                String.valueOf(reason.getReasonId()),
                reason.getCode(),
                reason.getName(),
                reason.getDescription(),
                reason.isRequiresEvidence());
    }
}
