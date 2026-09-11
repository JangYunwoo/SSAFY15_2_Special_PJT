package com.gongguhan.domain.participation.scheduler;

import com.gongguhan.domain.participation.service.PurchaseConfirmService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API 명세서 18장: 자동 구매확정 - 매 1시간, 참여 상태 조건부 갱신.
 * {@link com.gongguhan.domain.payment.common.scheduler.PaymentSlotExpiryScheduler}와
 * 같은 패턴 - 후보를 모아 건별 트랜잭션으로 처리하고, 한 건의 실패가 나머지를
 * 막지 않는다.
 */
@Component
@Profile("!test")
public class AutoConfirmScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmScheduler.class);

    private final PurchaseConfirmService purchaseConfirmService;

    public AutoConfirmScheduler(PurchaseConfirmService purchaseConfirmService) {
        this.purchaseConfirmService = purchaseConfirmService;
    }

    @Scheduled(cron = "${app.jobs.auto-confirm.cron:0 0 * * * *}")
    public void confirmOverdueDeliveries() {
        List<Long> candidates = purchaseConfirmService.findAutoConfirmCandidateIds();
        for (Long participationId : candidates) {
            try {
                purchaseConfirmService.autoConfirmIfDue(participationId);
            } catch (Exception e) {
                log.warn("자동 구매확정 실패: participationId={}", participationId, e);
            }
        }
    }
}
