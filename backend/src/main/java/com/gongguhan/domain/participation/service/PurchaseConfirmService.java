package com.gongguhan.domain.participation.service;

import com.gongguhan.domain.participation.dto.PurchaseConfirmResponse;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매확정 (CFM-01 수동, 배치 자동). 에스크로 해제는 여기서 하지 않는다 -
 * {@link com.gongguhan.domain.escrow.entity.Escrow#release}의 계약대로 정산
 * 유스케이스가 구매확정 여부를 근거로 해제한다(FR-ESC-02·03).
 */
@Service
@Transactional(readOnly = true)
public class PurchaseConfirmService {

    private final ParticipationRepository participationRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public PurchaseConfirmService(ParticipationRepository participationRepository,
                                   PolicyService policyService,
                                   Clock clock) {
        this.participationRepository = participationRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    /** CFM-01: 배송완료 건 수동 구매확정. */
    @Transactional
    public PurchaseConfirmResponse confirmManually(Long userId, Long participationId, Boolean noticeAgreed) {
        if (!Boolean.TRUE.equals(noticeAgreed)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "구매확정 후 반품 제한 고지에 동의해야 확정할 수 있습니다.");
        }
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여 정보를 찾을 수 없습니다."));
        if (!participation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        participation.confirmManually(LocalDateTime.now(clock));
        return PurchaseConfirmResponse.from(participation);
    }

    /**
     * 자동 확정 후보 조회 (매시 배치). 배송완료 + AUTO_CONFIRM_DAYS가 지났고 반품
     * 보류가 아닌 참여의 ID만 뽑는다. 실제 확정은 건별 {@link #autoConfirmIfDue}에서
     * 재검증 후 처리한다 - 조회와 처리 사이에 수동 확정·반품 신청이 끼어들 수 있다.
     */
    public List<Long> findAutoConfirmCandidateIds() {
        LocalDateTime threshold = LocalDateTime.now(clock)
                .minusDays(policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS));
        return participationRepository
                .findByStatusAndConfirmHoldFalseAndDeliveredAtBefore(Participation.STATUS_DELIVERED, threshold)
                .stream()
                .map(Participation::getParticipationId)
                .toList();
    }

    /** 후보 재검증 후 자동 확정. 조건이 바뀌었으면 조용히 넘어간다(다음 주기·수동 경로가 처리). */
    @Transactional
    public void autoConfirmIfDue(Long participationId) {
        Participation participation = participationRepository.findById(participationId).orElse(null);
        if (participation == null
                || !Participation.STATUS_DELIVERED.equals(participation.getStatus())
                || participation.isConfirmHold()) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now(clock)
                .minusDays(policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS));
        if (participation.getDeliveredAt() == null || !participation.getDeliveredAt().isBefore(threshold)) {
            return;
        }
        participation.confirmAutomatically(LocalDateTime.now(clock));
    }
}
