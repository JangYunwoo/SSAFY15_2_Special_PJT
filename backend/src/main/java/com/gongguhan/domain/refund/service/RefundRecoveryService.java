package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.refund.repository.FailedRecruitmentRefundRepository;
import com.gongguhan.domain.refund.repository.RefundRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class RefundRecoveryService {
    private static final Logger log=LoggerFactory.getLogger(RefundRecoveryService.class);
    private static final int LIMIT=500;
    private final FailedRecruitmentRefundRepository failed;
    private final FailedRecruitmentRefundRecordService records;
    private final RefundRepository refunds;
    private final RefundOrchestrationService execution;
    private final Clock clock;
    public RefundRecoveryService(FailedRecruitmentRefundRepository failed, FailedRecruitmentRefundRecordService records,
            RefundRepository refunds, RefundOrchestrationService execution, Clock clock) {
        this.failed=failed;this.records=records;this.refunds=refunds;this.execution=execution;this.clock=clock;
    }
    public void run() {
        processFailedGroup(null);
        for(Long id:refunds.findUnsent(LocalDateTime.now(clock).minusSeconds(30),PageRequest.of(0,LIMIT))) resume(id);
        for(Long id:refunds.findUnfinalized(PageRequest.of(0,LIMIT))) resume(id);
    }
    public void processFailedGroup(Long groupBuyId) {
        for(Long participationId:failed.findCandidates(groupBuyId,PageRequest.of(0,LIMIT))) {
            try { Long id=records.reserve(participationId);if(id!=null)resume(id); }
            catch(RuntimeException exception){log.error("모집 실패 환불 예약 실패 participationId={}",participationId,exception);}
        }
    }
    private void resume(Long id) {
        try {execution.resumePersisted(id);}
        catch(RuntimeException exception){log.error("환불 복구 보류 refundId={}",id,exception);}
    }
}
