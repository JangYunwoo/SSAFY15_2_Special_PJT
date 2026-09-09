package com.gongguhan.domain.payment.common.scheduler;

import com.gongguhan.domain.payment.common.service.PaymentService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * API 명세서 18장: 결제 슬롯 만료 - 매 1분, payments.status/slot_expires_at 조건부 갱신.
 * PROCESSING 상태로 방치된 결제(사용자 이탈 등)의 슬롯을 해제해 group_buys.reserved_slots
 * 정합성을 보정한다. 다른 배치보다 주기가 짧아(1분) 테스트 스위트 실행 중 우연히
 * 걸릴 위험이 커서, 테스트에서는 명시적으로 비활성화한다.
 */
@Component
@Profile("!test")
public class PaymentSlotExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSlotExpiryScheduler.class);

    private final PaymentService paymentService;

    public PaymentSlotExpiryScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(cron = "${app.jobs.payment-slot-expiry.cron:0 * * * * *}")
    public void expireOverdueSlots() {
        List<Long> candidates = paymentService.findExpiredSlotPaymentIds();
        for (Long paymentId : candidates) {
            try {
                paymentService.expireSlotIfDue(paymentId);
            } catch (Exception e) {
                log.warn("결제 슬롯 만료 처리 실패: paymentId={}", paymentId, e);
            }
        }
    }
}
