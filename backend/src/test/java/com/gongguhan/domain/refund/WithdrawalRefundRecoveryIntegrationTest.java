package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.service.GroupBuyWithdrawalService;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.participation.service.ParticipationCancelService;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.*;
import com.gongguhan.global.policy.PolicyService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:withdrawalrefundrecovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class WithdrawalRefundRecoveryIntegrationTest {
    @Autowired GroupBuyWithdrawalService withdrawal;
    @Autowired RefundRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ParticipationCancelService cancellation;
    @Autowired ParticipationRepository participations;
    @MockitoSpyBean GroupBuyRepository groups;
    @MockitoBean SsafyTransferClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;
    private static final AtomicLong IDS = new AtomicLong(993_000);

    @BeforeEach void provider() {
        when(policies.getString(any())).thenReturn("QA-NOT-LIVE");
        when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenReturn("QA-WITHDRAWAL");
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenAnswer(call -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return success(); });
    }

    @AfterEach void isolateTestLedgers() {
        // 이 클래스 전용 메모리 DB의 잔여 미송신 작업이 다음 테스트의 복구 스캔에 섞이지 않게 한다.
        jdbc.update("DELETE FROM seller_penalties");
        jdbc.update("DELETE FROM refunds");
        jdbc.update("DELETE FROM escrows");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM participations");
        jdbc.update("DELETE FROM group_buys");
        jdbc.update("DELETE FROM products");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM seller_profiles");
        jdbc.update("DELETE FROM users");
    }

    @Test void preparesEveryRefundBeforeAnyProviderCall() {
        long group = fixture();
        withdrawal.prepareWithdrawal(group, group, "QA withdrawal");
        assertThat(count(group, "PROCESSING")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN participations p ON p.participation_id=r.participation_id WHERE p.group_buy_id=? AND r.reason_type='SELLER_FAULT' AND r.execution_claimed=FALSE", Integer.class, group)).isEqualTo(2);
        verifyNoInteractions(bank);
    }

    @Test void failedFirstProviderDoesNotSkipAnotherBuyerOrRetryUnknown() {
        long group = fixture(); AtomicInteger calls = new AtomicInteger();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenAnswer(call -> { if (calls.incrementAndGet() == 1) throw new IllegalStateException("QA provider response lost"); return success(); });
        assertThatCode(() -> withdrawal.withdraw(group, group, "QA withdrawal")).doesNotThrowAnyException();
        assertThat(count(group, "PROCESSING")).isEqualTo(1);
        assertThat(count(group, "COMPLETED")).isEqualTo(1);
        recovery.run(); recovery.run();
        assertThat(calls.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN participations p ON p.participation_id=r.participation_id WHERE p.group_buy_id=?", Integer.class, group)).isEqualTo(2);
    }

    @Test void processStoppedAfterPreparationIsRecoveredWithoutAnyNewEvent() {
        long group = fixture();
        withdrawal.prepareWithdrawal(group, group, "QA stop before provider");
        ageUnsent(group);
        recovery.run(); recovery.run();
        assertThat(count(group, "COMPLETED")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=? AND status='REFUNDED'", Integer.class, group)).isEqualTo(2);
        verify(bank, times(2)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void preparationFailureRollsBackAllReservationsAndCancellation() {
        long group = fixture();
        jdbc.update("UPDATE payments SET status='FAILED' WHERE payment_id=?", group+2);
        assertThatThrownBy(() -> withdrawal.prepareWithdrawal(group, group, "QA invalid payment")).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM group_buys WHERE group_buy_id=?", String.class, group)).isEqualTo("RECRUITING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participations WHERE group_buy_id=? AND status='PAID'", Integer.class, group)).isEqualTo(2);
        assertThat(count(group, "PROCESSING")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_penalties WHERE group_buy_id=?", Integer.class, group)).isZero();
        verifyNoInteractions(bank);
    }

    @Test void recoveryRacingOriginalLoopStillTransfersOncePerBuyer() throws Exception {
        long group = fixture();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    if (calls.incrementAndGet() == 1) {
                        entered.countDown();
                        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                    }
                    return success();
                });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> original = executor.submit(() -> withdrawal.withdraw(group, group, "QA concurrency"));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            ageUnsent(group);
            recovery.run();
            release.countDown(); original.get(10, TimeUnit.SECONDS);
            recovery.run();
        } finally { release.countDown(); executor.shutdownNow(); }
        assertThat(count(group, "COMPLETED")).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void historicalCancellationWithoutLedgerRequiresExplicitReviewEvenWithCandidate() {
        long group = fixture();
        jdbc.update("UPDATE group_buys SET status='CANCELED' WHERE group_buy_id=?", group);
        jdbc.update("UPDATE participations SET status='REFUNDING' WHERE group_buy_id=?", group);
        jdbc.update("INSERT INTO seller_penalties(seller_id,group_buy_id,reason_type,detail,status,detected_at) VALUES(?,?,'WITHDRAWAL','QA historic','CANDIDATE',NOW())", group,group);
        recovery.run();
        assertThat(count(group, "PROCESSING")).isZero();
        assertThat(count(group, "COMPLETED")).isZero();
        verifyNoInteractions(bank);
    }

    @ParameterizedTest
    @CsvSource({"KAKAOPAY,KAKAOPAY", "CRYPTO,CRYPTO"})
    void preservesExistingPaymentMethodMappingWithoutProviderCall(String paymentMethod, String refundMethod) {
        long group = fixture();
        jdbc.update("UPDATE payments SET method=? WHERE group_buy_id=?", paymentMethod, group);
        withdrawal.prepareWithdrawal(group, group, "QA method mapping");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN participations p ON p.participation_id=r.participation_id WHERE p.group_buy_id=? AND r.refund_method=?", Integer.class, group, refundMethod)).isEqualTo(2);
        verifyNoInteractions(bank);
    }

    @Test void externalExecutionCannotJoinAnOuterTransaction() {
        long group = fixture();
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(tx -> {
            withdrawal.withdraw(group, group, "QA outer transaction"); return null;
        })).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM group_buys WHERE group_buy_id=?", String.class, group)).isEqualTo("RECRUITING");
        verifyNoInteractions(bank);
    }

    @Test void individualCancellationFirstCannotDeadlockWithdrawalOrChangeItsRefundReason() throws Exception {
        long group = fixture();
        CountDownLatch participantLocked = new CountDownLatch(1), cancelNow = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> individual = executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                participations.findWithLockByParticipationId(group + 1).orElseThrow();
                participantLocked.countDown(); await(cancelNow);
                cancellation.prepareCancel(group + 1, group + 1, "1234567890123456", "123");
                return null;
            }));
            assertThat(participantLocked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> seller = executor.submit(() -> withdrawal.withdraw(group, group, "QA concurrent cancellation"));
            assertThatThrownBy(() -> seller.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            cancelNow.countDown();
            individual.get(10, TimeUnit.SECONDS); seller.get(10, TimeUnit.SECONDS);
        } finally { cancelNow.countDown(); executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT reason_type FROM refunds WHERE participation_id=?", String.class, group+1)).isEqualTo("PARTICIPATION_CANCEL");
        assertThat(jdbc.queryForObject("SELECT reason_type FROM refunds WHERE participation_id=?", String.class, group+2)).isEqualTo("SELLER_FAULT");
        assertThat(count(group, "COMPLETED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM group_buys WHERE group_buy_id=?", String.class, group)).isEqualTo("CANCELED");
        verify(bank, times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void participantCommittedBetweenSnapshotAndGroupLockIsIncludedAfterFreshTransactionRetry() throws Exception {
        long group = fixture();
        jdbc.update("UPDATE group_buys SET target_participants=5 WHERE group_buy_id=?", group);
        CountDownLatch beforeGroupLock = new CountDownLatch(1), commitNewBuyer = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var repositoryDelegate = mockingDetails(groups).getMockCreationSettings().getDefaultAnswer();
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("withdrawal-snapshot") && attempts.incrementAndGet() == 1) {
                beforeGroupLock.countDown(); await(commitNewBuyer);
            }
            return repositoryDelegate.answer(call);
        }).when(groups).findWithLockByGroupBuyId(group);
        ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "withdrawal-snapshot"));
        try {
            Future<?> seller = executor.submit(() -> withdrawal.withdraw(group, group, "QA concurrent new buyer"));
            assertThat(beforeGroupLock.await(10, TimeUnit.SECONDS)).isTrue();
            verifyNoInteractions(bank);
            new TransactionTemplate(manager).execute(tx -> {
                var current = groups.findWithLockByGroupBuyId(group).orElseThrow();
                current.reserveSlot(); current.confirmParticipant();
                long buyer = group+3;
                jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,FALSE,'ACTIVE','QA-KEY',?,NOW(),NOW())",buyer,UUID.randomUUID()+"@example.test","QA"+buyer);
                insertBuyer(group, buyer, LocalDateTime.now(ZoneOffset.UTC));
                return null;
            });
            commitNewBuyer.countDown(); seller.get(10, TimeUnit.SECONDS);
        } finally { commitNewBuyer.countDown(); executor.shutdownNow(); }
        verify(groups, times(2)).findVersionByGroupBuyId(group);
        assertThat(count(group, "COMPLETED")).isEqualTo(3);
        verify(bank, times(3)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private void ageUnsent(long group) {
        jdbc.update("UPDATE refunds SET created_at=? WHERE participation_id IN (SELECT participation_id FROM participations WHERE group_buy_id=?)", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2), group);
    }

    private int count(long group, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN participations p ON p.participation_id=r.participation_id WHERE p.group_buy_id=? AND r.status=?", Integer.class, group, status);
    }
    private long fixture() {
        long group = IDS.addAndGet(10); LocalDateTime then = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        for (long id : new long[]{group,group+1,group+2}) {
            jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE','QA-KEY',?,NOW(),NOW())",id,UUID.randomUUID()+"@example.test","QA"+id);
        }
        jdbc.update("INSERT INTO seller_profiles(seller_id,business_name,status,created_at,updated_at) VALUES(?,'QA','ACTIVE',NOW(),NOW())",group);
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)",group);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())",group,group,group);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,3,1000,0,2,0,?,'RECRUITING',0,NOW(),NOW())",group,group,group,then.plusDays(2));
        for (long id : new long[]{group+1,group+2}) {
            insertBuyer(group, id, then);
        }
        return group;
    }
    private void insertBuyer(long group, long id, LocalDateTime then) {
            jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA','PAID',?,FALSE,FALSE,'Y')",id,group,id,then);
            jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,participation_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',1000,?,?,'PAID',NOW(),NOW())",id,group,id,id,then,UUID.randomUUID().toString());
            jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,1000,0,'HELD',NOW())",id,group);
    }
    private SsafyTransferClient.TransferResponse success() {
        return new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H0000","QA","QA-WITHDRAWAL"),
                List.of(new SsafyTransferClient.TransferResponse.Record("1","20260921","1","QA-ACCOUNT")));
    }
}
