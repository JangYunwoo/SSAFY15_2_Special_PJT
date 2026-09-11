package com.gongguhan.domain.participation.adapter;

import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import org.springframework.stereotype.Component;

/**
 * {@link ParticipationWriterPort}의 participation 도메인 구현체 (S15P21B205-105).
 *
 * <p>{@link com.gongguhan.domain.groupbuy.adapter.GroupBuySlotAdapter}와 같은
 * 얇은 위임 패턴 — 규칙 자체는 {@link Participation} 엔티티와
 * {@link ParticipationRepository}에 이미 있고, 여기서는 포트 계약 모양으로
 * 옮겨 담기만 한다.
 */
@Component
public class ParticipationWriterAdapter implements ParticipationWriterPort {

    private final ParticipationRepository participationRepository;

    public ParticipationWriterAdapter(ParticipationRepository participationRepository) {
        this.participationRepository = participationRepository;
    }

    @Override
    public boolean existsActiveParticipation(Long groupBuyId, Long userId) {
        return participationRepository.existsByGroupBuyIdAndUserIdAndActiveMarker(
                groupBuyId, userId, Participation.ACTIVE_MARKER);
    }

    @Override
    public ParticipationRecord create(ParticipationCreateCommand command) {
        Participation participation = Participation.create(
                command.groupBuyId(), command.userId(),
                command.shareAmount(), command.shippingFee(), command.totalAmount(),
                command.recipientName(), command.recipientPhone(), command.zipcode(),
                command.address1(), command.address2());
        participationRepository.saveAndFlush(participation);
        return new ParticipationRecord(
                participation.getParticipationId(), participation.getGroupBuyId(), participation.getUserId());
    }
}
