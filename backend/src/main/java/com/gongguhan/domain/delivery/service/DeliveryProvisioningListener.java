package com.gongguhan.domain.delivery.service;

import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.event.GroupBuyFulfilledEvent;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 모집 완료 시 참여를 FULFILLED로 전환하고 참여자별 배송 행(READY)을 만든다.
 *
 * <p><b>BEFORE_COMMIT인 이유</b>: 마지막 참여자의 결제 완료 경로에서는
 * {@code PaymentService.completePayment}가 슬롯 확정({@code GroupBuySlotPort.confirm} →
 * 모집 판정 → 이 이벤트 발행)을 참여 행 생성보다 <b>먼저</b> 수행한다. 발행 즉시 실행되는
 * 일반 {@code @EventListener}였다면 마지막 참여자의 participations 행이 아직 없어 배송
 * 행이 누락된다. 커밋 직전으로 미루면 같은 트랜잭션 안에서 모든 참여 행이 존재하는
 * 시점에 실행되고, 원자성도 유지된다(리스너 실패 시 판정까지 함께 롤백).
 */
@Component
public class DeliveryProvisioningListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryProvisioningListener.class);

    private final ParticipationRepository participationRepository;
    private final DeliveryRepository deliveryRepository;

    public DeliveryProvisioningListener(ParticipationRepository participationRepository,
                                         DeliveryRepository deliveryRepository) {
        this.participationRepository = participationRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onFulfilled(GroupBuyFulfilledEvent event) {
        List<Participation> paidParticipations = participationRepository
                .findByGroupBuyIdAndStatus(event.groupBuyId(), Participation.STATUS_PAID);
        for (Participation participation : paidParticipations) {
            participation.markFulfilled();
            deliveryRepository.save(Delivery.ready(participation.getParticipationId()));
        }
        log.info("모집 완료 배송 준비: groupBuyId={} 참여 {}건 FULFILLED 전환·배송 행 생성",
                event.groupBuyId(), paidParticipations.size());
    }
}
