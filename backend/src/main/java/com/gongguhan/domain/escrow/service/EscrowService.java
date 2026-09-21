package com.gongguhan.domain.escrow.service;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 에스크로 상태 전이 (FR-ESC).
 *
 * <p><b>사용자가 직접 상태를 바꾸는 API 는 제공하지 않습니다.</b> 해제·환불은 결제·환불·
 * 정산 유스케이스 내부에서만 일어납니다. 그래서 이 서비스는 컨트롤러에 연결되지 않고
 * 다른 서비스가 호출합니다.
 *
 * <p><b>호출 예정 지점</b> (아직 미구현)
 * <ul>
 *   <li>{@link #release} — 정산 배치(STL), 관리자 수동 정산(ADM)</li>
 *   <li>{@link #refund} — 모집 실패, 참여 취소, 구매확정 전 반품, 판매자 귀책 무산(RFD)</li>
 * </ul>
 *
 * <p>규칙 자체는 {@link Escrow} 엔티티가 강제합니다. 이 서비스는 "구매확정됐는지"를
 * 판정해 엔티티에 넘겨주는 역할입니다. 참여 건은 다른 담당자 소유 패키지라 읽기만 합니다.
 */
@Service
@Transactional(readOnly = true)
public class EscrowService {

    private static final Logger log = LoggerFactory.getLogger(EscrowService.class);

    /**
     * 구매확정 상태값.
     *
     * <p>{@code participations.status} 의 값 중 하나입니다(schema v2 컬럼 주석:
     * {@code PAID/FULFILLED/SHIPPING/DELIVERED/CONFIRMED/SETTLED/...}).
     *
     * <p>여기에 문자열로 둔 이유: 구매확정(FR-CFM)이 아직 구현되지 않아
     * {@code Participation} 에 해당 상수가 없습니다. 그 패키지는 다른 담당자 소유라
     * 임의로 상수를 추가하지 않았습니다. 생기면 그 상수로 교체합니다.
     */
    private static final String PARTICIPATION_CONFIRMED = "CONFIRMED";

    /**
     * 정산이 끝난 상태.
     *
     * <p>이미 정산된 건도 에스크로 관점에서는 해제 완료이므로 재해제 대상이 아닙니다.
     * 엔티티의 {@code requireHeld} 가 막지만, 판정 시 함께 인정합니다.
     */
    private static final String PARTICIPATION_SETTLED = "SETTLED";

    private final EscrowRepository escrowRepository;
    private final ParticipationRepository participationRepository;
    private final Clock clock;

    public EscrowService(EscrowRepository escrowRepository,
                          ParticipationRepository participationRepository,
                          Clock clock) {
        this.escrowRepository = escrowRepository;
        this.participationRepository = participationRepository;
        this.clock = clock;
    }

    /**
     * 구매확정된 참여 건의 에스크로를 해제합니다 (FR-ESC-02, DR-33).
     *
     * <p>구매확정 여부를 참여 건에서 직접 읽어 판정합니다. 호출 측이 boolean 을 만들어
     * 넘기게 하면 정산 배치와 관리자 경로가 각자 판정하게 되어 규칙이 갈립니다.
     *
     * @throws BusinessException {@code RESOURCE_NOT_FOUND} — 에스크로·참여 건 없음
     * @throws BusinessException {@code INVALID_STATE_TRANSITION} — 구매확정 전이거나
     *         이미 해제·환불된 건
     */
    @Transactional
    public Escrow release(Long participationId) {
        Escrow escrow = findByParticipationId(participationId);
        Participation participation = findParticipation(participationId);

        boolean confirmed = isPurchaseConfirmed(participation.getStatus());
        escrow.release(confirmed, LocalDateTime.now(clock));

        log.info("에스크로 해제 escrowId={} participationId={} amount={}",
                escrow.getEscrowId(), participationId, escrow.getAmount());
        return escrow;
    }

    /**
     * 에스크로를 환불로 종료합니다 (FR-ESC-05).
     *
     * <p>모집 실패·참여 취소·구매확정 전 반품·판매자 귀책 무산에서 호출합니다.
     * 구매확정 여부를 보지 않습니다 — 확정 전 반품이 정상 경로이기 때문입니다.
     *
     * <p>실제 환불 송금(카드 취소·계좌 이체·코인 전송)은 RFD 도메인이 담당합니다.
     * 여기서는 에스크로 상태만 닫습니다.
     *
     * @param reason 로그용 사유. 감사 로그는 별도 티켓(FR-ESC-06)입니다.
     */
    @Transactional
    public Escrow refund(Long participationId, String reason) {
        Escrow escrow = findByParticipationId(participationId);
        escrow.refund(LocalDateTime.now(clock));

        log.info("에스크로 전액 환불 escrowId={} participationId={} amount={} reason={}",
                escrow.getEscrowId(), participationId, escrow.getAmount(), reason);
        return escrow;
    }

    /**
     * 일부만 환불합니다 (FR-ESC-05).
     *
     * <p>반품 환불액이 결제금액 전액이 아닌 경우가 있습니다. 단순변심 반품이면 최초
     * 배송비를 돌려주지 않아 54,000원 중 42,000원만 환불되고, <b>남은 12,000원은
     * 판매자에게 정산되어야 합니다.</b>
     *
     * <p>그래서 남은 금액이 있으면 에스크로는 {@code HELD} 로 남습니다. 여기서
     * 종료시키면 판매자가 받아야 할 돈이 정산 대상에서 조용히 사라집니다.
     *
     * @param refundAmount 이번에 환불할 금액. 누계가 보관 금액을 넘으면 거부합니다.
     */
    @Transactional
    public Escrow refundPartially(Long participationId, long refundAmount, String reason) {
        Escrow escrow = findByParticipationId(participationId);
        escrow.refundPartially(refundAmount, LocalDateTime.now(clock));

        log.info("에스크로 부분 환불 escrowId={} participationId={} 환불={} 잔액={} 상태={} reason={}",
                escrow.getEscrowId(), participationId, refundAmount,
                escrow.heldAmount(), escrow.getStatus(), reason);
        return escrow;
    }

    /** 0원 반품도 원 에스크로 존재/보관 상태를 검증하되 자금 누계를 바꾸지 않는다. */
    @Transactional
    public Escrow refundReturn(Long participationId, long amount, String reason) {
        if (amount > 0) { return refundPartially(participationId, amount, reason); }
        Escrow escrow = findByParticipationId(participationId);
        if (amount != 0 || !escrow.isHeld()) { throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION); }
        return escrow;
    }

    /** 정산 서비스가 반품 배송비 근거를 잠금 후 재검증한 동일 트랜잭션에서만 호출한다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Escrow releaseReturnShipping(Long participationId, long expectedAmount) {
        Participation participation = findParticipation(participationId);
        Escrow escrow = findByParticipationId(participationId);
        if (!Participation.STATUS_REFUNDED.equals(participation.getStatus()) || participation.isConfirmHold()
                || expectedAmount <= 0 || escrow.heldAmount() != expectedAmount) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        escrow.release(true, LocalDateTime.now(clock));
        return escrow;
    }

    /**
     * 정산 대상 금액 (FR-STL-07).
     *
     * <p>{@code amount} 가 아니라 잔액입니다. 이미 환불한 만큼을 판매자에게 한 번 더
     * 주지 않기 위한 구분입니다.
     */
    @Transactional(readOnly = true)
    public long getSettlementAmount(Long participationId) {
        return findByParticipationId(participationId).heldAmount();
    }

    /** 환불로 빠져나간 총액 (FR-ADM-12). 부분 환불은 상태 집계에 잡히지 않습니다. */
    @Transactional(readOnly = true)
    public long getTotalRefundedAmount() {
        return escrowRepository.sumRefundedAmount();
    }

    /**
     * 공동구매별 보관 총액 (FR-ESC-04).
     *
     * <p>ESC-01/ESC-02 조회 API 는 별도 티켓(S15P21B205-100)입니다. 여기서는 정산·환불
     * 로직이 쓸 수 있는 조회만 제공합니다.
     */
    public long getHeldAmountByGroupBuy(Long groupBuyId) {
        return escrowRepository.sumHeldAmountByGroupBuy(groupBuyId);
    }

    /** 플랫폼 전체 보관 총액 (FR-ADM-12). */
    public long getTotalHeldAmount() {
        return escrowRepository.sumHeldAmount();
    }

    /**
     * 불변식을 검증합니다 (FR-ESC-08, NFR-DT-05).
     *
     * <blockquote>에스크로 보관 총액 = 미해제 참여 건의 결제금액 합계</blockquote>
     *
     * <p>정상이라면 빈 목록입니다. 값이 나오면 결제 확정 경로에서 금액이 어긋나게
     * 기록된 것이므로 즉시 확인이 필요합니다. 관리자 점검 화면과 운영 중 알람에
     * 쓸 용도입니다.
     */
    public List<Escrow> findInvariantViolations() {
        List<Escrow> mismatches = escrowRepository.findAmountMismatches();
        List<Escrow> orphans = escrowRepository.findOrphans();

        if (!mismatches.isEmpty() || !orphans.isEmpty()) {
            log.error("에스크로 불변식 위반 - 금액 불일치 {}건, 고아 행 {}건",
                    mismatches.size(), orphans.size());
        }
        if (orphans.isEmpty()) {
            return mismatches;
        }
        List<Escrow> all = new java.util.ArrayList<>(mismatches);
        all.addAll(orphans);
        return all;
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /**
     * 구매확정으로 볼 수 있는 참여 상태인가.
     *
     * <p>{@code SETTLED} 를 포함하는 이유: 정산까지 끝난 건은 구매확정을 이미 거쳤습니다.
     * 재해제 시도는 엔티티가 막지만, 판정 단계에서 "확정 안 됐다"고 잘못 말하지 않도록
     * 함께 인정합니다.
     */
    private boolean isPurchaseConfirmed(String participationStatus) {
        return PARTICIPATION_CONFIRMED.equals(participationStatus)
                || PARTICIPATION_SETTLED.equals(participationStatus);
    }

    private Escrow findByParticipationId(Long participationId) {
        return escrowRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "에스크로를 찾을 수 없습니다. participationId=" + participationId));
    }

    private Participation findParticipation(Long participationId) {
        return participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "참여 건을 찾을 수 없습니다. participationId=" + participationId));
    }
}
