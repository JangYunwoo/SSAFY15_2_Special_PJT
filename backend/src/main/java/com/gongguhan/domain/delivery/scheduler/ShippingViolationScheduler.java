package com.gongguhan.domain.delivery.scheduler;

import com.gongguhan.domain.delivery.service.ShippingViolationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** API 명세서 18장: 배송 기한 감지 - 매 1시간. 자동 구매확정 배치(정시)와 30분 어긋나게 돈다. */
@Component
@Profile("!test")
public class ShippingViolationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShippingViolationScheduler.class);

    private final ShippingViolationService shippingViolationService;

    public ShippingViolationScheduler(ShippingViolationService shippingViolationService) {
        this.shippingViolationService = shippingViolationService;
    }

    @Scheduled(cron = "${app.jobs.shipping-violation.cron:0 30 * * * *}")
    public void detectViolations() {
        try {
            shippingViolationService.detectViolations();
        } catch (Exception e) {
            log.warn("배송 기한 위반 감지 배치 실패 - 다음 주기에 재시도", e);
        }
    }
}
