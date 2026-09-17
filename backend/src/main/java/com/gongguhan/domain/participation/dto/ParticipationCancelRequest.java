package com.gongguhan.domain.participation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PAR-03 참여 취소 요청 (선택 바디).
 *
 * <p>카드 결제 건은 SSAFY 카드 승인 취소({@code deleteTransaction})가 카드번호·CVC 를
 * 다시 요구하므로 화면에서 받아 전달한다. 카드 원문은 저장하지 않고 취소 호출에만 쓴다.
 * 다른 결제수단(카카오페이·코인)이나 카드가 아닌 경우 바디 없이 호출해도 된다.
 */
public record ParticipationCancelRequest(
        @Pattern(regexp = "\\d{15,16}", message = "카드번호는 숫자 15~16자리여야 합니다.")
        String cardNumber,
        @Pattern(regexp = "\\d{3}", message = "CVC는 숫자 3자리여야 합니다.")
        @Size(max = 3)
        String cvc
) {
}
