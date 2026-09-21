package com.gongguhan.domain.payment.kakaopay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 승인과 동일하게 취소·실패 복귀도 서버가 발급한 state를 제출한다. */
public record KakaoPayFlowRequest(@NotBlank @Size(max = 512) String state) {
}
