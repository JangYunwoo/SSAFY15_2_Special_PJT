package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** USR-02 사용자 프로필 부분 수정. ssafyAccountNo는 카드 취소 불가 시 환불 수취 계좌다. */
public record UserUpdateRequest(
        @Size(max = 50)
        @Pattern(regexp = AccountInputPatterns.NON_BLANK, message = "닉네임은 공백일 수 없습니다.") String nickname,
        @Size(max = 20)
        @Pattern(regexp = AccountInputPatterns.PHONE_DIGITS, message = AccountInputPatterns.PHONE_MESSAGE) String phone,
        @Pattern(regexp = "\\d{10,16}", message = "계좌번호는 10~16자리 숫자여야 합니다.") String ssafyAccountNo
) {
}
