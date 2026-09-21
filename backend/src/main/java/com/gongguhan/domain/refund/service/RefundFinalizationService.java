package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.escrow.service.EscrowService;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
 * <p>반품 환불은 finalizeReturnRefund에서 부분 환불 누계와 반품 이력을 함께 종결한다.
 * 0원 반품은 자금 이동 없이 종결하며 배송비 잔액은 별도 정산 정책이 검증한다.
 * returnId가 없는 전액 참여 환불은 finalizeParticipationRefund를 사용한다.
 */
@Service
public class RefundFinalizationService {

    private final EscrowService escrowService;
    private final ParticipationRepository participationRepository;
    private final ReturnRequestRepository returns;
    private final Clock clock;

    public RefundFinalizationService(EscrowService escrowService,
                                     ParticipationRepository participationRepository,
                                     ReturnRequestRepository returns, Clock clock) {
        this.escrowService = escrowService;
        this.participationRepository = participationRepository;
        this.returns = returns;
        this.clock = clock;
    }

    /** 반품 상태 선점을 이용해 부분환불 누계를 정확히 한 번 반영한다. 실패하면 모두 롤백한다. */
    @Transactional
    public void finalizeReturnRefund(Refund refund) {
        if (!refund.isCompleted() || refund.getReturnId() == null) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        LocalDateTime completedAt = LocalDateTime.now(clock);
        if (returns.markRefunded(refund.getReturnId(), completedAt)) {
            escrowService.refundReturn(refund.getParticipationId(), refund.getAmount(), "반품 환불 완료");
            returns.addHistory(refund.getReturnId(), "REFUNDED", "환불 완료: REFUND-" + refund.getRefundId(), completedAt);
        }
    }

    /**
     * 참여 환불 완료를 도메인에 반영한다.
     *
     * <p>에스크로가 {@code HELD} 가 아니면(이미 환불·정산됨) {@link EscrowService#refund} 가
     * 거부한다. 그 판단은 호출 측이 아니라 여기서 예외로 드러내, 이중 종결을 조용히 넘기지 않는다.
     */
    @Transactional
    public void finalizeParticipationRefund(Long participationId, String reason) {
        Participation participation = participationRepository.findWithLockByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "참여를 찾을 수 없습니다. participationId=" + participationId));
        if (Participation.STATUS_REFUNDED.equals(participation.getStatus())) { return; }
        escrowService.refund(participationId, reason);        // HELD → REFUNDED
        participation.markRefunded();                          // → REFUNDED
    }
}
