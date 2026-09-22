package com.gongguhan.domain.penalty;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.event.GroupBuyWithdrawnEvent;
import com.gongguhan.domain.groupbuy.service.GroupBuyWithdrawalService;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:withdrawalpenalty;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class WithdrawalPenaltyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GroupBuyWithdrawalService withdrawal;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean RefundOrchestrationService refunds;
    private static final AtomicLong IDS = new AtomicLong(992_000);

    @Test void prepareCreatesCandidateInSameCommitWithoutInvokingFinance() {
        long id = fixture();
        withdrawal.prepareWithdrawal(id, id, "재고 부족");
        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(candidateCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM seller_penalties WHERE group_buy_id=?", String.class, id)).isEqualTo("CANDIDATE");
        assertThat(jdbc.queryForObject("SELECT detail FROM seller_penalties WHERE group_buy_id=?", String.class, id)).contains("재고 부족");
        assertThat(jdbc.queryForObject("SELECT penalty_count FROM seller_profiles WHERE seller_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM seller_profiles WHERE seller_id=?", String.class, id)).isEqualTo("ACTIVE");
        verifyNoInteractions(refunds);
    }

    @Test void externalRefundFailureDoesNotEraseCommittedCandidate() {
        long id = fixture();
        when(refunds.resumePersisted(anyLong())).thenThrow(new IllegalStateException("QA refund unavailable"));
        assertThatCode(() -> withdrawal.withdraw(id, id, "재고 부족")).doesNotThrowAnyException();
        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(candidateCount(id)).isEqualTo(1);
        verify(refunds, times(1)).resumePersisted(anyLong());
    }

    @Test void rollbackRemovesCancellationAndCandidateTogether() {
        long id = fixture();
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(tx -> {
            withdrawal.prepareWithdrawal(id, id, "QA rollback");
            throw new IllegalStateException("QA rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(status(id)).isEqualTo("RECRUITING");
        assertThat(candidateCount(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM participations WHERE participation_id=?", String.class, id)).isEqualTo("PAID");
    }

    @Test void repeatedEventDoesNotDuplicateCandidateAndLongReasonFitsStorage() {
        long id = fixture();
        withdrawal.prepareWithdrawal(id, id, "긴사유".repeat(300));
        new TransactionTemplate(manager).execute(tx -> {
            events.publishEvent(new GroupBuyWithdrawnEvent(id, id, 1, "replay", LocalDateTime.now()));
            return null;
        });
        assertThat(candidateCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detail FROM seller_penalties WHERE group_buy_id=?", String.class, id)).hasSize(500);
    }

    @Test void concurrentWithdrawalCannotCreateTwoCandidates() throws Exception {
        long id = fixture();
        CountDownLatch prepared = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                withdrawal.prepareWithdrawal(id, id, "first");
                prepared.countDown(); await(release); return null;
            }));
            assertThat(prepared.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> withdrawal.prepareWithdrawal(id, id, "second"));
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); first.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(candidateCount(id)).isEqualTo(1);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("transaction timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }
    private String status(long id) { return jdbc.queryForObject("SELECT status FROM group_buys WHERE group_buy_id=?", String.class, id); }
    private int candidateCount(long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM seller_penalties WHERE group_buy_id=? AND reason_type='WITHDRAWAL'", Integer.class, id); }
    private long fixture() {
        long id = IDS.incrementAndGet(); LocalDateTime then = LocalDateTime.now().minusHours(1);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE',NOW(),NOW())", id,UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO seller_profiles(seller_id,business_name,status,created_at,updated_at) VALUES(?,'QA','ACTIVE',NOW(),NOW())",id);
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)", id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())",id,id,id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,2,1000,0,1,0,?,'RECRUITING',0,NOW(),NOW())",id,id,id,then.plusDays(2));
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA','PAID',?,FALSE,FALSE,'Y')",id,id,id,then);
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,participation_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',1000,?,?,'PAID',NOW(),NOW())",id,id,id,id,then,UUID.randomUUID().toString());
        return id;
    }
}
