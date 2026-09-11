package com.gongguhan.domain.returnrequest.scheduler;

import com.gongguhan.domain.returnrequest.service.ReturnRefundRecordService;
import com.gongguhan.domain.returnrequest.service.ReturnRefundService;
import com.gongguhan.global.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReturnRefundScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReturnRefundScheduler.class);
    private static final int BATCH_SIZE = 100;
    private final ReturnRefundRecordService recordService;
    private final ReturnRefundService refundService;

    public ReturnRefundScheduler(ReturnRefundRecordService recordService,
                                 ReturnRefundService refundService) {
        this.recordService = recordService;
        this.refundService = refundService;
    }

    @Scheduled(fixedDelayString = "${app.returns.auto-refund-delay-ms:60000}")
    public void refundNeglectedReturns() {
        for (Long returnId : recordService.findAutomaticCandidates(BATCH_SIZE)) {
            try {
                refundService.processAutomatic(returnId);
            } catch (BusinessException exception) {
                log.warn("자동 반품 환불 처리 실패 returnId={} code={}",
                        returnId, exception.getErrorCode());
            }
        }
    }
}
