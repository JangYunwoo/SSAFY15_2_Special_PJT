package com.gongguhan.domain.penalty.service;

import com.gongguhan.domain.groupbuy.event.GroupBuyWithdrawnEvent;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 필수 판매 철회 후보를 공구 취소와 함께 커밋한다. 후보는 자동 확정/판매 정지가 아니다. */
@Component
public class WithdrawalPenaltyListener {
    private final GroupBuyRepository groupBuys;
    private final SellerPenaltyRepository penalties;

    public WithdrawalPenaltyListener(GroupBuyRepository groupBuys, SellerPenaltyRepository penalties) {
        this.groupBuys = groupBuys;
        this.penalties = penalties;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onWithdrawn(GroupBuyWithdrawnEvent event) {
        var groupBuy = groupBuys.findWithLockByGroupBuyId(event.groupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!groupBuy.getSellerId().equals(event.sellerId()) || !"CANCELED".equals(groupBuy.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "취소된 판매자 공구만 철회 후보로 기록할 수 있습니다.");
        }
        // 같은 공구 행 잠금 안에서 조회와 저장을 직렬화해 이벤트 중복 발행도 한 건으로 유지한다.
        if (penalties.existsByGroupBuyIdAndReasonType(event.groupBuyId(), SellerPenalty.REASON_WITHDRAWAL)) return;
        String detail = "판매자 판매 철회: " + (event.reason() == null ? "" : event.reason().trim());
        penalties.save(SellerPenalty.candidate(event.sellerId(), event.groupBuyId(),
                SellerPenalty.REASON_WITHDRAWAL, detail.length() <= 500 ? detail : detail.substring(0, 500),
                event.withdrawnAt()));
    }
}
