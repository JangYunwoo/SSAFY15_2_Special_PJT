package com.gongguhan.domain.settlement.scheduler;

import com.gongguhan.domain.settlement.service.SettlementBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정산 집계 배치 (FR-STL-01~05). 매일 03:00.
 *
 * <p>새벽에 도는 이유는 정산 가능 시점이 날짜 단위({@code 배송완료 + 7일})라 하루에
 * 한 번이면 충분하고, 낮에 돌면 조회·결제와 DB 를 다투기 때문입니다.
 *
 * <p><b>돈을 보내지는 않습니다.</b> 지급 명세만 만들고, 실제 계좌이체는 관리자가
 * STL-04 로 실행합니다.
 *
 * <p>{@code AutoConfirmScheduler} 와 같은 패턴입니다 — 후보를 모아 건별로 처리하고
 * 한 건의 실패가 나머지를 막지 않습니다. 실패 격리는
 * {@link SettlementBatchService#run()} 안에서 합니다.
 *
 * <p><b>ShedLock 이 없습니다.</b> 인스턴스를 2대 이상으로 늘리면 같은 시각에 두 번
 * 돕니다. 이중 정산은 {@code settlement_items.UNIQUE(participation_id)} 가 막지만,
 * 실패 로그가 쌓이므로 확장 전에 ShedLock 을 넣어야 합니다.
 */
@Component
@Profile("!test")
public class SettlementBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchScheduler.class);

    private final SettlementBatchService settlementBatchService;

    public SettlementBatchScheduler(SettlementBatchService settlementBatchService) {
        this.settlementBatchService = settlementBatchService;
    }

    @Scheduled(cron = "${app.jobs.settlement.cron:0 0 3 * * *}")
    public void aggregateSettlements() {
        try {
            settlementBatchService.run();
        } catch (Exception exception) {
            // 배치 전체가 죽으면 다음 날까지 정산이 멈춘다. 예외를 삼키고 로그로 남긴다.
            log.error("정산 배치 실행 실패", exception);
        }
    }
}
