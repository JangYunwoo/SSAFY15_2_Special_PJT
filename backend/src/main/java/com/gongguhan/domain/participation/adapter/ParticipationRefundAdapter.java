package com.gongguhan.domain.participation.adapter;

import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.refund.port.ParticipationRefundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ParticipationRefundPort}의 participation 도메인 구현체.
 *
 * <p>{@link ParticipationWriterAdapter}와 같은 얇은 위임 패턴 — 상태 전이 규칙은
 * {@link Participation} 엔티티에 있고 여기서는 대상 여부만 가린다.
 *
 * <p><b>짧은 트랜잭션이다.</b> 호출 측({@code RefundOrchestrationService})은 외부 송금을
 * 트랜잭션 밖에서 하므로, 기록만 여기서 묶는다.
 */
@Component
public class ParticipationRefundAdapter implements ParticipationRefundPort {

    private static final Logger log = LoggerFactory.getLogger(ParticipationRefundAdapter.class);

    private final ParticipationRepository participationRepository;

    public ParticipationRefundAdapter(ParticipationRepository participationRepository) {
        this.participationRepository = participationRepository;
    }

    @Override
    @Transactional
    public void finishRefundingParticipation(Long participationId) {
        Participation participation = participationRepository.findById(participationId).orElse(null);
        if (participation == null) {
            log.error("환불은 끝났는데 참여 건을 찾을 수 없습니다. participationId={}", participationId);
            return;
        }
        if (!Participation.STATUS_REFUNDING.equals(participation.getStatus())) {
            // 반품 환불(RETURNING) 등 다른 경로다. 그쪽 상태 기계를 건드리지 않는다.
            return;
        }
        participation.markRefunded();
        log.info("참여 환불 종료 participationId={} REFUNDING -> REFUNDED", participationId);
    }
}
