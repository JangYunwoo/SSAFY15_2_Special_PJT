package com.gongguhan.domain.penalty.service;

import static org.assertj.core.api.Assertions.*;

import com.gongguhan.domain.account.dto.SellerProfileUpdateRequest;
import com.gongguhan.domain.account.service.SellerProfileService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
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

/** Separate real transactions: the first writer has changed state but has not committed. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:penaltyrace;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class PenaltyTransitionConcurrencyIntegrationTest {
    @Autowired PenaltyService penalties;
    @Autowired SellerProfileService profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private static final AtomicLong IDS = new AtomicLong(995_000);

    @Test void differentConfirmationsReachThreeStrikesWithoutLosingIncrement() throws Exception {
        long id = fixture(1);
        long a = candidate(id), b = candidate(id);
        race(() -> penalties.confirm(id, a, "QA first"), () -> penalties.confirm(id, b, "QA second"), false);
        assertSeller(id, 3, "SUSPENDED");
    }

    @Test void differentRevocationsDoNotLoseDecrement() throws Exception {
        long id = fixture(3);
        var ids = jdbc.queryForList("SELECT penalty_id FROM seller_penalties WHERE seller_id=? ORDER BY penalty_id", Long.class, id);
        race(() -> penalties.revoke(id, ids.get(0), "QA first"), () -> penalties.revoke(id, ids.get(1), "QA second"), false);
        assertSeller(id, 1, "ACTIVE");
    }

    @Test void confirmThenDismissCannotOverwriteConfirmedState() throws Exception {
        long id = fixture(0), penalty = candidate(id);
        race(() -> penalties.confirm(id, penalty, "QA confirm"), () -> penalties.dismiss(id, penalty, "QA stale dismiss"), true);
        assertSeller(id, 1, "ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM seller_penalties WHERE penalty_id=?", String.class, penalty)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_type='SELLER_PENALTY' AND target_id=?", Integer.class, penalty)).isEqualTo(1);
    }

    @Test void sameRevocationOnlySucceedsOnce() throws Exception {
        long id = fixture(1);
        long penalty = jdbc.queryForObject("SELECT penalty_id FROM seller_penalties WHERE seller_id=?", Long.class, id);
        race(() -> penalties.revoke(id, penalty, "QA first"), () -> penalties.revoke(id, penalty, "QA duplicate"), true);
        assertSeller(id, 0, "ACTIVE");
    }

    @Test void profileEditCannotEraseConcurrentSuspension() throws Exception {
        long id = fixture(2), penalty = candidate(id);
        race(() -> penalties.confirm(id, penalty, "QA confirm"), () -> editProfile(id), false);
        assertSeller(id, 3, "SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT business_phone FROM seller_profiles WHERE seller_id=?", String.class, id)).isEqualTo("01012345678");
    }

    @Test void confirmationCannotEraseConcurrentProfileEdit() throws Exception {
        long id = fixture(2), penalty = candidate(id);
        race(() -> { editProfile(id); jdbc.queryForObject("SELECT 1", Integer.class); }, () -> penalties.confirm(id, penalty, "QA confirm"), false);
        assertSeller(id, 3, "SUSPENDED");
        assertThat(jdbc.queryForObject("SELECT business_phone FROM seller_profiles WHERE seller_id=?", String.class, id)).isEqualTo("01012345678");
    }

    private void editProfile(long id) {
        profiles.updateProfile(id, new SellerProfileUpdateRequest("01012345678", null, null, null, null, null));
    }

    private void race(Runnable first, Runnable second, boolean rejectSecond) throws Exception {
        CountDownLatch written = new CountDownLatch(1), release = new CountDownLatch(1), attempted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstRun = executor.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                first.run();
                // Flush all managed state, including the profile-edit-first case, before releasing the contender.
                entityManager.flush();
                written.countDown(); await(release); return null;
            }));
            assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> secondRun = executor.submit(() -> { attempted.countDown(); second.run(); });
            assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> secondRun.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); firstRun.get(10, TimeUnit.SECONDS);
            if (rejectSecond) {
                ExecutionException failure = catchThrowableOfType(ExecutionException.class, () -> secondRun.get(10, TimeUnit.SECONDS));
                assertThat(failure).isNotNull();
                assertThat(failure.getCause()).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) failure.getCause()).getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
            } else secondRun.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("transaction release timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    private void assertSeller(long id, int count, String status) {
        assertThat(jdbc.queryForObject("SELECT penalty_count FROM seller_profiles WHERE seller_id=?", Integer.class, id)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_penalties WHERE seller_id=? AND status='CONFIRMED'", Integer.class, id)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT status FROM seller_profiles WHERE seller_id=?", String.class, id)).isEqualTo(status);
    }

    private long fixture(int confirmed) {
        long id = IDS.incrementAndGet();
        jdbc.update("MERGE INTO platform_policies(policy_key,policy_value) KEY(policy_key) VALUES('PENALTY_LIMIT','3')");
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE',NOW(),NOW())", id, UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO seller_profiles(seller_id,business_name,penalty_count,status,verified_at,created_at,updated_at) VALUES(?,'QA',?,?,NOW(),NOW(),NOW())", id, confirmed, confirmed >= 3 ? "SUSPENDED" : "ACTIVE");
        for (int i=0; i<confirmed; i++) {
            long penalty = candidate(id);
            jdbc.update("UPDATE seller_penalties SET status='CONFIRMED' WHERE penalty_id=?", penalty);
        }
        return id;
    }

    private long candidate(long seller) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO seller_penalties(penalty_id,seller_id,reason_type,status,detected_at) VALUES(?,?,'NO_INVOICE','CANDIDATE',NOW())", id, seller);
        return id;
    }
}
