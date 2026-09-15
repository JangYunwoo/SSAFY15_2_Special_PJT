package com.gongguhan.domain.groupbuy.event;

import java.time.LocalDateTime;

/**
 * 판매자 귀책 판매 철회 발생(GRP-10). 공동구매 취소·전원 환불은 groupbuy 도메인이 처리하고,
 * <b>판매자 위반 후보 생성</b>은 penalty 도메인(코인 담당)이 이 이벤트를 소비해 잇는다
 * (도메인 경계를 지키기 위한 seam - 여기서 penalty 도메인을 직접 건드리지 않는다).
 */
public record GroupBuyWithdrawnEvent(
        Long groupBuyId,
        Long sellerId,
        int refundedParticipants,
        String reason,
        LocalDateTime withdrawnAt
) {
}
