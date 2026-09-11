package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SEL-07 연락처·정산계좌·반품주소 부분 수정. null 필드는 유지된다. */
public record SellerProfileUpdateRequest(
        @Size(max = 20) String businessPhone,
        @Pattern(regexp = "\\d{10,16}", message = "계좌번호는 10~16자리 숫자여야 합니다.") String ssafyAccountNo,
        @Size(max = 50) String accountHolder,
        @Pattern(regexp = "\\d{5}", message = "우편번호는 5자리 숫자여야 합니다.") String returnZipcode,
        @Size(max = 255) String returnAddress1,
        @Size(max = 255) String returnAddress2
) {
}
