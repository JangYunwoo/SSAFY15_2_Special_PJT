package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 Redis(docker-compose {@code redis:7-alpine}, localhost:6379)에 연결해 Lua 스크립트의
 * 원자성을 검증한다. Testcontainers를 쓰지 않으므로(이 저장소에 선례 없음) 실행 전
 * {@code docker compose up -d}로 Redis가 떠 있어야 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class GroupBuySlotGateIntegrationTest {

    private static final Long GROUP_BUY_ID = 777001L;
    private static final int THREAD_COUNT = 30;

    @Autowired
    private GroupBuySlotGate gate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanKey() {
        redisTemplate.delete("slot-gate:group-buy:" + GROUP_BUY_ID);
    }

    @Test
    void onlyTargetParticipantsGrantsSucceedUnderConcurrency() throws Exception {
        int target = 5;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Boolean>> tasks = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> (Callable<Boolean>) () -> {
                    ready.countDown();
                    start.await();
                    return gate.tryReserve(GROUP_BUY_ID, target, 0);
                })
                .collect(Collectors.toList());

        List<Future<Boolean>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        long grantedCount = futures.stream().map(f -> {
            try {
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).filter(Boolean::booleanValue).count();
        executor.shutdown();

        assertEquals(target, grantedCount, "target_participants만큼만 승인되어야 한다");
    }

    @Test
    void concurrentHydrationOnlyAppliesDbTruthOnce() throws Exception {
        int dbTruth = 10;
        int target = 100;
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger grants = new AtomicInteger();

        List<Callable<Void>> tasks = IntStream.range(0, THREAD_COUNT)
                .mapToObj(i -> (Callable<Void>) () -> {
                    ready.countDown();
                    start.await();
                    if (gate.tryReserve(GROUP_BUY_ID, target, dbTruth)) {
                        grants.incrementAndGet();
                    }
                    return null;
                })
                .collect(Collectors.toList());

        List<Future<Void>> futures = tasks.stream().map(executor::submit).collect(Collectors.toList());
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        String finalValue = redisTemplate.opsForValue().get("slot-gate:group-buy:" + GROUP_BUY_ID);
        assertEquals(dbTruth + grants.get(), Integer.parseInt(finalValue),
                "dbTruth는 단 한 번만 반영되어야 한다 (중복 hydration 없음)");
    }
}
