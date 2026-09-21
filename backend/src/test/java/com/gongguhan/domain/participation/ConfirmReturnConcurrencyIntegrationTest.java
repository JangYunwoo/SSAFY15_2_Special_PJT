package com.gongguhan.domain.participation;

import static org.assertj.core.api.Assertions.*;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.participation.service.PurchaseConfirmService;
import com.gongguhan.domain.returnrequest.dto.ReturnCreateRequest;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.service.ReturnRequestService;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.service.SettlementRecordService;
import com.gongguhan.global.error.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:confirmreturnrace;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class ConfirmReturnConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PurchaseConfirmService confirm;
    @Autowired ReturnRequestService returns;
    @Autowired ReturnRequestRepository returnRepository;
    @Autowired ParticipationRepository participations;
    @Autowired SettlementTargetRepository targets;
    @Autowired SettlementRecordService settlements;
    @Autowired PlatformTransactionManager manager;
    static final AtomicLong IDS=new AtomicLong(990_000);

    @Test void returnCommitPreventsConcurrentManualConfirmation() throws Exception { returnFirst(false); }
    @Test void returnCommitPreventsConcurrentAutomaticConfirmation() throws Exception { returnFirst(true); }

    private void returnFirst(boolean automatic) throws Exception {
        long id=fixture("DELIVERED",false);
        CountDownLatch written=new CountDownLatch(1), release=new CountDownLatch(1);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<?> creating=executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                returns.create(id,id,request());
                written.countDown();
                await(release);
                return null;
            }));
            assertThat(written.await(10,TimeUnit.SECONDS)).isTrue();
            CountDownLatch attempted=new CountDownLatch(1);
            Future<Boolean> confirming=executor.submit(() -> {
                attempted.countDown();
                try { if(automatic) confirm.autoConfirmIfDue(id); else confirm.confirmManually(id,id,true); return true; }
                catch(BusinessException rejected) { return false; }
            });
            assertThat(attempted.await(10,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> confirming.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            creating.get(10,TimeUnit.SECONDS);
            assertThat(confirming.get(10,TimeUnit.SECONDS)).isEqualTo(automatic);
            assertThat(status(id)).isEqualTo("DELIVERED");
            assertThat(jdbc.queryForObject("SELECT confirm_hold FROM participations WHERE participation_id=?",Boolean.class,id)).isTrue();
        } finally {release.countDown();executor.shutdownNow();}
    }

    @Test void confirmationFirstMakesWaitingReturnRequireAdminReview() throws Exception {
        long id=fixture("DELIVERED",false);
        CountDownLatch written=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<?> confirming=executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                confirm.confirmManually(id,id,true);written.countDown();await(release);return null;
            }));
            assertThat(written.await(10,TimeUnit.SECONDS)).isTrue();
            Future<?> creating=executor.submit(() -> returns.create(id,id,request()));
            assertThatThrownBy(() -> creating.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();confirming.get(10,TimeUnit.SECONDS);creating.get(10,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT admin_review_required FROM return_requests WHERE participation_id=?",Boolean.class,id)).isTrue();
            assertThat(jdbc.queryForObject("SELECT post_confirm FROM return_requests WHERE participation_id=?",Boolean.class,id)).isTrue();
        } finally {release.countDown();executor.shutdownNow();}
    }

    @Test void settlementQueryExcludesHeldConfirmation() {
        long id=fixture("CONFIRMED",true);
        jdbc.update("UPDATE participations SET confirm_hold=TRUE WHERE participation_id=?",id);
        assertThat(targets.findTargets(LocalDateTime.now())).noneMatch(t->t.participationId().equals(id));
    }

    @Test void staleSettlementCandidateIsRejectedAfterReturnHold() {
        long id=fixture("CONFIRMED",true);
        var snapshot=targets.findTargets(LocalDateTime.now()).stream().filter(t->t.participationId().equals(id)).toList();
        assertThat(snapshot).hasSize(1);
        new TransactionTemplate(manager).execute(tx -> {
            participations.findWithLockByParticipationId(id).orElseThrow().holdConfirmation();return null;
        });
        assertThatThrownBy(() -> settlements.settle(id,snapshot,LocalDateTime.now())).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE group_buy_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM escrows WHERE participation_id=?",String.class,id)).isEqualTo("HELD");
    }

    @Test void staleAmountCannotBeUsedToOverpaySeller() {
        long id=fixture("CONFIRMED",true);
        var snapshot=targets.findTargets(LocalDateTime.now()).stream().filter(t->t.participationId().equals(id)).toList();
        jdbc.update("UPDATE escrows SET refunded_amount=100 WHERE participation_id=?",id);
        assertThatThrownBy(() -> settlements.settle(id,snapshot,LocalDateTime.now())).isInstanceOf(BusinessException.class);
        assertThat(status(id)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlements WHERE group_buy_id=?",Integer.class,id)).isZero();
    }

    private ReturnCreateRequest request() { return new ReturnCreateRequest("DEFECT","QA","00000","QA",null); }
    private void await(CountDownLatch latch) { try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("timeout");} catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);} }
    private String status(long id){return jdbc.queryForObject("SELECT status FROM participations WHERE participation_id=?",String.class,id);}
    private long fixture(String status, boolean eligible) {
        long id=IDS.incrementAndGet();LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE',NOW(),NOW())",id,UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)",id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())",id,id,id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,2,1000,0,2,0,?,'FULFILLED',0,NOW(),NOW())",id,id,id,now.minusDays(20));
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,delivered_at,withdrawal_period_end_at,settlement_eligible_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA',?,?,?,?,?,FALSE,FALSE,'Y')",id,id,id,status,now.minusDays(20),now.minusDays(15),now.plusDays(5),eligible?now.minusDays(1):now.plusDays(5));
        jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,1000,0,'HELD',NOW())",id,id);
        return id;
    }
}
