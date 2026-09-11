package com.gongguhan.domain.payment.crypto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * CRYP-03 트랜잭션 해시 등록 요청.
 *
 * <p>{@code quoteId}를 받는 이유: {@code crypto_quotes}에 {@code payment_id} 컬럼이
 * 없어 어느 결제의 견적인지 DB에서 역추적할 수 없다. 서버는 이 견적의 원화 금액이
 * 결제 건의 금액과 같은지 대조하고, {@code used} 플래그를 원자적으로 claim 해서
 * 중복 사용을 막는다.
 *
 * <p>형식을 정규식으로 걸러내는 이유: 잘못된 해시를 그대로 노드에 보내면 RPC 오류가
 * 나는데, 사용자에게는 "체인 연동 실패"로 보여 원인 파악이 어렵다. 400으로 되돌린다.
 */
public record CryptoTxSubmitRequest(

        @NotNull(message = "quoteId는 필수입니다.")
        Long quoteId,

        @NotBlank(message = "txHash는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$",
                message = "txHash 형식이 올바르지 않습니다. 0x + 64자리 16진수여야 합니다.")
        String txHash,

        @NotBlank(message = "fromAddress는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{40}$",
                message = "지갑 주소 형식이 올바르지 않습니다. 0x + 40자리 16진수여야 합니다.")
        String fromAddress
) {
}
