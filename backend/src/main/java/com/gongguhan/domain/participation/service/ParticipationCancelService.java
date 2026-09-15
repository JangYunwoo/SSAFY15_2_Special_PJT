package com.gongguhan.domain.participation.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAR-03 모집 완료 전 참여 취소·환불 개시 (FR-JOI-12~13, FR-RFD-02).
 *
 * <p>참여(카드 도메인)의 상태 전이·슬롯 되돌림만 여기서 하고, 실제 환불 실행은 refund
 * 도메인의 {@link RefundOrchestrationService#refundParticipation}에 위임한다(그 서비스는
 * 원장 개설과 외부 송금/취소를 스스로 관리한다). 외부 호출을 트랜잭션 안에서 붙들지
 * 않도록 DB 작업만 별도 트랜잭션({@link #prepareCancel})으로 처리하고, 환불은 그 밖에서 부른다.
 */
@Service
public class ParticipationCancelService {

    private final ParticipationRepository participationRepository;
    private final PaymentRepository paymentRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final GroupBuySlotGate groupBuySlotGate;
    private final RefundOrchestrationService refundOrchestrationService;
    /** prepareCancel의 @Transactional을 프록시로 태우기 위한 self 참조(AuthService와 같은 패턴). */
    private final ObjectProvider<ParticipationCancelService> self;

    public ParticipationCancelService(ParticipationRepository participationRepository,
                                      PaymentRepository paymentRepository,
                                      GroupBuyRepository groupBuyRepository,
                                      GroupBuySlotGate groupBuySlotGate,
                                      RefundOrchestrationService refundOrchestrationService,
                                      ObjectProvider<ParticipationCancelService> self) {
        this.participationRepository = participationRepository;
        this.paymentRepository = paymentRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.groupBuySlotGate = groupBuySlotGate;
        this.refundOrchestrationService = refundOrchestrationService;
        this.self = self;
    }

    public void cancel(Long userId, Long participationId) {
        long amount = self.getObject().prepareCancel(userId, participationId);
        // 외부 환불은 트랜잭션 밖에서. 실패해도 원장이 FAILED로 남아 재처리(RFD-04) 대상이 된다.
        refundOrchestrationService.refundParticipation(
                participationId, amount, RefundReason.PARTICIPATION_CANCEL);
    }

    /**
     * 취소 가능 여부를 검증하고 참여·정원·슬롯을 되돌린 뒤 환불 금액을 반환한다.
     * PAID → REFUNDING 전이가 중복 취소를 막는 게이트 역할도 한다.
     */
    @Transactional
    public long prepareCancel(Long userId, Long participationId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여를 찾을 수 없습니다."));
        if (!participation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        if (!Participation.STATUS_PAID.equals(participation.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "결제 완료(PAID) 상태의 참여만 취소할 수 있습니다.");
        }

        Payment payment = paymentRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
        GroupBuy groupBuy = groupBuyRepository.findById(participation.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));

        groupBuy.cancelConfirmedParticipant();               // 모집 완료 전만, 정원 되돌림
        participation.markRefunding();                        // PAID → REFUNDING
        groupBuySlotGate.release(participation.getGroupBuyId()); // Redis 슬롯 게이트 미러 되돌림
        return payment.getKrwAmount();
    }
}
