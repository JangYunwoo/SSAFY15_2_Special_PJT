package com.gongguhan.domain.delivery.service;

import static org.assertj.core.api.Assertions.*;

import com.gongguhan.domain.participation.dto.DisputeRequest;
import com.gongguhan.domain.participation.service.ParticipationDisputeService;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:deliverydisputerace;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class DeliveryDisputeConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DeliveryService deliveries;
    @Autowired ParticipationDisputeService disputes;
    @Autowired PlatformTransactionManager manager;
    private static final AtomicLong IDS = new AtomicLong(991_000);

    @Test void disputeFirstKeepsHoldAfterSingleDeliveryCompletion() throws Exception { disputeFirst(false); }
    @Test void disputeFirstKeepsHoldAfterBulkDeliveryCompletion() throws Exception { disputeFirst(true); }

    private void disputeFirst(boolean bulk) throws Exception {
        long id = fixture();
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> disputing = executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                disputes.submitDispute(id, id, new DisputeRequest("NOT_DELIVERED", "QA concurrent dispute"));
                // 다른 트랜잭션의 일반 조회는 아직 false를 보도록 커밋 전 대기한다.
                written.countDown(); await(release); return null;
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            CountDownLatch attempted = new CountDownLatch(1);
            Future<?> delivering = executor.submit(() -> {
                attempted.countDown();
                if (bulk) return deliveries.bulkUpdateStatus(id, id, "DELIVERED");
                return deliveries.updateStatus(id, id, "DELIVERED");
            });
            assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> delivering.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            disputing.get(10, TimeUnit.SECONDS);
            delivering.get(10, TimeUnit.SECONDS);
            assertDeliveredAndHeld(id);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void deliveryFirstAllowsDisputeAfterCompletionWithoutLosingStatus() throws Exception {
        long id = fixture();
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> delivering = executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                deliveries.updateStatus(id, id, "DELIVERED");
                written.countDown(); await(release); return null;
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> disputing = executor.submit(() -> disputes.submitDispute(
                    id, id, new DisputeRequest("NOT_DELIVERED", "QA concurrent dispute")));
            assertThatThrownBy(() -> disputing.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            delivering.get(10, TimeUnit.SECONDS);
            disputing.get(10, TimeUnit.SECONDS);
            assertDeliveredAndHeld(id);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void assertDeliveredAndHeld(long id) {
        assertThat(jdbc.queryForObject("SELECT status FROM participations WHERE participation_id=?", String.class, id)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT status FROM deliveries WHERE delivery_id=?", String.class, id)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT confirm_hold FROM participations WHERE participation_id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_penalties WHERE group_buy_id=?", Integer.class, id)).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for transaction release"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private long fixture() {
        long id = IDS.incrementAndGet();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("MERGE INTO platform_policies(policy_key,policy_value) KEY(policy_key) VALUES('WITHDRAWAL_PERIOD_DAYS','7')");
        jdbc.update("MERGE INTO platform_policies(policy_key,policy_value) KEY(policy_key) VALUES('SETTLEMENT_ELIGIBLE_DAYS','7')");
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE',NOW(),NOW())", id, UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)", id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())", id,id,id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,2,1000,0,2,0,?,'FULFILLED',0,NOW(),NOW())", id,id,id,now.minusDays(2));
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA','SHIPPING',?,FALSE,FALSE,'Y')",id,id,id,now.minusDays(2));
        jdbc.update("INSERT INTO deliveries(delivery_id,participation_id,courier_code,tracking_number,shipped_at,status) VALUES(?,?,'QA',?,?,'SHIPPING')",id,id,"QA"+id,now.minusDays(1));
        return id;
    }
}
