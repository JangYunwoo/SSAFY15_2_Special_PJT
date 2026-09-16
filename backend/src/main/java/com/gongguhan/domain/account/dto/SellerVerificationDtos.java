package com.gongguhan.domain.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 판매자 전환(SEL-03~05) 요청·응답 DTO 모음.
 *
 * <p>정산계좌 1원 인증(SEL-03·04)은 실제 SSAFY 금융망을 호출하며, 인증코드도 1원 입금
 * 적요에서 자동으로 읽어 응답에 담는다. 휴대폰 인증은 SSAFY에 API가 없고 은행 업무도
 * 아니라 제거했다.
 */
public final class SellerVerificationDtos {

    private SellerVerificationDtos() {
    }

    /** SEL-03 정산계좌 1원 인증 요청 응답. 개설/사용된 계좌번호와 자동 조회한 인증코드. */
    public record AccountRequestResponse(String accountNo, String devAuthCode, String message) {
    }

    /** SEL-04 정산계좌 1원 인증 확인. */
    public record AccountConfirmRequest(@NotBlank String accountNo, @NotBlank String authCode) {
    }

    /** SEL-05 판매자 전환. 인증(휴대폰·계좌)을 모두 통과한 뒤 호출한다. */
    public record SellerProfileCreateRequest(
            @NotBlank @Size(max = 100) String businessName,
            String businessPhone,
            @NotBlank @Size(max = 50) String accountHolder,
            @Valid @NotNull ReturnAddress returnAddress
    ) {

        public record ReturnAddress(
                @NotBlank @Size(min = 5, max = 5) String zipcode,
                @NotBlank String address1,
                String address2
        ) {
        }
    }
}
