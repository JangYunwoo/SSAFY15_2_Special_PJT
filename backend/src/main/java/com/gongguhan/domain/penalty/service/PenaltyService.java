package com.gongguhan.domain.penalty.service;

import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.admin.service.AuditLogService;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.event.SanctionLevel;
import com.gongguhan.domain.penalty.event.SellerSanctionedEvent;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 제재 판정 (PEN-04~06, FR-PEN-06~13).
 *
 * <h2>후보와 확정을 나누는 이유</h2>
 *
 * <p>위반 후보는 <b>자동으로</b> 쌓입니다 — 배송 기한 초과 배치({@code -77})와 구매자
 * 이의 제기가 그렇습니다. 이걸 그대로 누적에 넣으면 사람 판단 없이 판매자가 정지됩니다.
 * 택배사 지연이나 구매자의 착오도 후보가 되기 때문입니다. 그래서 관리자가 확정한
 * 건만 셉니다.
 *
 * <h2>정지의 범위</h2>
 *
 * <p>{@code PENALTY_LIMIT}(정책값, 기본 3) 회 누적이면 판매를 정지합니다. 정지는
 * <b>신규 상품 등록과 공동구매 생성만</b> 막습니다({@code ProductService}·
 * {@code GroupBuyService} 가 {@code SELLER_SUSPENDED} 로 거부). 이미 진행 중인 거래의
 * 배송·반품·정산은 계속 처리됩니다 — 이미 돈을 낸 구매자를 볼모로 잡지 않기
 * 위해서입니다.
 *
 * <h2>누적의 기준</h2>
 *
 * <p>정답은 {@code seller_penalties} 의 확정 건수입니다.
 * {@code seller_profiles.penalty_count} 는 상품 등록·공동구매 생성이 매번 조인 없이
 * 확인하려고 둔 비정규화 값입니다. 확정·취소 때마다 함께 갱신하되, 어긋나면 원장이
 * 정답입니다.
 */
@Service
public class PenaltyService {

    private static final Logger log = LoggerFactory.getLogger(PenaltyService.class);

    private final SellerPenaltyRepository penaltyRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final PolicyService policyService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public PenaltyService(SellerPenaltyRepository penaltyRepository,
                          SellerProfileRepository sellerProfileRepository,
                          PolicyService policyService,
                          AuditLogService auditLogService,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.penaltyRepository = penaltyRepository;
        this.sellerProfileRepository = sellerProfileRepository;
        this.policyService = policyService;
        this.auditLogService = auditLogService;
        this.events = events;
        this.clock = clock;
    }

    /**
     * 위반을 확정합니다 (PEN-04).
     *
     * <p>확정 → 누적 증가 → 임계 도달 시 정지 → 알림 이벤트 발행 순서입니다.
     * 이벤트는 알림 도메인({@code -110})이 소비해 판매자에게 통지합니다.
     *
     * @param adminId 판단한 관리자
     * @param memo    판단 근거. 판매자가 이의를 제기하면 이 기록이 근거가 됩니다
     */
    @Transactional
    public SellerPenalty confirm(Long adminId, Long penaltyId, String memo) {
        SellerPenalty penalty = load(penaltyId);
        // 일반 조회/정책 조회보다 먼저 잠근다. 다른 판정의 누적과 프로필 수정을 함께 보호한다.
        SellerProfile seller = loadSeller(penalty.getSellerId());
        LocalDateTime now = LocalDateTime.now(clock);
        penalty.confirm(adminId, memo, now);

        int limit = penaltyLimit();
        seller.recordPenalty(limit);

        // 비정규화 값이 아니라 원장을 세어 알린다. 둘이 어긋나도 알림은 사실을 말해야 한다.
        int confirmedCount = confirmedCount(penalty.getSellerId());
        SanctionLevel level = confirmedCount >= limit ? SanctionLevel.THREE_STRIKE
                : SanctionLevel.WARNING;

        log.info("위반 확정 penaltyId={} sellerId={} 누적={}/{} 판정={} 관리자={}",
                penaltyId, penalty.getSellerId(), confirmedCount, limit, level, adminId);

        auditLogService.record(adminId, AuditLogService.TARGET_SELLER_PENALTY, penaltyId,
                AuditLogService.ACTION_CONFIRM,
                AuditLogService.values("status", SellerPenalty.STATUS_CANDIDATE),
                AuditLogService.values("status", penalty.getStatus(), "sellerId", penalty.getSellerId(),
                        "confirmedCount", confirmedCount, "limit", limit,
                        "level", level.name(), "memo", memo == null ? "" : memo));

        events.publishEvent(new SellerSanctionedEvent(
                penalty.getSellerId(), penaltyId, level, confirmedCount,
                penalty.getReasonType(), now));
        return penalty;
    }

    /**
     * 위반 후보를 기각합니다 (PEN-05).
     *
     * <p>누적에 들어가지 않았던 건이라 판매자 상태는 건드리지 않습니다. 알림도 보내지
     * 않습니다 — 자동으로 잡힌 후보가 기각되는 것은 판매자에게 알릴 일이 아닙니다.
     */
    @Transactional
    public SellerPenalty dismiss(Long adminId, Long penaltyId, String memo) {
        SellerPenalty penalty = load(penaltyId);
        penalty.dismiss(adminId, memo, LocalDateTime.now(clock));

        auditLogService.record(adminId, AuditLogService.TARGET_SELLER_PENALTY, penaltyId,
                AuditLogService.ACTION_DISMISS,
                AuditLogService.values("status", SellerPenalty.STATUS_CANDIDATE),
                AuditLogService.values("status", penalty.getStatus(), "sellerId", penalty.getSellerId(),
                        "memo", memo == null ? "" : memo));

        log.info("위반 후보 기각 penaltyId={} sellerId={} 관리자={}",
                penaltyId, penalty.getSellerId(), adminId);
        return penalty;
    }

    /**
     * 확정된 위반을 취소합니다 (PEN-06, FR-PEN-13).
     *
     * <p>잘못 확정한 건을 되돌리는 경로입니다. 누적이 임계 아래로 내려가면 판매 정지도
     * 함께 풀립니다 — 관리자가 실수를 바로잡았는데 판매자가 계속 막혀 있으면 취소가
     * 의미 없습니다.
     */
    @Transactional
    public SellerPenalty revoke(Long adminId, Long penaltyId, String memo) {
        SellerPenalty penalty = load(penaltyId);
        SellerProfile seller = loadSeller(penalty.getSellerId());
        penalty.revoke(adminId, memo, LocalDateTime.now(clock));

        int limit = penaltyLimit();
        seller.revokePenalty(limit);

        auditLogService.record(adminId, AuditLogService.TARGET_SELLER_PENALTY, penaltyId,
                AuditLogService.ACTION_REVOKE,
                AuditLogService.values("status", SellerPenalty.STATUS_CONFIRMED),
                AuditLogService.values("status", penalty.getStatus(), "sellerId", penalty.getSellerId(),
                        "confirmedCount", confirmedCount(penalty.getSellerId()), "limit", limit,
                        "sellerStatus", seller.getStatus(), "memo", memo == null ? "" : memo));

        log.info("위반 확정 취소 penaltyId={} sellerId={} 누적={}/{} 상태={} 관리자={}",
                penaltyId, penalty.getSellerId(), confirmedCount(penalty.getSellerId()),
                limit, seller.getStatus(), adminId);
        return penalty;
    }

    /** 판매자의 확정 위반 횟수. 원장 기준입니다. */
    @Transactional(readOnly = true)
    public int confirmedCount(Long sellerId) {
        return (int) penaltyRepository.countBySellerIdAndStatus(
                sellerId, SellerPenalty.STATUS_CONFIRMED);
    }

    /** 정지 임계 횟수 (정책값 {@code PENALTY_LIMIT}). */
    public int penaltyLimit() {
        return policyService.getInt(PolicyKey.PENALTY_LIMIT);
    }

    private SellerPenalty load(Long penaltyId) {
        return penaltyRepository.findForUpdate(penaltyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "위반 기록을 찾을 수 없습니다. penaltyId=" + penaltyId));
    }

    private SellerProfile loadSeller(Long sellerId) {
        return sellerProfileRepository.findForUpdate(sellerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "판매자를 찾을 수 없습니다. sellerId=" + sellerId));
    }
}
