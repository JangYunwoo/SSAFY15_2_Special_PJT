package com.gongguhan.domain.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 사용 가능 여부 확인·예약 요청(C안).
 *
 * <p>가입 전 확인이 SSAFY 회원 생성(쓰기)을 유발할 수 있으므로 조회가 아니라 POST 바디로
 * 받는다. 검증 수준은 {@link SignupRequest#email()}과 동일하게 맞춘다(과하게 조이면
 * 가입은 되는데 확인만 막히는 불일치가 생긴다).
 */
public record EmailAvailabilityRequest(
        @Email @NotBlank String email
) {
}
