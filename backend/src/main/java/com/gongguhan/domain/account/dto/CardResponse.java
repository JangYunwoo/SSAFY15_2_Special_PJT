package com.gongguhan.domain.account.dto;

import com.gongguhan.domain.account.service.SsafyCardIssueClient;

/**
 * 마이페이지 카드 조회·발급 응답.
 *
 * <p>{@code hasCard=false}면 나머지는 모두 null이고 프론트는 "카드 발급" 버튼을 보여준다.
 * 카드번호·CVC는 SSAFY 교육망에서 온디맨드로 조회한 값이며 우리 DB엔 저장하지 않는다
 * (결제 시 사용자가 입력할 수 있도록 전체 값을 내려준다 - 실서비스라면 마스킹·보안 검토 필요).
 */
public record CardResponse(
        boolean hasCard,
        String cardNo,
        String cvc,
        String cardName,
        String cardIssuerName,
        String cardExpiryDate
) {

    public static CardResponse none() {
        return new CardResponse(false, null, null, null, null, null);
    }

    public static CardResponse of(SsafyCardIssueClient.Card card) {
        return new CardResponse(true, card.cardNo(), card.cvc(), card.cardName(),
                card.cardIssuerName(), card.cardExpiryDate());
    }
}
