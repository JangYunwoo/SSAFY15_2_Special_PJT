package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** USR-04 배송지 등록 요청 (FE AddressInput). */
public record AddressCreateRequest(
        @NotBlank @Size(max = 50) String recipientName,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = AccountInputPatterns.PHONE_DIGITS, message = AccountInputPatterns.PHONE_MESSAGE) String recipientPhone,
        @NotBlank @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") String zipcode,
        @NotBlank @Size(max = 255) String address1,
        @Size(max = 255) String address2,
        @Size(max = 100) String deliveryRequest,
        Boolean isDefault
) {
}
