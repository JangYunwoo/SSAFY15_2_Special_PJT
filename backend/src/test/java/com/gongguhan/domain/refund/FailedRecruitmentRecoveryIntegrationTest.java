package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.*;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:recruitrefundrecovery;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class FailedRecruitmentRecoveryIntegrationTest {
    @Autowired RefundRecoveryService recovery;
    @Autowired FailedRecruitmentRefundRecordService records;
    @Autowired RefundOrchestrationService execution;
    @Autowired RefundLedgerService ledger;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SsafyTransferClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;
    static final AtomicLong IDS=new AtomicLong(985_000);

    @BeforeEach void provider(){
        when(policies.getString(any())).thenReturn("QA-NOT-LIVE");
        when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenReturn("QA-REFUND-RECOVERY");
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return success();});
    }

    @Test void persistedFailedGroupRefundsWithoutAnyEventAndOnlyOnce(){
        long id=fixture("FAILED",true);
        recovery.processFailedGroup(id);recovery.processFailedGroup(id);recovery.run();
        completed(id);
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),eq(1000L),anyString(),anyString());
    }

    @Test void recruitingGroupIsNotRefunded(){
        long id=fixture("RECRUITING",true);recovery.processFailedGroup(id);
        assertThat(status("participations",id)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=?",Integer.class,id)).isZero();
        verifyNoInteractions(bank);
    }

    @Test void failedBuyerDoesNotPreventAnotherRefundOrCreateRepeatedFailedRows(){
        long missing=fixture("FAILED",false),good=fixture("FAILED",true);
        recovery.processFailedGroup(missing);recovery.processFailedGroup(good);recovery.processFailedGroup(missing);
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE participation_id=?",String.class,missing)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=?",Integer.class,missing)).isEqualTo(1);
        completed(good);
    }

    @Test void processStoppedAfterReservationRecoversUnsentLedger(){
        long id=fixture("FAILED",true);Long refundId=records.reserve(id);
        assertThat(refundId).isNotNull();assertThat(status("participations",id)).isEqualTo("REFUNDING");
        jdbc.update("UPDATE refunds SET created_at=? WHERE refund_id=?",LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2),refundId);
        recovery.run();completed(id);
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void claimedUnknownOutcomeIsNeverAutomaticallySentAgain(){
        long id=fixture("FAILED",true);Long refundId=records.reserve(id);
        ledger.claimExecution(refundId,"QA-POSSIBLY-SENT");
        jdbc.update("UPDATE refunds SET created_at=? WHERE refund_id=?",LocalDateTime.now(java.time.ZoneOffset.UTC).minusDays(1),refundId);
        recovery.run();execution.resumePersisted(refundId);
        verifyNoInteractions(bank);
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE refund_id=?",String.class,refundId)).isEqualTo("PROCESSING");
    }

    @Test void completedExternalRefundReconcilesDomainWithoutSendingAgain(){
        long id=fixture("FAILED",true);jdbc.update("DELETE FROM escrows WHERE participation_id=?",id);
        recovery.processFailedGroup(id);
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE participation_id=?",String.class,id)).isEqualTo("COMPLETED");
        escrow(id);recovery.run();recovery.run();completed(id);
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void concurrentRecoveryAndOriginalExecutionHaveOneProviderCall() throws Exception {
        long id=fixture("FAILED",true);Long refundId=records.reserve(id);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenAnswer(call->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return success();});
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try {
            Future<?> first=executor.submit(()->execution.resumePersisted(refundId));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            execution.resumePersisted(refundId);
            assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE refund_id=?",String.class,refundId)).isEqualTo("PROCESSING");
            release.countDown();first.get(10,TimeUnit.SECONDS);
        }finally{release.countDown();executor.shutdownNow();}
        completed(id);
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    private long fixture(String state,boolean account){
        long id=IDS.incrementAndGet();LocalDateTime then=LocalDateTime.now().minusDays(1);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,FALSE,'ACTIVE','QA-KEY',?,NOW(),NOW())",id,UUID.randomUUID()+"@example.test",account?"QA-ACCOUNT":null);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,1,1,2,1000,0,1,0,?,?,0,NOW(),NOW())",id,then,state);
        jdbc.update("INSERT INTO participations(participation_id,group_buy_id,user_id,share_amount,shipping_fee,total_amount,recipient_name,recipient_phone,zipcode,address1,status,joined_at,confirm_notice_agreed,confirm_hold,active_marker) VALUES(?,?,?,1000,0,1000,'QA','01000000000','00000','QA','PAID',?,FALSE,FALSE,'Y')",id,id,id,then);
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,participation_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',1000,?,?,'PAID',NOW(),NOW())",id,id,id,id,then,UUID.randomUUID().toString());
        escrow(id);return id;
    }
    private void escrow(long id){jdbc.update("INSERT INTO escrows(participation_id,group_buy_id,amount,refunded_amount,status,held_at) VALUES(?,?,1000,0,'HELD',NOW())",id,id);}
    private String status(String table,long id){return jdbc.queryForObject("SELECT status FROM "+table+" WHERE participation_id=?",String.class,id);}
    private void completed(long id){assertThat(status("participations",id)).isEqualTo("REFUNDED");assertThat(status("escrows",id)).isEqualTo("REFUNDED");assertThat(status("refunds",id)).isEqualTo("COMPLETED");}
    private SsafyTransferClient.TransferResponse success(){return new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H0000","QA","QA-REFUND-RECOVERY"),List.of(new SsafyTransferClient.TransferResponse.Record("1","20260921","1","QA-ACCOUNT")));}
}
