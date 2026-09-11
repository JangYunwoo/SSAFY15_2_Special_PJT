package com.gongguhan.domain.groupbuy.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GroupBuyRecruitmentScheduler {

    private final GroupBuyRecruitmentService groupBuyRecruitmentService;

    public GroupBuyRecruitmentScheduler(GroupBuyRecruitmentService groupBuyRecruitmentService) {
        this.groupBuyRecruitmentService = groupBuyRecruitmentService;
    }

    @Scheduled(cron = "${app.jobs.group-buy-recruitment.cron:0 */5 * * * *}")
    public void judgeRecruitment() {
        groupBuyRecruitmentService.judgeCandidates();
    }
}
