package com.gongguhan.domain.payment.common.port;

import java.time.LocalDateTime;

/**
 * 결제 공통 계층이 에스크로를 보관 상태로 만들기 위한 경계 계약.
 *
 * <p>결제 공통은 이 계약에만 의존하며 에스크로 엔티티나 저장소를 직접 참조하지 않는다.
 * {@link GroupBuySlotPort}와 같은 이유로 존재한다.
 *
 * <p>운영 구현은 escrow 패키지의 {@code EscrowWriterAdapter}에 둔다. 결제 패키지는
 * 에스크로 엔티티와 저장소를 알지 않고, 어댑터가 이 계약을 JPA 저장으로 변환한다.
 */
public interface EscrowWriterPort {

    /** 결제 완료 즉시 참여 건의 결제금액을 보관 상태로 만든다. */
    void hold(Long participationId, Long groupBuyId, long amount, LocalDateTime heldAt);
}
