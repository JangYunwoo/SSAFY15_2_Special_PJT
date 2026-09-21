package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.CardPaymentService;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cardpriorityaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
/** 금융망만 모킹하고 실제 DB 트랜잭션으로 중복 승인·슬롯 종료 경합을 검증한다. */
class CardLifecycleSafetyAuditTest {
    @Autowired CardPaymentService cards;
    @Autowired PaymentService payments;
    @Autowired com.gongguhan.domain.payment.card.service.CardApprovalRecordService approvalRecords;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean SsafyCardApiClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;
    @MockitoBean GroupBuySlotGate gate;
    static AtomicInteger ids=new AtomicInteger(910000);
    record Fixture(long user,long payment) {}

    Fixture fixture() {
        long id=ids.incrementAndGet();
        User user=User.create("card-audit-"+UUID.randomUUID()+"@example.test","not-a-login-hash","QA","01000000000");
        user.linkSsafyUserKey("isolated-qa-user-key"); user=users.saveAndFlush(user);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,10,1000,0,0,1,?,'RECRUITING',0,NOW(),NOW())",id,LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) VALUES(?,?,'QA','01000000000','00000','QA','QA',TRUE,NOW(),NOW())",id,user.getUserId());
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,?,'CARD',1000,?,'Y',?,'PROCESSING',NOW(),NOW())",id,id,user.getUserId(),id,LocalDateTime.now(clock).plusMinutes(5),UUID.randomUUID().toString());
        when(policies.getLong(any())).thenReturn(1L); when(policies.getString(any())).thenReturn("QA");
        AtomicInteger tx=new AtomicInteger(); when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenAnswer(x->"20260921"+String.format("%012d",tx.incrementAndGet()));
        return new Fixture(user.getUserId(),id);
    }
    SsafyCardApiClient.SsafyCardTransactionResponse success(int n) {
        return new SsafyCardApiClient.SsafyCardTransactionResponse(new SsafyCardApiClient.SsafyCardTransactionResponse.Header("H0000","OK"),new SsafyCardApiClient.SsafyCardTransactionResponse.Rec(String.valueOf(n),"1","QA","1","QA","20260921","110000","1000"));
    }
    String approve(Fixture f) {try {cards.approve(f.user(),f.payment(),new CardApproveRequest("0000000000000000","000"));return "returned";}catch(BusinessException e){return e.getErrorCode().name();}}
    String status(Fixture f) {return jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,f.payment());}
    long count(Fixture f,String table) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(table.equals("participations")?"group_buy_id":"payment_id")+"=?",Long.class,f.payment());}

    @Test void normalApprovalPositiveControl() {
        Fixture f=fixture();when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong())).thenReturn(success(1));
        assertThat(approve(f)).isEqualTo("returned");assertThat(status(f)).isEqualTo("PAID");assertThat(count(f,"participations")).isEqualTo(1);
    }

    @Test void callerTransactionCannotEncloseExternalCharge() {
        Fixture f=fixture();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(ignored -> approve(f)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        verifyNoInteractions(bank);
    }
    void overlappingEnd(String end) throws Exception {
        Fixture f=fixture();AtomicInteger calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong())).thenAnswer(x->{calls.incrementAndGet();entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("QA release timeout");return success(1);});
        String result;
        try(ExecutorService pool=Executors.newSingleThreadExecutor()) {
            Future<String> pending=pool.submit(()->approve(f));
            try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                if(end.equals("abort"))assertThatThrownBy(()->payments.abort(f.user(),f.payment())).isInstanceOf(BusinessException.class);
                else {jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=?",LocalDateTime.now(clock).minusSeconds(1),f.payment());payments.expireSlotIfDue(f.payment());}
            }finally {release.countDown();}
            result=pending.get(15,TimeUnit.SECONDS);
        }
        System.out.printf("AUDIT card-%s externalSuccess=%d result=%s final=%s participationRows=%d cardRows=%d%n",end,calls.get(),result,status(f),count(f,"participations"),count(f,"card_payments"));
        assertThat(status(f)).as("successful external charge must be finalized or explicitly tracked for compensation, not simply terminated").isNotIn("FAILED","EXPIRED");
        assertThat(result).isEqualTo("returned");
        assertThat(status(f)).isEqualTo("PAID");
        assertThat(count(f,"participations")).isEqualTo(1);
    }
    @Test void approvalVersusAbortMustNotOrphanCharge() throws Exception {overlappingEnd("abort");}
    @Test void approvalVersusExpiryMustNotOrphanCharge() throws Exception {overlappingEnd("expiry");}

    @Test void ambiguousTimeoutMustNotBlindlyCreateAnotherCharge() {
        Fixture f=fixture();AtomicInteger calls=new AtomicInteger();
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong())).thenAnswer(x->{int n=calls.incrementAndGet();if(n==1)throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,"QA timeout: bank outcome unknown");return success(n);});
        String first=approve(f),second=approve(f);
        System.out.printf("AUDIT cardTimeout bankCalls=%d first=%s second=%s final=%s%n",calls.get(),first,second,status(f));
        assertThat(calls.get()).as("unknown first charge outcome requires reconciliation before a new financial request").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT card_approval_status FROM payments WHERE payment_id=?",String.class,f.payment())).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->payments.abort(f.user(),f.payment())).isInstanceOf(BusinessException.class);
        jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=?",LocalDateTime.now(clock).minusSeconds(1),f.payment());
        payments.expireSlotIfDue(f.payment());
        assertThat(status(f)).isEqualTo("PROCESSING");
    }

    @Test void concurrentApprovalMustChargeOnce() throws Exception {
        Fixture f=fixture();AtomicInteger calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong())).thenAnswer(x->{calls.incrementAndGet();entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("QA release timeout");return success(1);});
        try(ExecutorService pool=Executors.newSingleThreadExecutor()) {
            Future<String> first=pool.submit(()->approve(f));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
                assertThat(approve(f)).isEqualTo("PAYMENT_ALREADY_PROCESSED");
                assertThat(jdbc.queryForObject("SELECT card_approval_status FROM payments WHERE payment_id=?",String.class,f.payment())).isEqualTo("PROCESSING");
            } finally {release.countDown();}
            assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo("returned");
        }
        assertThat(calls.get()).isEqualTo(1);
        assertThat(status(f)).isEqualTo("PAID");
        assertThat(count(f,"participations")).isEqualTo(1);
        assertThat(count(f,"card_payments")).isEqualTo(1);
        System.out.println("AUDIT concurrentCard bankCalls=1 participationRows=1 cardRows=1");
    }


    @Test void committedClaimBlocksLaterRequestEvenWithoutOriginalCaller() {
        Fixture f=fixture();
        approvalRecords.claim(f.user(),f.payment(),"20260921000000000999");
        assertThat(approve(f)).isEqualTo("PAYMENT_ALREADY_PROCESSED");
        verifyNoInteractions(bank);
    }

    @Test void explicitDeclineReleasesExactlyOneSlotAndCannotChargeAgain() {
        Fixture f=fixture();
        when(bank.createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong()))
                .thenReturn(new SsafyCardApiClient.SsafyCardTransactionResponse(new SsafyCardApiClient.SsafyCardTransactionResponse.Header("A1054","QA decline"),null));
        assertThat(approve(f)).isEqualTo("EXTERNAL_PAYMENT_ERROR");
        assertThat(approve(f)).isEqualTo("PAYMENT_ALREADY_PROCESSED");
        assertThat(status(f)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.payment())).isZero();
        verify(bank,times(1)).createCreditCardTransaction(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyLong());
    }

    @Test void abortBeforeApprovalMustNotCallBank() {
        Fixture f=fixture();payments.abort(f.user(),f.payment());
        assertThat(approve(f)).isEqualTo("PAYMENT_ALREADY_PROCESSED");
        verifyNoInteractions(bank);
    }

    @Test void simultaneousAbortMustPreserveAnotherBuyersSlot() throws Exception {
        Fixture f=fixture();
        jdbc.update("UPDATE group_buys SET reserved_slots=2 WHERE group_buy_id=?",f.payment());
        CountDownLatch start=new CountDownLatch(1);AtomicInteger accepted=new AtomicInteger();
        Callable<Void> abort=()->{start.await();try {payments.abort(f.user(),f.payment());accepted.incrementAndGet();}catch(BusinessException e){assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ALREADY_PROCESSED);}return null;};
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<Void> a=pool.submit(abort),b=pool.submit(abort);start.countDown();a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
        }
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.payment())).isEqualTo(1);
        verify(gate,times(1)).release(f.payment());
    }
}
