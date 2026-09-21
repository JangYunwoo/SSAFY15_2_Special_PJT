package com.gongguhan.domain.settlement.service;

import com.gongguhan.domain.escrow.service.EscrowService;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.entity.SettlementItem;
import com.gongguhan.domain.settlement.event.SettlementCompletedEvent;
import com.gongguhan.domain.settlement.repository.SettlementItemRepository;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository.SettlementTarget;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 공동구매의 정산을 기록합니다 (FR-STL-01~05).
 *
 * <p><b>{@link SettlementBatchService} 와 빈을 나눈 이유는 자기 호출 문제입니다.</b>
 * 같은 클래스 안에서 메서드를 부르면 스프링 프록시를 거치지 않아
 * {@code @Transactional} 이 걸리지 않습니다. 한 공동구매의 정산은 반드시 한
 * 트랜잭션이어야 하므로 — 항목만 저장되고 에스크로가 남으면 그 돈은 영원히
 * 정산되지도 환불되지도 않습니다 — 별도 빈으로 뺐습니다. 코인 결제의
 * {@code CryptoTxVerificationService} / {@code CryptoTxRecordService} 분리와 같습니다.
 */
@Service
public class SettlementRecordService {

    private static final Logger log = LoggerFactory.getLogger(SettlementRecordService.class);

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final ParticipationRepository participationRepository;
    private final EscrowService escrowService;
    private final ApplicationEventPublisher events;
    private final SettlementTargetRepository targetRepository;
    private final GroupBuyRepository groupBuyRepository;

    public SettlementRecordService(SettlementRepository settlementRepository,
                                   SettlementItemRepository settlementItemRepository,
                                   ParticipationRepository participationRepository,
                                   EscrowService escrowService,
                                   ApplicationEventPublisher events, SettlementTargetRepository targetRepository,
                                   GroupBuyRepository groupBuyRepository) {
        this.settlementRepository = settlementRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.participationRepository = participationRepository;
        this.escrowService = escrowService;
        this.events = events;
        this.targetRepository = targetRepository;
        this.groupBuyRepository = groupBuyRepository;
    }

    /**
     * 정산 생성 → 항목 저장 → 에스크로 해제 → 참여 건 전환을 한 트랜잭션으로 묶습니다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         이미 정산된 참여 건이 섞인 경우 ({@code UNIQUE(participation_id)})
     */
    @Transactional
    public Settlement settle(Long groupBuyId, List<SettlementTarget> targets, LocalDateTime now) {
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "정산 대상이 없습니다.");
        }
        // 참여 취소와 같은 참여 → 공구 순서다. 여러 참여는 ID 오름차순으로 모두 잠근다.
        var lockedParticipations = new java.util.HashMap<Long, Participation>();
        for (SettlementTarget target : targets.stream()
                .sorted(java.util.Comparator.comparing(SettlementTarget::participationId)).toList()) {
            Participation participation = participationRepository.findWithLockByParticipationId(target.participationId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            if (!groupBuyId.equals(participation.getGroupBuyId())) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
            }
            lockedParticipations.put(target.participationId(), participation);
        }
        // 서로 다른 참여 목록도 공구의 회차/차액을 공유한다. 일반 조회는 이 잠금 뒤에 한다.
        groupBuyRepository.findWithLockByGroupBuyId(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        // 후보 조회는 잠금이 아니다. 반품/이의제기가 끼어들었는지 쓰기 직전에 재검증한다.
        for (SettlementTarget target : targets) {
            Participation participation = lockedParticipations.get(target.participationId());
            if (target.returnShipping()) {
                SettlementTarget current = targetRepository.findReturnShippingTarget(target.participationId(), now)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "반품 배송비 잔액 근거가 변경되었습니다."));
                if (!groupBuyId.equals(current.groupBuyId()) || !current.equals(target)) {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "반품 배송비 정산 금액이 변경되었습니다.");
                }
                continue;
            }
            if (!groupBuyId.equals(participation.getGroupBuyId())
                    || !groupBuyId.equals(target.groupBuyId())
                    || !Participation.STATUS_CONFIRMED.equals(participation.getStatus())
                    || participation.isConfirmHold()
                    || participation.getSettlementEligibleAt() == null
                    || participation.getSettlementEligibleAt().isAfter(now)
                    || participation.getShareAmount() != target.shareAmount()
                    || participation.getShippingFee() != target.shippingFee()
                    || escrowService.getSettlementAmount(target.participationId()) != participation.getTotalAmount()) {
                throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                        "정산 후보의 상태 또는 금액이 변경되었습니다. participationId=" + target.participationId());
            }
        }
        SettlementTarget first = targets.get(0);
        long shareTotal = targets.stream().mapToLong(SettlementTarget::shareAmount).sum();
        long shippingTotal = targets.stream().mapToLong(SettlementTarget::shippingFee).sum();

        int roundNo = settlementRepository.nextRoundNo(groupBuyId);
        // 배송비만 정산한 회차는 상품 차액을 소진하지 않는다. 최초 상품 포함 회차에 한 번 반영한다.
        long roundingDiff = shareTotal > 0
                && !settlementRepository.existsByGroupBuyIdAndRoundingDiffGreaterThan(groupBuyId, 0)
                ? first.roundingDiff() : 0L;

        Settlement settlement = settlementRepository.save(Settlement.create(
                groupBuyId, first.sellerId(), roundNo, targets.size(),
                shareTotal, shippingTotal, roundingDiff, first.platformFeeRate(), now));

        for (SettlementTarget target : targets) {
            settlementItemRepository.save(SettlementItem.of(settlement.getSettlementId(),
                    target.participationId(), target.shareAmount(), target.shippingFee()));
            if (target.returnShipping()) {
                escrowService.releaseReturnShipping(target.participationId(), target.shippingFee());
            } else {
                releaseAndMark(target.participationId());
            }
        }
        // UNIQUE(participation_id) 위반을 지금 드러낸다. 커밋까지 미루면 호출 측이
        // 어느 단계에서 겹쳤는지 알 수 없다.
        settlementItemRepository.flush();

        log.info("정산 생성 settlementId={} groupBuyId={} 회차={} 건수={} 분담금={} 배송비={} 수수료={} 지급액={}",
                settlement.getSettlementId(), groupBuyId, roundNo, targets.size(),
                shareTotal, shippingTotal, settlement.getPlatformFee(),
                settlement.getSettlementAmount());
        return settlement;
    }

    /**
     * 에스크로를 해제하고 참여 건을 정산 완료로 바꿉니다.
     *
     * <p>에스크로 해제는 "이 돈은 더 이상 보관 중이 아니다" 를 뜻합니다. 실제 이체는
     * 아직이지만, 정산 명세에 들어간 순간 보관 대상에서 빠져야 관리자 집계(ESC-03)의
     * 보관액과 정산대기액이 맞습니다.
     */
    private void releaseAndMark(Long participationId) {
        escrowService.release(participationId);

        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "참여 건을 찾을 수 없습니다. participationId=" + participationId));
        participation.markSettled();
    }

    /**
     * 이체 성공을 기록하고 판매자 알림 이벤트를 발행합니다 (STL-04).
     *
     * <p>이벤트는 커밋 이후에 소비되므로(소비 측이 {@code AFTER_COMMIT}), 알림 저장이
     * 실패해도 이체 기록은 남습니다. 돈은 나갔는데 기록이 사라지는 쪽이 훨씬 위험합니다.
     */
    @Transactional
    public Settlement completeTransfer(Long settlementId, String institutionTxNo,
                                       Long withdrawalTxNo, Long depositTxNo,
                                       LocalDateTime now) {
        Settlement settlement = load(settlementId);
        settlement.completeTransfer(institutionTxNo, withdrawalTxNo, depositTxNo, now);

        events.publishEvent(new SettlementCompletedEvent(
                settlementId, settlement.getSellerId(), settlement.getGroupBuyId(),
                settlement.getRoundNo(), settlement.getSettlementAmount(), depositTxNo, now));

        log.info("정산 이체 완료 settlementId={} sellerId={} 금액={} 입금거래번호={}",
                settlementId, settlement.getSellerId(), settlement.getSettlementAmount(),
                depositTxNo);
        return settlement;
    }

    /** 이체 실패를 기록합니다. 자동 재시도 대상으로 남습니다 (STL-07). */
    @Transactional
    public Settlement failTransfer(Long settlementId, String reason) {
        Settlement settlement = load(settlementId);
        settlement.failTransfer(truncate(reason));
        log.warn("정산 이체 실패 settlementId={} reason={}", settlementId, reason);
        return settlement;
    }

    /**
     * 이체 결과를 알 수 없음으로 기록합니다.
     *
     * <p>자동 재시도에서 빠집니다. 사람이 금융망 거래내역을 확인한 뒤 수동으로
     * 재처리해야 합니다.
     */
    @Transactional
    public Settlement markTransferUnknown(Long settlementId, String reason) {
        Settlement settlement = load(settlementId);
        settlement.markTransferUnknown(truncate(reason));
        log.error("정산 이체 결과 불명 settlementId={} reason={} - 금융망 거래내역 확인 필요",
                settlementId, reason);
        return settlement;
    }

    /** 분쟁 등으로 정산을 보류합니다 (STL-05, FR-STL-13). */
    @Transactional
    public Settlement hold(Long settlementId, String reason) {
        Settlement settlement = load(settlementId);
        settlement.hold(truncate(reason));
        log.info("정산 보류 settlementId={} reason={}", settlementId, reason);
        return settlement;
    }

    /** 보류를 해제합니다 (STL-06). */
    @Transactional
    public Settlement releaseHold(Long settlementId) {
        Settlement settlement = load(settlementId);
        settlement.releaseHold();
        log.info("정산 보류 해제 settlementId={}", settlementId);
        return settlement;
    }

    /** 금융망 호출 전에 실행권을 잠금 하에 기록하고 커밋한다. 단순 조회가 아니다. */
    @Transactional
    public Settlement loadExecutable(Long settlementId) {
        Settlement settlement = load(settlementId);
        validateExecutable(settlement);
        settlement.claimTransfer();
        return settlement;
    }

    private void validateExecutable(Settlement settlement) {
        Long settlementId = settlement.getSettlementId();
        if (!settlement.isExecutable()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "실행할 수 없는 정산입니다. settlementId=%d status=%s"
                            .formatted(settlementId, settlement.getStatus()));
        }
        if (settlement.isTransferOutcomeUnknown()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "이전 이체 결과를 알 수 없습니다. 금융망 거래내역을 확인한 뒤 처리하세요. settlementId="
                            + settlementId);
        }
    }

    @Transactional
    public void assignTransferReference(Long settlementId, String institutionTxNo) {
        load(settlementId).assignTransferReference(institutionTxNo);
    }

    private Settlement load(Long settlementId) {
        return settlementRepository.findForUpdate(settlementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "정산 내역을 찾을 수 없습니다. settlementId=" + settlementId));
    }

    /** {@code transfer_fail_reason} 이 VARCHAR(255) 라 넘치면 저장 시점에 깨진다. */
    private String truncate(String reason) {
        if (reason == null) {
            return "사유가 기록되지 않았습니다.";
        }
        return reason.length() <= 255 ? reason : reason.substring(0, 255);
    }
}
