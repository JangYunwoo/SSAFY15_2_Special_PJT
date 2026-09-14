package com.gongguhan.domain.returnrequest.scheduler;

import com.gongguhan.domain.returnrequest.service.ReturnRequestService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReturnAutoApprovalScheduler {

    private static final int BATCH_SIZE = 100;
    private final ReturnRequestService service;

    public ReturnAutoApprovalScheduler(ReturnRequestService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.returns.auto-approval-delay-ms:60000}")
    public void approveOverdueRequests() {
        for (Long returnId : service.autoApprovalCandidates(BATCH_SIZE)) {
            service.approveAutomatically(returnId);
        }
    }
}
