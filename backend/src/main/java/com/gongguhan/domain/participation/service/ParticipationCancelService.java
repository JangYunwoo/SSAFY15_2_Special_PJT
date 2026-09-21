package com.gongguhan.domain.participation.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.service.RefundLedgerService.OpenResult;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PAR-03 모집 완료 전 참여 취소·환불 개시 (FR-JOI-12~13, FR-RFD-02).
 *
 * <p>참여 상태·정원·환불 의도를 prepareCancel의 한 트랜잭션으로 확정한다.
 * 실제 외부 취소는 커밋 후 RefundOrchestrationService에 위임한다.
 * 입력 오류나 원장 저장 실패가 참여만 REFUNDING으로 남기지 않도록 경계를 맞춘다.
 */
@Service
public class ParticipationCancelService {

    private static final Logger log = LoggerFactory.getLogger(ParticipationCancelService.class);

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

    /**
     * 참여를 취소하고 환불을 개시한다.
     *
     * <p>카드 결제 건은 화면에서 받은 카드번호·CVC 로 <b>카드 승인 취소</b>를 시도하고,
     * 취소가 불가능한 경우(부분 환불·청구서 발행)에만 계좌이체로 폴백한다. 카드 정보가
     * 없거나 형식이 틀리면 상태 변경 전에 거부한다. 다른 결제수단은 제공사 경로로 간다.
     *
     * @param cardNumber 카드 결제 취소용 카드번호(선택). 카드 결제일 때만 의미가 있다.
     * @param cvc 카드 CVC(선택). 카드번호와 함께 넘어와야 한다.
     */
    public void cancel(Long userId, Long participationId, String cardNumber, String cvc) {
        CancelPrepared prepared = self.getObject().prepareCancel(userId, participationId, cardNumber, cvc);
        // 참여 상태·정원·환불 의도가 함께 커밋된 이후에만 외부 환불을 실행한다.
        refundOrchestrationService.executeParticipationCancellation(
                participationId, prepared.refund(), cardNumber, cvc);
    }

    /** 취소 준비 결과. 환불 경로를 정하려면 결제수단이 필요하다. */
    public record CancelPrepared(long amount, String paymentMethod, OpenResult refund) {
    }

    @Transactional
    public CancelPrepared prepareCancel(Long userId, Long participationId) {
        return prepareCancel(userId, participationId, null, null);
    }

    /**
     * 취소 가능 여부를 검증하고 참여·정원·슬롯을 되돌린 뒤 환불 금액·결제수단을 반환한다.
     * PAID → REFUNDING 전이가 중복 취소를 막는 게이트 역할도 한다.
     */
    @Transactional
    public CancelPrepared prepareCancel(Long userId, Long participationId, String cardNumber, String cvc) {
        Participation participation = participationRepository.findWithLockByParticipationId(participationId)
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
        if (Payment.METHOD_CARD.equals(payment.getMethod())
                && (cardNumber == null || !cardNumber.matches("[0-9]{16}")
                    || cvc == null || !cvc.matches("[0-9]{3}"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "카드 취소에는 숫자 16자리 카드번호와 숫자 3자리 CVC가 필요합니다.");
        }
        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(participation.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));

        groupBuy.cancelConfirmedParticipant();               // 모집 완료 전만, 정원 되돌림
        participation.markRefunding();                        // PAID → REFUNDING
        OpenResult refund = refundOrchestrationService.reserveParticipationCancellation(
                participationId, payment.getKrwAmount(), payment.getMethod());
        if (!refund.created()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 환불 원장이 존재하는 참여입니다.");
        }
        Long groupBuyId = participation.getGroupBuyId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    groupBuySlotGate.release(groupBuyId);
                } catch (RuntimeException e) {
                    // DB 커밋 후 캐시 장애가 이미 예약된 환불 실행까지 막으면 안 된다.
                    log.error("취소 커밋 후 슬롯 게이트 동기화 실패 groupBuyId={} (재대조 필요)", groupBuyId);
                }
            }
        });
        return new CancelPrepared(payment.getKrwAmount(), payment.getMethod(), refund);
    }
}
