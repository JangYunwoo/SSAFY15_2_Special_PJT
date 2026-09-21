package com.gongguhan.domain.refund.scheduler;

import com.gongguhan.domain.refund.service.RefundRecoveryService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RefundRecoveryScheduler {
    private final RefundRecoveryService recovery;
    public RefundRecoveryScheduler(RefundRecoveryService recovery){this.recovery=recovery;}

    @Scheduled(fixedDelayString="${app.jobs.refund-recovery.delay-ms:60000}")
    public void recover(){recovery.run();}

}
