package com.gongguhan.domain.payment.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:payment-transition;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class PaymentTransitionIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GroupBuySlotPort slots;
    @MockitoBean GroupBuySlotGate gate;
    private static final AtomicInteger IDS = new AtomicInteger(981000);

    @Test void twoAbortsReleaseOnlyTheOwnedSlot() throws Exception { race(false); }
    @Test void abortAndFailureReleaseOnlyOnce() throws Exception { race(true); }

    private void race(boolean failSecond) throws Exception {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,
                slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at)
                VALUES(?,?,10,10,'CARD',1000,?,'Y',?,'PROCESSING',NOW(),NOW())
                """, id, id, LocalDateTime.now().plusDays(1), UUID.randomUUID().toString());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("test release timeout");
            }
            return null;
        }).when(slots).release(anyLong());
        boolean secondWaited = false;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> payments.abort(10L, id));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                try {
                    if (failSecond) payments.failPayment(id, "TEST_FAILED");
                    else payments.abort(10L, id);
                } catch (BusinessException expected) {
                    assertThat(expected.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);
                }
            });
            try { second.get(250, TimeUnit.MILLISECONDS); }
            catch (TimeoutException expected) { secondWaited = true; }
            finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
        } finally { release.countDown(); }
        assertThat(secondWaited).as("같은 결제의 미커밋 종료를 기다려야 한다").isTrue();
        assertThat(calls.get()).isEqualTo(1);
        verify(gate, times(1)).release(id);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?", String.class, id)).isEqualTo("FAILED");
    }
}
