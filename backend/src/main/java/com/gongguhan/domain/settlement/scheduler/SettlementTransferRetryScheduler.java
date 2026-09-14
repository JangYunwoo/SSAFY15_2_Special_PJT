package com.gongguhan.domain.settlement.scheduler;

import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.service.SettlementTransferService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 실패한 정산 이체 재시도 (STL-07, FR-ADM-08). 1시간 주기.
 *
 * <p>이체 실패는 대부분 시간이 지나면 해소됩니다 — 플랫폼 계좌 잔액 부족, 금융망
 * 일시 장애 같은 것들입니다. 관리자가 매번 눌러야 하면 주말에 실패한 정산이 월요일까지
 * 밀립니다.
 *
 * <p><b>결과를 알 수 없는 건({@code TRANSFER_UNKNOWN})은 건드리지 않습니다.</b>
 * 요청은 나갔는데 응답을 못 받은 상태라 이체가 이미 일어났을 수 있고, 자동으로 다시
 * 보내면 판매자에게 두 번 지급됩니다. 사람이 금융망 거래내역을 확인한 뒤 STL-07 로
 * 수동 처리해야 합니다. 이 구분이 이 배치의 존재 이유이자 가장 중요한 제약입니다.
 *
 * <p>한 주기에 처리할 건수를 제한합니다. 실패가 쌓였을 때 한 번에 전부 보내면 금융망
 * 호출 한도에 걸리고, 같은 원인으로 전부 다시 실패할 가능성이 높습니다.
 *
 * <p><b>ShedLock 이 없습니다.</b> 인스턴스를 2대 이상으로 늘리면 같은 정산을 동시에
 * 두 번 보낼 수 있습니다 — 이체는 되돌릴 수 없으므로 확장 전에 반드시 넣어야 합니다.
 */
@Component
@Profile("!test")
public class SettlementTransferRetryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(SettlementTransferRetryScheduler.class);

    private final SettlementRepository settlementRepository;
    private final SettlementTransferService transferService;
    private final int batchSize;

    public SettlementTransferRetryScheduler(
            SettlementRepository settlementRepository,
            SettlementTransferService transferService,
            @org.springframework.beans.factory.annotation.Value(
                    "${app.jobs.settlement-retry.batch-size:20}") int batchSize) {
        this.settlementRepository = settlementRepository;
        this.transferService = transferService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.jobs.settlement-retry.cron:0 0 * * * *}")
    public void retryFailedTransfers() {
        List<Settlement> targets =
                settlementRepository.findAutoRetryTargets(PageRequest.of(0, batchSize));
        if (targets.isEmpty()) {
            return;
        }

        int succeeded = 0;
        for (Settlement settlement : targets) {
            try {
                Settlement result = transferService.execute(settlement.getSettlementId());
                if (result.isCompleted()) {
                    succeeded++;
                }
            } catch (Exception exception) {
                // 한 건의 실패가 나머지를 막지 않는다. 사유는 이미 정산 행에 기록돼 있다.
                log.warn("정산 이체 재시도 실패 settlementId={}",
                        settlement.getSettlementId(), exception);
            }
        }
        log.info("정산 이체 재시도 완료 대상={}건 성공={}건", targets.size(), succeeded);
    }
}
