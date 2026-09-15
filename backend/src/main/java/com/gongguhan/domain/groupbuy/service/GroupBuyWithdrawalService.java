package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.event.GroupBuyWithdrawnEvent;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * GRP-10 참여 발생 후 판매자 귀책 판매 철회 (FR-PEN-01~02, 07).
 *
 * <p>스펙의 "공동구매 취소 · 전원 환불 개시 · 판매자 위반 후보 생성"을 한 흐름으로 실행하되,
 * 도메인 경계를 지킨다:
 * <ul>
 *   <li>공동구매 취소 + 참여 상태 전이 - groupbuy/participation(직접)</li>
 *   <li>전원 환불 - refund 도메인의 {@link RefundOrchestrationService#refundParticipation}(호출)</li>
 *   <li>위반 후보 생성 - {@link GroupBuyWithdrawnEvent}를 발행해 penalty 도메인(코인)이 소비(seam)</li>
 * </ul>
 *
 * <p>외부 환불 호출은 트랜잭션 밖에서 반복한다 - DB 상태 전이만 {@link #prepareWithdrawal}로
 * 묶고, 환불은 그 뒤에 건별로 부른다(원자성 규칙).
 */
@Service
public class GroupBuyWithdrawalService {

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundOrchestrationService refundOrchestrationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<GroupBuyWithdrawalService> self;

    public GroupBuyWithdrawalService(GroupBuyRepository groupBuyRepository,
                                     ParticipationRepository participationRepository,
                                     PaymentRepository paymentRepository,
                                     RefundOrchestrationService refundOrchestrationService,
                                     ApplicationEventPublisher eventPublisher,
                                     ObjectProvider<GroupBuyWithdrawalService> self) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.paymentRepository = paymentRepository;
        this.refundOrchestrationService = refundOrchestrationService;
        this.eventPublisher = eventPublisher;
        this.self = self;
    }

    public void withdraw(Long sellerId, Long groupBuyId, String reason) {
        List<RefundTarget> targets = self.getObject().prepareWithdrawal(sellerId, groupBuyId);
        // 전원 환불(외부 호출). 일부가 실패해도 원장에 FAILED로 남아 재처리(RFD-04) 대상이 된다.
        for (RefundTarget target : targets) {
            refundOrchestrationService.refundParticipation(
                    target.participationId(), target.amount(), RefundReason.SELLER_FAULT);
        }
        // 위반 후보 생성은 penalty 도메인이 이 이벤트를 소비해 처리한다(seam).
        eventPublisher.publishEvent(new GroupBuyWithdrawnEvent(
                groupBuyId, sellerId, targets.size(), reason, LocalDateTime.now()));
    }

    /**
     * 소유·참여자 존재를 검증하고 공동구매를 취소하며 참여를 REFUNDING으로 전이한 뒤,
     * 환불 대상(참여ID·금액)을 모아 반환한다.
     */
    @Transactional
    public List<RefundTarget> prepareWithdrawal(Long sellerId, Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findByGroupBuyIdAndSellerId(groupBuyId, sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));

        List<Participation> paidParticipations =
                participationRepository.findByGroupBuyIdAndStatus(groupBuyId, Participation.STATUS_PAID);
        if (paidParticipations.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "참여자가 있는 모집 중 공동구매만 판매 철회할 수 있습니다.");
        }

        groupBuy.withdrawBySeller(); // 모집 중이어야 함

        List<RefundTarget> targets = new ArrayList<>();
        for (Participation participation : paidParticipations) {
            Payment payment = paymentRepository.findByParticipationId(participation.getParticipationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
            participation.markRefunding();
            targets.add(new RefundTarget(participation.getParticipationId(), payment.getKrwAmount()));
        }
        return targets;
    }

    public record RefundTarget(Long participationId, long amount) {
    }
}
