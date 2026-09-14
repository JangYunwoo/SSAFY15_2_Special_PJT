package com.gongguhan.domain.participation.dto;

import jakarta.validation.constraints.NotNull;

/** CFM-01 수동 구매확정 요청. 반품 제한 고지 동의(FR-CFM) 없이는 확정할 수 없다. */
public record PurchaseConfirmRequest(
        @NotNull Boolean noticeAgreed
) {
}
