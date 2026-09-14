package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** USR-05 배송지 부분 수정 요청. null 필드는 유지된다. */
public record AddressUpdateRequest(
        @Size(max = 50) String recipientName,
        @Size(max = 20) String recipientPhone,
        @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") String zipcode,
        @Size(max = 255) String address1,
        @Size(max = 255) String address2,
        @Size(max = 100) String deliveryRequest,
        Boolean isDefault
) {
}
