package com.gongguhan.domain.payment.common.port;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * 결제 공통 계층이 공동구매 슬롯을 조회하고 변경하기 위한 경계 계약.
 *
 * <p>결제 공통은 이 계약에만 의존하며 공동구매 엔티티나 저장소를 직접 참조하지 않는다.
 */
public interface GroupBuySlotPort {

    GroupBuySlot get(Long groupBuyId);

    /** 목록 응답 조립 시 공동구매를 한 번에 읽어 결제 건별 N+1 조회를 피한다. */
    Map<Long, GroupBuySlot> getAll(Collection<Long> groupBuyIds);

    GroupBuySlot reserve(Long groupBuyId);

    GroupBuySlot release(Long groupBuyId);

    GroupBuySlot confirm(Long groupBuyId);

    /**
     * 완료 경계 전용 확정. 공동구매 행에 락을 잡되, 모집 중이 아니면 예외 대신
     * {@code notRecruiting} 결과를 돌려준다. 외부 결제 승인이 이미 성공한 뒤 공구가 종료된
     * 경합에서, 호출 트랜잭션을 rollback-only로 오염시키지 않고 같은 트랜잭션에서 환불 예약을
     * durable하게 기록할 수 있게 한다. 모집 중이면 {@link #confirm}과 동일하게 확정한다.
     */
    ConfirmOutcome confirmForCompletion(Long groupBuyId);

    record ConfirmOutcome(boolean confirmed, GroupBuySlot slot) {
        public static ConfirmOutcome confirmed(GroupBuySlot slot) {
            return new ConfirmOutcome(true, slot);
        }

        public static ConfirmOutcome notRecruiting(GroupBuySlot slot) {
            return new ConfirmOutcome(false, slot);
        }

        public boolean notRecruiting() {
            return !confirmed;
        }
    }

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
