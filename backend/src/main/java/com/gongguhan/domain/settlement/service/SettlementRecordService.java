package com.gongguhan.domain.settlement.service;

import com.gongguhan.domain.escrow.service.EscrowService;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.entity.SettlementItem;
import com.gongguhan.domain.settlement.repository.SettlementItemRepository;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository.SettlementTarget;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public SettlementRecordService(SettlementRepository settlementRepository,
                                   SettlementItemRepository settlementItemRepository,
                                   ParticipationRepository participationRepository,
                                   EscrowService escrowService) {
        this.settlementRepository = settlementRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.participationRepository = participationRepository;
        this.escrowService = escrowService;
    }

    /**
     * 정산 생성 → 항목 저장 → 에스크로 해제 → 참여 건 전환을 한 트랜잭션으로 묶습니다.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException
     *         이미 정산된 참여 건이 섞인 경우 ({@code UNIQUE(participation_id)})
     */
    @Transactional
    public Settlement settle(Long groupBuyId, List<SettlementTarget> targets, LocalDateTime now) {
        SettlementTarget first = targets.get(0);
        long shareTotal = targets.stream().mapToLong(SettlementTarget::shareAmount).sum();
        long shippingTotal = targets.stream().mapToLong(SettlementTarget::shippingFee).sum();

        int roundNo = settlementRepository.nextRoundNo(groupBuyId);
        // 절상 차액은 공동구매 생성 시 한 번 확정된 값이라 1회차에만 가산한다.
        // 회차마다 더하면 판매자가 같은 차액을 여러 번 받는다.
        long roundingDiff = roundNo == 1 ? first.roundingDiff() : 0L;

        Settlement settlement = settlementRepository.save(Settlement.create(
                groupBuyId, first.sellerId(), roundNo, targets.size(),
                shareTotal, shippingTotal, roundingDiff, first.platformFeeRate(), now));

        for (SettlementTarget target : targets) {
            settlementItemRepository.save(SettlementItem.of(settlement.getSettlementId(),
                    target.participationId(), target.shareAmount(), target.shippingFee()));
            releaseAndMark(target.participationId());
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
}
