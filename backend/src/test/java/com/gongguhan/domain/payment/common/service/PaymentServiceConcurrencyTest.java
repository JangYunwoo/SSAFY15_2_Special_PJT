package com.gongguhan.domain.payment.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * S15P21B205-70 검증 항목: 동시 요청 부하 테스트에서 ck_group_buys_slots(정원 초과) 위반이
 * 없어야 한다. target_participants=1인 공동구매에 N명이 동시에 슬롯 선점을 시도해 정확히
 * 1건만 성공하는지 실제 DB(H2) 위에서 확인한다.
 *
 * <p>이 테스트는 DB의 최종 정합성을 검증하므로 외부 Redis에 의존하지 않는 로컬 원자
 * 게이트를 사용한다. 실제 Redis Lua 원자성은 GroupBuySlotGateIntegrationTest에서
 * Redis가 실행 중일 때 별도로 검증한다.
 */
@SpringBootTest(properties = "app.slot-gate.local-fallback-enabled=true")
@ActiveProfiles("test")
class PaymentServiceConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final long GROUP_BUY_ID = 900001L;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void onlyOneConcurrentPrepareSucceedsForSingleSlotGroupBuy() throws InterruptedException {
        long groupBuyId = GROUP_BUY_ID;
        jdbcTemplate.update("""
                INSERT INTO group_buys
                  (group_buy_id, product_id, seller_id, target_participants, unit_share_amount,
                   shipping_fee, current_participants, reserved_slots, recruit_start_at, recruit_end_at, status,
                   version, created_at, updated_at)
                VALUES (?, 1, 1, 1, 50000, 4000, 0, 0, ?, ?, 'RECRUITING', 0, NOW(), NOW())
                """, groupBuyId, LocalDateTime.now(clock).minusDays(1), LocalDateTime.now(clock).plusDays(1));

        for (int i = 0; i < THREAD_COUNT; i++) {
            long userId = 900100L + i;
            long addressId = 900200L + i;
            jdbcTemplate.update("""
                    INSERT INTO user_addresses
                      (address_id, user_id, recipient_name, recipient_phone, zipcode, address1,
                       is_default, created_at, updated_at)
                    VALUES (?, ?, 'tester', '01000000000', '06236', 'seoul', true, NOW(), NOW())
                    """, addressId, userId);
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Boolean>> tasks = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> (Callable<Boolean>) () -> {
                    long userId = 900100L + i;
                    long addressId = 900200L + i;
                    ready.countDown();
                    start.await();
                    try {
                        paymentService.prepare(
                                userId, groupBuyId,
                                new PaymentPrepareRequest("CARD", addressId, null),
                                "concurrency-test-key-" + i);
                        return true;
                    } catch (BusinessException e) {
                        assertEquals(ErrorCode.GROUP_BUY_FULL, e.getErrorCode());
                        return false;
                    }
                })
                .collect(Collectors.toList());

        List<Future<Boolean>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        long successCount = futures.stream().map(f -> {
            try {
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).filter(Boolean::booleanValue).count();
        executor.shutdown();

        assertEquals(1, successCount, "target_participants=1인 공동구매는 정확히 1건만 성공해야 한다");

        Integer reservedPlusCurrent = jdbcTemplate.queryForObject(
                "SELECT reserved_slots + current_participants FROM group_buys WHERE group_buy_id = ?",
                Integer.class, groupBuyId);
        assertTrue(reservedPlusCurrent <= 1, "ck_group_buys_slots 위반: 정원을 초과해 예약되었다");
    }
}
