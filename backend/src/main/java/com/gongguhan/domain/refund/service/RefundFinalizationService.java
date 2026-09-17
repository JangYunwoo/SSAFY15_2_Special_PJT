package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.escrow.service.EscrowService;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 건 환불이 완료된 뒤의 마무리 처리 (FR-ESC-05, FR-RFD).
 *
 * <p>환불 <b>송금</b>(카드 취소·계좌 이체)은 {@link RefundOrchestrationService} 가 이미 끝냈고,
 * 여기서는 그 결과를 도메인 상태에 반영한다 — 에스크로를 {@code REFUNDED} 로 닫고 참여를
 * {@code REFUNDED} 로 종결한다. 둘을 한 트랜잭션으로 묶어, 에스크로만 닫히고 참여는 남는
 * 어긋난 상태가 생기지 않게 한다.
 *
 * <p>반품 환불은 반품 도메인이 스스로 종결하므로(부분 환불·정산 잔액 처리가 달라) 이 경로로
 * 오지 않는다. 호출 측이 {@code returnId} 가 없는 참여 환불에 대해서만 부른다.
 */
@Service
public class RefundFinalizationService {

    private final EscrowService escrowService;
    private final ParticipationRepository participationRepository;

    public RefundFinalizationService(EscrowService escrowService,
                                     ParticipationRepository participationRepository) {
        this.escrowService = escrowService;
        this.participationRepository = participationRepository;
    }

    /**
     * 참여 환불 완료를 도메인에 반영한다.
     *
     * <p>에스크로가 {@code HELD} 가 아니면(이미 환불·정산됨) {@link EscrowService#refund} 가
     * 거부한다. 그 판단은 호출 측이 아니라 여기서 예외로 드러내, 이중 종결을 조용히 넘기지 않는다.
     */
    @Transactional
    public void finalizeParticipationRefund(Long participationId, String reason) {
        escrowService.refund(participationId, reason);        // HELD → REFUNDED

        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "참여를 찾을 수 없습니다. participationId=" + participationId));
        participation.markRefunded();                          // → REFUNDED
    }
}
