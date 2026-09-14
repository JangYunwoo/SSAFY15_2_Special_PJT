package com.gongguhan.domain.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * STL-05 정산 보류 요청 (FR-STL-13).
 *
 * <p><b>사유를 필수로 받습니다.</b> 보류는 판매자가 받을 돈을 막는 처분이라, 왜
 * 막혔는지 남지 않으면 판매자에게 설명할 수 없고 나중에 누가 왜 풀어야 하는지도
 * 알 수 없습니다.
 *
 * @param reason 보류 사유. {@code settlements.transfer_fail_reason} 이 VARCHAR(255)
 */
public record SettlementHoldRequest(
        @NotBlank(message = "보류 사유는 필수입니다.")
        @Size(max = 255, message = "보류 사유는 255자 이하여야 합니다.")
        String reason
) {
}
