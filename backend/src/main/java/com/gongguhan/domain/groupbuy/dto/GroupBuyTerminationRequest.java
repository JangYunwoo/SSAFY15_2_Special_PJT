package com.gongguhan.domain.groupbuy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** GRP-08·10 종료 사유. 취소와 판매자 귀책 철회 모두 빈 사유를 허용하지 않는다. */
public record GroupBuyTerminationRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
