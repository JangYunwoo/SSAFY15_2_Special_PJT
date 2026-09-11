package com.gongguhan.domain.groupbuy.service;

import com.gongguhan.domain.groupbuy.dto.GroupBuyJudgementResult;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.entity.GroupBuy.RecruitmentJudgement;
import com.gongguhan.domain.groupbuy.event.GroupBuyFailedEvent;
import com.gongguhan.domain.groupbuy.event.GroupBuyFulfilledEvent;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupBuyRecruitmentService {

    private static final int BATCH_SIZE = 500;
    private static final String FAILURE_REASON_TARGET_NOT_REACHED = "TARGET_NOT_REACHED";

    private final GroupBuyRepository groupBuyRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final PolicyService policyService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GroupBuyRecruitmentService(
            GroupBuyRepository groupBuyRepository,
            BusinessDayCalculator businessDayCalculator,
            PolicyService policyService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.businessDayCalculator = businessDayCalculator;
        this.policyService = policyService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public GroupBuyJudgementResult judgeCandidates() {
        LocalDateTime judgedAt = LocalDateTime.now(clock);
        List<GroupBuy> candidates = groupBuyRepository.findJudgementCandidates(
                judgedAt, PageRequest.of(0, BATCH_SIZE));
        int fulfilledCount = 0;
        int failedCount = 0;

        for (GroupBuy groupBuy : candidates) {
            RecruitmentJudgement judgement = judge(groupBuy, judgedAt);
            if (judgement == RecruitmentJudgement.FULFILLED) {
                fulfilledCount++;
            } else if (judgement == RecruitmentJudgement.FAILED) {
                failedCount++;
            }
        }

        return new GroupBuyJudgementResult(candidates.size(), fulfilledCount, failedCount);
    }

    @Transactional
    public void judgeTargetReached(GroupBuy groupBuy) {
        if (groupBuy.getCurrentParticipants() < groupBuy.getTargetParticipants()) {
            return;
        }
        judge(groupBuy, LocalDateTime.now(clock));
    }

    private RecruitmentJudgement judge(GroupBuy groupBuy, LocalDateTime judgedAt) {
        LocalDateTime shippingDeadlineAt = groupBuy.getCurrentParticipants() >= groupBuy.getTargetParticipants()
                ? businessDayCalculator.addBusinessDays(
                        judgedAt, policyService.getInt(PolicyKey.SHIPPING_DEADLINE_DAYS))
                : null;
        RecruitmentJudgement judgement = groupBuy.judgeRecruitment(judgedAt, shippingDeadlineAt);
        if (judgement == RecruitmentJudgement.FULFILLED) {
            publishFulfilled(groupBuy);
        } else if (judgement == RecruitmentJudgement.FAILED) {
            publishFailed(groupBuy, judgedAt);
        }
        return judgement;
    }

    private void publishFulfilled(GroupBuy groupBuy) {
        eventPublisher.publishEvent(new GroupBuyFulfilledEvent(
                groupBuy.getGroupBuyId(),
                groupBuy.getSellerId(),
                groupBuy.getCurrentParticipants(),
                groupBuy.getUnitShareAmount(),
                groupBuy.getFulfilledAt(),
                groupBuy.getShippingDeadlineAt()));
    }

    private void publishFailed(GroupBuy groupBuy, LocalDateTime failedAt) {
        eventPublisher.publishEvent(new GroupBuyFailedEvent(
                groupBuy.getGroupBuyId(),
                groupBuy.getSellerId(),
                groupBuy.getCurrentParticipants(),
                failedAt,
                FAILURE_REASON_TARGET_NOT_REACHED));
    }
}
