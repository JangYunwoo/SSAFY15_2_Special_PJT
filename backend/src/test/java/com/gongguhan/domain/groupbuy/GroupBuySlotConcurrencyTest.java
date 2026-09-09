package com.gongguhan.domain.groupbuy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GroupBuySlotConcurrencyTest {

    private static final long GROUP_BUY_ID = 910_001L;
    private static final int THREAD_COUNT = 10;

    @Autowired
    private GroupBuySlotService groupBuySlotService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM group_buys WHERE group_buy_id = ?", GROUP_BUY_ID);
    }

    @Test
    void concurrentReservationsNeverExceedTargetParticipants() throws Exception {
        insertGroupBuy(1, 0, 0);
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, THREAD_COUNT)
                    .mapToObj(index -> (Callable<Boolean>) () -> reserve(ready, start))
                    .toList();
            List<Future<Boolean>> futures = tasks.stream().map(executor::submit).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            long successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertEquals(1, successCount);
            assertEquals(1, slotCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void releaseAndConfirmKeepSlotCountsConsistent() {
        insertGroupBuy(2, 0, 1);

        groupBuySlotService.release(GROUP_BUY_ID);
        assertEquals(0, slotCount());

        groupBuySlotService.reserve(GROUP_BUY_ID);
        groupBuySlotService.confirm(GROUP_BUY_ID);

        Integer currentParticipants = jdbcTemplate.queryForObject(
                "SELECT current_participants FROM group_buys WHERE group_buy_id = ?",
                Integer.class,
                GROUP_BUY_ID);
        assertEquals(1, currentParticipants);
        assertEquals(1, slotCount());
    }

    @Test
    void nonRecruitingGroupBuyCannotReserveSlot() {
        insertGroupBuy(2, 0, 0, "FULFILLED");

        BusinessException exception = assertThrows(
                BusinessException.class, () -> groupBuySlotService.reserve(GROUP_BUY_ID));

        assertEquals(ErrorCode.GROUP_BUY_NOT_RECRUITING, exception.getErrorCode());
        assertEquals(0, slotCount());
    }

    @Test
    void emptyReservationCannotBeReleasedOrConfirmed() {
        insertGroupBuy(2, 0, 0);

        assertThrows(IllegalStateException.class, () -> groupBuySlotService.release(GROUP_BUY_ID));
        assertThrows(IllegalStateException.class, () -> groupBuySlotService.confirm(GROUP_BUY_ID));

        assertEquals(0, slotCount());
    }

    private boolean reserve(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            groupBuySlotService.reserve(GROUP_BUY_ID);
            return true;
        } catch (BusinessException exception) {
            assertEquals(ErrorCode.GROUP_BUY_FULL, exception.getErrorCode());
            return false;
        }
    }

    private void insertGroupBuy(int targetParticipants, int currentParticipants, int reservedSlots) {
        insertGroupBuy(targetParticipants, currentParticipants, reservedSlots, "RECRUITING");
    }

    private void insertGroupBuy(
            int targetParticipants, int currentParticipants, int reservedSlots, String status) {
        jdbcTemplate.update("""
                INSERT INTO group_buys (
                    group_buy_id, product_id, seller_id, target_participants,
                    unit_share_amount, shipping_fee, current_participants, reserved_slots,
                    recruit_end_at, status, version, created_at, updated_at
                ) VALUES (?, 1, 1, ?, 50000, 4000, ?, ?, ?, ?, 0, NOW(), NOW())
                """,
                GROUP_BUY_ID,
                targetParticipants,
                currentParticipants,
                reservedSlots,
                LocalDateTime.now().plusDays(1),
                status);
    }

    private int slotCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT current_participants + reserved_slots FROM group_buys WHERE group_buy_id = ?",
                Integer.class,
                GROUP_BUY_ID);
        return count == null ? 0 : count;
    }
}
