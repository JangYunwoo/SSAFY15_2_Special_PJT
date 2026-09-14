package com.gongguhan.domain.penalty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PEN-04~06 위반 검토 요청.
 *
 * <p><b>판단 근거를 필수로 받습니다.</b> 제재는 판매자의 영업을 막는 처분이라,
 * 왜 그렇게 판단했는지 남지 않으면 판매자가 이의를 제기할 수 없고 관리자끼리도
 * 판단 기준이 갈립니다. 취소(PEN-06)도 마찬가지로 이유가 필요합니다 — 왜 되돌렸는지
 * 없으면 같은 실수가 반복됩니다.
 *
 * @param memo 판단 근거. {@code seller_penalties.memo} 가 VARCHAR(500) 입니다
 */
public record PenaltyReviewRequest(
        @NotBlank(message = "판단 근거는 필수입니다.")
        @Size(max = 500, message = "판단 근거는 500자 이하여야 합니다.")
        String memo
) {
}
