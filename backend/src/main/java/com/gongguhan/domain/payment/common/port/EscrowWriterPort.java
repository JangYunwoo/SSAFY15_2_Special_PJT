package com.gongguhan.domain.payment.common.port;

import java.time.LocalDateTime;

/**
 * 결제 공통 계층이 에스크로를 보관 상태로 만들기 위한 경계 계약.
 *
 * <p>결제 공통은 이 계약에만 의존하며 에스크로 엔티티나 저장소를 직접 참조하지 않는다.
 * {@link GroupBuySlotPort}와 같은 이유로 존재한다.
 *
 * <p><b>구현체(어댑터)는 S15P21B205-106에서 만든다.</b> 이 인터페이스만으로는
 * Spring이 빈을 찾지 못해 애플리케이션이 기동되지 않는다 — 계약 확정(S15P21B205-103)이
 * 어댑터 구현보다 먼저 병합되는 팀 프로세스에 따른 의도된 상태다.
 */
public interface EscrowWriterPort {

    /** 결제 완료 즉시 참여 건의 결제금액을 보관 상태로 만든다. */
    void hold(Long participationId, Long groupBuyId, long amount, LocalDateTime heldAt);
}
