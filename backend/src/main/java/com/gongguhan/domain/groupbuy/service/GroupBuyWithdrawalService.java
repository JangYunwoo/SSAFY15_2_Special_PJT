package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.event.GroupBuyWithdrawnEvent;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GRP-10 참여 발생 후 판매자 귀책 판매 철회 (FR-PEN-01~02, 07).
 *
 * <p>스펙의 "공동구매 취소 · 전원 환불 개시 · 판매자 위반 후보 생성"을 한 흐름으로 실행하되,
 * 도메인 경계를 지킨다:
 * <ul>
 *   <li>공동구매 취소 + 참여 상태 전이 - groupbuy/participation(직접)</li>
 *   <li>전원 환불 - 취소와 함께 원장을 예약하고 커밋 후 공통 실행권 경로로 실행</li>
 *   <li>위반 후보 생성 - {@link GroupBuyWithdrawnEvent}를 발행해 penalty 도메인(코인)이 소비(seam)</li>
 * </ul>
 *
 * <p>외부 환불 호출은 트랜잭션 밖에서 반복한다. {@link #prepareWithdrawal}이 상태 전이와
 * 모든 환불 원장을 함께 예약하므로 중단되어도 기존 DB 미송신 복구가 뒤 작업을 발견한다.
 */
@Service
public class GroupBuyWithdrawalService {
    private static final Logger log = LoggerFactory.getLogger(GroupBuyWithdrawalService.class);

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final PaymentRepository paymentRepository;
    private final RefundOrchestrationService refundOrchestrationService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<GroupBuyWithdrawalService> self;
    private final Clock clock;
    private final RefundLedgerService ledger;

    public GroupBuyWithdrawalService(GroupBuyRepository groupBuyRepository,
                                     ParticipationRepository participationRepository,
                                     PaymentRepository paymentRepository,
                                     RefundOrchestrationService refundOrchestrationService,
                                     ApplicationEventPublisher eventPublisher,
                                     ObjectProvider<GroupBuyWithdrawalService> self,
                                     Clock clock, RefundLedgerService ledger) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.paymentRepository = paymentRepository;
        this.refundOrchestrationService = refundOrchestrationService;
        this.eventPublisher = eventPublisher;
        this.self = self;
        this.clock = clock;
        this.ledger = ledger;
    }

    @Transactional(propagation = Propagation.NEVER)
    public void withdraw(Long sellerId, Long groupBuyId, String reason) {
        List<RefundTarget> targets = prepareWithFreshSnapshot(sellerId, groupBuyId, reason);
        // 한 요청의 장애로 뒤 원장의 실행을 생략하지 않는다. 결과 불명 원장은 자동 재송금하지 않는다.
        for (RefundTarget target : targets) {
            try {
                refundOrchestrationService.resumePersisted(target.refundId());
            } catch (RuntimeException exception) {
                log.error("판매 철회 환불 실행 보류 groupBuyId={} refundId={} cause={}",
                        groupBuyId, target.refundId(), exception.getClass().getSimpleName());
            }
        }
    }

    private List<RefundTarget> prepareWithFreshSnapshot(Long sellerId, Long groupBuyId, String reason) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                // 프록시 호출의 롤백이 끝난 뒤 다음 호출에서 새 TX/영속성 컨텍스트를 연다.
                return self.getObject().prepareWithdrawal(sellerId, groupBuyId, reason);
            } catch (WithdrawalSnapshotChangedException conflict) {
                if (attempt == 2) {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                            "참여 현황이 변경되고 있습니다. 잠시 후 판매 철회를 다시 요청해 주세요.");
                }
            }
        }
        throw new IllegalStateException("unreachable withdrawal retry");
    }

    /**
     * 소유·참여자 존재를 검증하고 공동구매를 취소하며 참여를 REFUNDING으로 전이한 뒤,
     * 환불 대상 전원의 원장을 같은 트랜잭션에서 예약하고 원장 ID를 반환한다.
     */
    @Transactional
    public List<RefundTarget> prepareWithdrawal(Long sellerId, Long groupBuyId, String reason) {
        Long observedVersion = groupBuyRepository.findVersionByGroupBuyId(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        // 개인 취소/반품과 동일한 participation → group 순서. 스칼라 조회는 1차 캐시를 채우지 않는다.
        List<Participation> paidParticipations = new ArrayList<>();
        for (Long participationId : participationRepository.findIdsByGroupBuyIdOrderByParticipationIdAsc(groupBuyId)) {
            participationRepository.findWithLockByParticipationId(participationId)
                    .filter(participation -> Participation.STATUS_PAID.equals(participation.getStatus()))
                    .ifPresent(paidParticipations::add);
        }
        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(groupBuyId)
                .filter(found -> found.getSellerId().equals(sellerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        // 목록 스냅샷 이후 새 참여/취소가 커밋됐다면 잠금을 모두 풀고 처음부터 읽어야 한다.
        // 같은 TX에서 일반 조회를 반복하면 MySQL REPEATABLE READ의 과거 목록이 재사용될 수 있다.
        if (!observedVersion.equals(groupBuy.getVersion())) {
            throw new WithdrawalSnapshotChangedException();
        }
        if (paidParticipations.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "참여자가 있는 모집 중 공동구매만 판매 철회할 수 있습니다.");
        }

        groupBuy.withdrawBySeller(); // 모집 중이어야 함

        List<RefundTarget> targets = new ArrayList<>();
        for (Participation participation : paidParticipations) {
            Payment payment = paymentRepository.findByParticipationId(participation.getParticipationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제 내역을 찾을 수 없습니다."));
            if (!Payment.STATUS_PAID.equals(payment.getStatus())) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "승인된 결제만 판매 철회 환불을 예약할 수 있습니다.");
            }
            RefundMethod method = switch (payment.getMethod()) {
                case Payment.METHOD_CARD -> RefundMethod.ACCOUNT_TRANSFER;
                case Payment.METHOD_KAKAOPAY -> RefundMethod.KAKAOPAY;
                case Payment.METHOD_CRYPTO -> RefundMethod.CRYPTO;
                default -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "지원하지 않는 환불 수단입니다.");
            };
            var reserved = ledger.openForParticipation(participation.getParticipationId(),
                    payment.getKrwAmount(), RefundReason.SELLER_FAULT, method);
            participation.markRefunding();
            targets.add(new RefundTarget(participation.getParticipationId(), payment.getKrwAmount(),
                    reserved.refund().getRefundId()));
        }
        // 필수 후보 기록은 취소와 같은 트랜잭션이다. 외부 환불 실패로 발행이 생략되면 안 된다.
        eventPublisher.publishEvent(new GroupBuyWithdrawnEvent(
                groupBuyId, sellerId, targets.size(), reason,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
        return targets;
    }

    public record RefundTarget(Long participationId, long amount, Long refundId) {
    }

    static final class WithdrawalSnapshotChangedException extends RuntimeException {
    }
}
