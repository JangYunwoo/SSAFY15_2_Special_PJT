package com.gongguhan.domain.payment.common.port;

import java.time.LocalDateTime;

/**
 * 결제 공통 계층이 공동구매 슬롯을 조회하고 변경하기 위한 경계 계약.
 *
 * <p>결제 공통은 이 계약에만 의존하며 공동구매 엔티티나 저장소를 직접 참조하지 않는다.
 */
public interface GroupBuySlotPort {

    GroupBuySlot get(Long groupBuyId);

    GroupBuySlot reserve(Long groupBuyId);

    GroupBuySlot release(Long groupBuyId);

    GroupBuySlot confirm(Long groupBuyId);

    record GroupBuySlot(
            Long groupBuyId,
            long unitShareAmount,
            long shippingFee,
            int targetParticipants,
            int currentParticipants,
            int reservedSlots,
            LocalDateTime recruitEndAt,
            String status
    ) {
        public long totalAmount() {
            return Math.addExact(unitShareAmount, shippingFee);
        }
    }
}
