package com.gongguhan.domain.payment.card.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 카드 인증정보. 어디에도 저장·로깅하지 않는다 - {@code CardPaymentService}를
 * 벗어나면 마스킹된 카드번호만 남는다.
 */
public record CardApproveRequest(
        @NotBlank String cardNumber,
        @NotBlank String cvc
) {
}
