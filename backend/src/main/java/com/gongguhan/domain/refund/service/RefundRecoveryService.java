package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.refund.repository.FailedRecruitmentRefundRepository;
import com.gongguhan.domain.refund.repository.RefundRepository;
import com.gongguhan.domain.refund.repository.StrandedPaymentRefundRepository;
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
    private final StrandedPaymentRefundRepository strandedPayments;
    private final StrandedPaymentRefundRecordService strandedRecords;
    private final Clock clock;
    public RefundRecoveryService(FailedRecruitmentRefundRepository failed, FailedRecruitmentRefundRecordService records,
            RefundRepository refunds, RefundOrchestrationService execution,
            StrandedPaymentRefundRepository strandedPayments, StrandedPaymentRefundRecordService strandedRecords, Clock clock) {
        this.failed=failed;this.records=records;this.refunds=refunds;this.execution=execution;
        this.strandedPayments=strandedPayments;this.strandedRecords=strandedRecords;this.clock=clock;
    }
    public void run() {
        processFailedGroup(null);
        for(Long id:refunds.findUnsent(LocalDateTime.now(clock).minusSeconds(30),PageRequest.of(0,LIMIT))) resume(id);
        for(Long id:refunds.findUnfinalized(PageRequest.of(0,LIMIT))) resume(id);
        reconcileStrandedPayments();
    }
    /** 승인 후 공구 종료로 고립된 결제(참여행 없음)를 기존 수단별 실행기로 환불한다. */
    private void reconcileStrandedPayments() {
        for(Long paymentId:strandedPayments.findCandidates(PageRequest.of(0,LIMIT))) {
            try { Long id=strandedRecords.reserve(paymentId);if(id!=null)execution.resumeStrandedPayment(id); }
            catch(RuntimeException exception){log.error("고립 결제 환불 예약 실패 paymentId={}",paymentId,exception);}
        }
        for(Long id:refunds.findUnsentStrandedPayment(LocalDateTime.now(clock).minusSeconds(30),PageRequest.of(0,LIMIT))) {
            try {execution.resumeStrandedPayment(id);}
            catch(RuntimeException exception){log.error("고립 결제 환불 복구 보류 refundId={}",id,exception);}
        }
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
