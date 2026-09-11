package com.gongguhan.domain.delivery.service;

import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;

/**
 * 배송 기한 초과 감지 (S15P21B205-77, 명세 18장 "배송 기한 감지" 매 1시간).
 *
 * <ul>
 *   <li>NO_INVOICE: 모집 완료(FULFILLED) 후 발송 기한(shipping_deadline_at)이 지났는데
 *       송장 미등록 배송이 남아 있는 공동구매</li>
 *   <li>FAKE_INVOICE: 발송 처리 후 FAKE_INVOICE_DETECT_DAYS가 지나도록 배송완료로
 *       전환되지 않은 배송이 있는 공동구매</li>
 * </ul>
 *
 * <p>후보는 {@code seller_penalties(status=CANDIDATE)}로 쌓이고, 같은 공동구매·같은
 * 사유는 상태와 무관하게 1건만 만든다(중복 생성 방지). 확정·기각은 관리자 검토
 * (S15P21B205-83)의 몫이다.
 */
@Service
@Transactional(readOnly = true)
public class ShippingViolationService {

    private static final Logger log = LoggerFactory.getLogger(ShippingViolationService.class);

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final DeliveryRepository deliveryRepository;
    private final SellerPenaltyRepository sellerPenaltyRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public ShippingViolationService(GroupBuyRepository groupBuyRepository,
                                     ParticipationRepository participationRepository,
                                     DeliveryRepository deliveryRepository,
                                     SellerPenaltyRepository sellerPenaltyRepository,
                                     PolicyService policyService,
                                     Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.deliveryRepository = deliveryRepository;
        this.sellerPenaltyRepository = sellerPenaltyRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    @Transactional
    public void detectViolations() {
        LocalDateTime now = LocalDateTime.now(clock);
        int noInvoice = detectNoInvoice(now);
        int fakeInvoice = detectFakeInvoice(now);
        if (noInvoice > 0 || fakeInvoice > 0) {
            log.info("배송 기한 위반 후보 등록: NO_INVOICE {}건, FAKE_INVOICE {}건", noInvoice, fakeInvoice);
        }
    }

    private int detectNoInvoice(LocalDateTime now) {
        int registered = 0;
        List<GroupBuy> overdue = groupBuyRepository
                .findByStatusAndShippingDeadlineAtBefore(GroupBuy.STATUS_FULFILLED, now);
        for (GroupBuy groupBuy : overdue) {
            if (sellerPenaltyRepository.existsByGroupBuyIdAndReasonType(
                    groupBuy.getGroupBuyId(), SellerPenalty.REASON_NO_INVOICE)) {
                continue;
            }
            long missingInvoiceCount = countMissingInvoices(groupBuy.getGroupBuyId());
            if (missingInvoiceCount == 0) {
                continue;
            }
            sellerPenaltyRepository.save(SellerPenalty.candidate(
                    groupBuy.getSellerId(), groupBuy.getGroupBuyId(), SellerPenalty.REASON_NO_INVOICE,
                    "발송 기한(%s) 초과, 송장 미등록 %d건".formatted(groupBuy.getShippingDeadlineAt(), missingInvoiceCount),
                    now));
            registered++;
        }
        return registered;
    }

    private long countMissingInvoices(Long groupBuyId) {
        List<Long> participationIds = participationRepository
                .findByGroupBuyIdOrderByParticipationIdAsc(groupBuyId)
                .stream()
                .map(Participation::getParticipationId)
                .toList();
        if (participationIds.isEmpty()) {
            return 0;
        }
        return deliveryRepository.findByParticipationIdIn(participationIds).stream()
                .filter(delivery -> !delivery.hasInvoice())
                .count();
    }

    private int detectFakeInvoice(LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(policyService.getInt(PolicyKey.FAKE_INVOICE_DETECT_DAYS));
        List<Delivery> stalled = deliveryRepository
                .findByStatusAndShippedAtBefore(Delivery.STATUS_SHIPPING, threshold);
        if (stalled.isEmpty()) {
            return 0;
        }

        Set<Long> groupBuyIds = new LinkedHashSet<>();
        for (Delivery delivery : stalled) {
            participationRepository.findById(delivery.getParticipationId())
                    .ifPresent(participation -> groupBuyIds.add(participation.getGroupBuyId()));
        }

        int registered = 0;
        for (Long groupBuyId : groupBuyIds) {
            if (sellerPenaltyRepository.existsByGroupBuyIdAndReasonType(
                    groupBuyId, SellerPenalty.REASON_FAKE_INVOICE)) {
                continue;
            }
            GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId).orElse(null);
            if (groupBuy == null) {
                continue;
            }
            sellerPenaltyRepository.save(SellerPenalty.candidate(
                    groupBuy.getSellerId(), groupBuyId, SellerPenalty.REASON_FAKE_INVOICE,
                    "발송 처리 후 %d일 경과에도 배송완료 미전환".formatted(
                            policyService.getInt(PolicyKey.FAKE_INVOICE_DETECT_DAYS)),
                    now));
            registered++;
        }
        return registered;
    }
}
