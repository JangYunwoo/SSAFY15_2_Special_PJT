package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository;
import com.gongguhan.domain.returnrequest.repository.ReturnRequestRepository.CreateCommand;
import com.gongguhan.domain.returnrequest.service.ReturnRefundService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real JPA transactions, return/participation/refund/escrow rows; only external finance is mocked. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:returnlifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class ReturnRefundLifecycleIntegrationTest {
    @Autowired ReturnRefundService service;
    @Autowired RefundOrchestrationService refunds;
    @Autowired ReturnRequestRepository returns;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean SsafyTransferClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;
    static final AtomicLong IDS = new AtomicLong(980_000);

    @BeforeEach void provider() {
        when(policies.getString(any())).thenReturn("QA-NOT-LIVE");
        when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenReturn("QA-TRANSACTION-1");
        when(bank.transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(call -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return success();
                });
    }

    @Test void partialReturnUpdatesAllLedgersAndDoesNotTransferTwice() {
        Fixture f = fixture(800, true);
        service.processAutomatic(f.returnId());
        service.processAutomatic(f.returnId());
        refunds.refundReturn(f.returnId(), f.id(), 800);
        assertCompleted(f, 800, "HELD");
        verify(bank, times(1)).transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), eq(800L), anyString(), anyString());
    }

    @Test void fullReturnClosesEscrow() {
        Fixture f = fixture(1000, true);
        service.pickupComplete(f.id(), f.returnId());
        assertCompleted(f, 1000, "REFUNDED");
    }

    @Test void missingAccountLeavesLedgerAndAdminRetryFinalizesFailedReturn() {
        Fixture f = fixture(800, false);
        service.processAutomatic(f.returnId());
        assertThat(status("return_requests", "return_id", f.returnId())).isEqualTo("REFUND_FAILED");
        verifyNoInteractions(bank);
        jdbc.update("UPDATE users SET ssafy_account_no='QA-ACCOUNT' WHERE user_id=?", f.id());
        refunds.retry(refundId(f));
        assertCompleted(f, 800, "HELD");
    }

    @Test void ambiguousTransferBlocksBothAdminRetryAndNewLedger() {
        Fixture f = fixture(800, true);
        when(bank.transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE));
        service.processAutomatic(f.returnId());
        assertThatThrownBy(() -> refunds.retry(refundId(f))).isInstanceOf(BusinessException.class);
        refunds.refundReturn(f.returnId(), f.id(), 800);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=?", Integer.class, f.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT execution_claimed FROM refunds WHERE participation_id=?", Boolean.class, f.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT institution_tx_no FROM refunds WHERE participation_id=?", String.class, f.id())).isEqualTo("QA-TRANSACTION-1");
        verify(bank, times(1)).transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test void explicitRejectionMayBeRetriedOnceAfterCauseIsFixed() {
        Fixture f = fixture(800, true);
        when(bank.transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenReturn(new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H1000", "QA rejected", "QA-TRANSACTION-1"), List.of()))
                .thenReturn(success());
        service.processAutomatic(f.returnId());
        refunds.retry(refundId(f));
        assertCompleted(f, 800, "HELD");
        verify(bank, times(2)).transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test void duplicatePickupWhileTransferIsBlockedHasOneExternalExecution() throws Exception {
        Fixture f = fixture(800, true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(bank.transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString()))
                .thenAnswer(call -> { entered.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); return success(); });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(() -> service.processAutomatic(f.returnId()));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            service.processAutomatic(f.returnId());
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); executor.shutdownNow(); }
        assertCompleted(f, 800, "HELD");
        verify(bank, times(1)).transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test void unsupportedMethodRollsBackReturnStateAndIntentTogether() {
        Fixture f = fixture(800, true);
        jdbc.update("UPDATE payments SET method='UNKNOWN' WHERE payment_id=?", f.id());
        assertThatThrownBy(() -> service.processAutomatic(f.returnId())).isInstanceOf(BusinessException.class);
        assertThat(status("return_requests", "return_id", f.returnId())).isEqualTo("APPROVED");
        assertThat(status("participations", "participation_id", f.id())).isEqualTo("RETURNING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=?", Integer.class, f.id())).isZero();
        verifyNoInteractions(bank);
    }

    @Test void finalizationFailureCanBeReconciledWithoutAnotherTransfer() {
        Fixture f = fixture(800, true);
        jdbc.update("DELETE FROM escrows WHERE participation_id=?", f.id());
        service.processAutomatic(f.returnId());
        assertThat(status("refunds", "refund_id", refundId(f))).isEqualTo("COMPLETED");
        assertThat(status("return_requests", "return_id", f.returnId())).isEqualTo("REFUND_REQUESTED");
        insertEscrow(f.id());
        refunds.refundReturn(f.returnId(), f.id(), 800);
        assertCompleted(f, 800, "HELD");
        verify(bank, times(1)).transfer(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    private Fixture fixture(long amount, boolean account) {
        long id = IDS.incrementAndGet();
        LocalDateTime then = LocalDateTime.now().minusDays(15);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE','QA-KEY',?,NOW(),NOW())", id, UUID.randomUUID()+"@example.test", account ? "QA-ACCOUNT" : null);
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)", id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())", id,id,id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,2,1000,0,2,0,?,'FULFILLED',0,NOW(),NOW())",id,id,id,then);
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA','DELIVERED',?,FALSE,TRUE,'Y')",id,id,id,then);
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,participation_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',1000,?,?,'PAID',NOW(),NOW())",id,id,id,id,then,UUID.randomUUID().toString());
        insertEscrow(id);
        long returnId = new TransactionTemplate(transactionManager).execute(tx -> {
            long created = returns.create(new CreateCommand(id,"DEFECT","QA",0,"SELLER",amount,false,false,then,then,"00000","QA",null));
            returns.approve(created,"SELLER",then);
            return created;
        });
        return new Fixture(id,returnId);
    }

    private void insertEscrow(long id) {
        jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,1000,0,'HELD',NOW())",id,id);
    }
    private long refundId(Fixture f) { return jdbc.queryForObject("SELECT refund_id FROM refunds WHERE return_id=?",Long.class,f.returnId()); }
    private String status(String table, String key, long id) { return jdbc.queryForObject("SELECT status FROM "+table+" WHERE "+key+"=?",String.class,id); }
    private void assertCompleted(Fixture f, long amount, String escrowStatus) {
        assertThat(status("return_requests","return_id",f.returnId())).isEqualTo("REFUNDED");
        assertThat(status("participations","participation_id",f.id())).isEqualTo("REFUNDED");
        assertThat(status("refunds","refund_id",refundId(f))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM escrows WHERE participation_id=?",String.class,f.id())).isEqualTo(escrowStatus);
        assertThat(jdbc.queryForObject("SELECT refunded_amount FROM escrows WHERE participation_id=?",Long.class,f.id())).isEqualTo(amount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM return_request_histories WHERE return_id=? AND status='REFUNDED'",Integer.class,f.returnId())).isEqualTo(1);
    }
    private SsafyTransferClient.TransferResponse success() {
        return new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H0000","QA","QA-TRANSACTION-1"),List.of(new SsafyTransferClient.TransferResponse.Record("1","20260921","1","QA-ACCOUNT")));
    }
    record Fixture(long id, long returnId) {}
}
