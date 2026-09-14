package com.gongguhan.domain.settlement.service;

import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository.SettlementTarget;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 정산 집계 배치 (FR-STL-01~05).
 *
 * <p>구매확정되고 정산 가능 시점이 지난 참여 건들을 공동구매별로 모아 지급 명세를
 * 만듭니다. <b>실제 계좌이체는 하지 않습니다</b> — 관리자가 STL-04 로 실행합니다.
 * 배치가 자동으로 돈을 보내면, 잘못 계산된 정산이 사람 확인 없이 나가고 회수가
 * 어렵습니다.
 *
 * <h2>왜 회차로 나누는가</h2>
 *
 * <p>같은 공동구매의 참여자들이 동시에 구매확정하지 않습니다. 배송 완료 시점이 다르고
 * ({@code settlement_eligible_at} = 배송완료 + 7일), 반품·분쟁으로 늦어지는 건도
 * 있습니다. 전원이 확정될 때까지 기다리면 판매자는 한 명 때문에 전체 대금을 못
 * 받습니다. 정산 가능한 건들만 모아 회차를 올려가며 나눠 지급합니다.
 *
 * <h2>트랜잭션 경계</h2>
 *
 * <p>이 클래스에는 트랜잭션이 없습니다. 공동구매 하나가 한 트랜잭션이며
 * {@link SettlementRecordService} 가 담당합니다. <b>다른 빈으로 뺀 이유는 자기
 * 호출이 프록시를 우회하기 때문</b>입니다 — 같은 클래스에 두면
 * {@code @Transactional} 이 걸리지 않습니다.
 *
 * <p>전체를 한 트랜잭션에 묶지 않는 이유는, 한 공동구매의 문제가 그날 정산 전체를
 * 막지 않게 하기 위해서입니다.
 */
@Service
public class SettlementBatchService {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchService.class);

    private final SettlementTargetRepository targetRepository;
    private final SettlementRecordService recordService;
    private final Clock clock;

    public SettlementBatchService(SettlementTargetRepository targetRepository,
                                  SettlementRecordService recordService,
                                  Clock clock) {
        this.targetRepository = targetRepository;
        this.recordService = recordService;
        this.clock = clock;
    }

    /**
     * 정산 대상을 모아 공동구매별로 정산을 만듭니다.
     *
     * @return 만들어진 정산 건수
     */
    public int run() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SettlementTarget> targets = targetRepository.findTargets(now);
        if (targets.isEmpty()) {
            return 0;
        }

        int created = 0;
        for (Map.Entry<Long, List<SettlementTarget>> entry : groupByGroupBuy(targets).entrySet()) {
            try {
                recordService.settle(entry.getKey(), entry.getValue(), now);
                created++;
            } catch (DataIntegrityViolationException exception) {
                // 다른 배치가 먼저 같은 건을 정산했다(UNIQUE(participation_id)).
                // 정상 경쟁이므로 오류가 아니라 정보로 남기고 다음으로 넘어간다.
                log.info("이미 정산된 참여 건이 있어 건너뜁니다. groupBuyId={}", entry.getKey());
            } catch (RuntimeException exception) {
                // 한 공동구매의 문제가 그날 정산 전체를 막지 않도록 격리한다.
                log.error("정산 생성 실패 groupBuyId={}", entry.getKey(), exception);
            }
        }
        log.info("정산 배치 완료 대상={}건 생성={}건", targets.size(), created);
        return created;
    }

    /** 삽입 순서를 유지해 공동구매 번호 순으로 처리합니다. 로그를 읽기 쉽게 하려는 것입니다. */
    private Map<Long, List<SettlementTarget>> groupByGroupBuy(List<SettlementTarget> targets) {
        Map<Long, List<SettlementTarget>> byGroupBuy = new LinkedHashMap<>();
        for (SettlementTarget target : targets) {
            byGroupBuy.computeIfAbsent(target.groupBuyId(), key -> new ArrayList<>()).add(target);
        }
        return byGroupBuy;
    }
}
