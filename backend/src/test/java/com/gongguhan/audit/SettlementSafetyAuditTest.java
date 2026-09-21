package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.account.entity.User;
import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.account.repository.UserRepository;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.service.SettlementRecordService;
import com.gongguhan.domain.settlement.service.SettlementTransferService;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real service transactions + isolated H2; bank is mocked. Assertions express safety, not bug acceptance. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:priorityaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class SettlementSafetyAuditTest {
    @Autowired SettlementTransferService service;
    @Autowired SettlementRecordService records;
    @Autowired SettlementRepository settlements;
    @Autowired UserRepository users;
    @Autowired SellerProfileRepository sellers;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean SsafyTransferClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policies;

    long fixture() {
        User user=User.create("audit-"+UUID.randomUUID()+"@example.test","not-a-login-hash","QA","01000000000");
        user.linkSsafyUserKey("isolated-qa-user-key");
        user=users.saveAndFlush(user);
        sellers.saveAndFlush(SellerProfile.create(user.getUserId(),"QA","01000000000","999000000002","QA","00000","QA","QA"));
        Settlement s=settlements.saveAndFlush(Settlement.create(user.getUserId()+900000,user.getUserId(),1,1,1000,0,0,BigDecimal.ZERO,LocalDateTime.now()));
        when(policies.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO)).thenReturn("999000000001");
        when(policies.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policies.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        AtomicInteger sequence=new AtomicInteger();
        when(logs.generateInstitutionTransactionUniqueNo(anyString())).thenAnswer(x->String.format("20260921000000%06d",sequence.incrementAndGet()));
        return s.getSettlementId();
    }
    SsafyTransferClient.TransferResponse success(int n) {
        return new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H0000","OK","QA"+n),List.of(
                new SsafyTransferClient.TransferResponse.Record(String.valueOf(n*2),"20260921","1","999000000001"),
                new SsafyTransferClient.TransferResponse.Record(String.valueOf(n*2+1),"20260921","2","999000000002")));
    }
    String execute(long id) { try {service.execute(id); return "returned";}catch(Exception e){return e.getClass().getSimpleName();} }

    @Test void simultaneousExecuteMustCallBankAtMostOnce() throws Exception {
        long id=fixture(); AtomicInteger calls=new AtomicInteger(); CountDownLatch both=new CountDownLatch(2);
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString())).thenAnswer(x->{int n=calls.incrementAndGet();both.countDown();both.await(3,TimeUnit.SECONDS);return success(n);});
        List<String> outcomes;
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<String> a=pool.submit(()->execute(id));Future<String> b=pool.submit(()->execute(id));
            outcomes=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
        System.out.printf("AUDIT simultaneousExecute bankCalls=%d outcomes=%s final=%s%n",calls.get(),outcomes,settlements.findById(id).orElseThrow().getStatus());
        assertThat(calls.get()).as("one settlement must cause at most one external transfer").isEqualTo(1);
    }

    @Test void holdMustNotSucceedAfterTransferHasAlreadyStarted() throws Exception {
        long id=fixture(); AtomicInteger calls=new AtomicInteger(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString())).thenAnswer(x->{calls.incrementAndGet();entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release timeout");return success(10);});
        boolean holdAccepted=false;String outcome;
        try(ExecutorService pool=Executors.newSingleThreadExecutor()) {
            Future<String> transfer=pool.submit(()->execute(id));
            try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->records.hold(id,"QA hold during external transfer")).isInstanceOf(BusinessException.class);}
            finally {release.countDown();}
            outcome=transfer.get(15,TimeUnit.SECONDS);
        }
        Settlement last=settlements.findById(id).orElseThrow();
        System.out.printf("AUDIT executeHold bankCalls=%d holdAccepted=%s outcome=%s final=%s/%s%n",calls.get(),holdAccepted,outcome,last.getStatus(),last.getTransferStatus());
        assertThat(holdAccepted).as("do not acknowledge a hold after the transfer has started").isFalse();
        assertThat(outcome).isEqualTo("returned");
        assertThat(last.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test void unknownOutcomeMustBlockRetry() {
        long id=fixture(); AtomicInteger calls=new AtomicInteger();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString())).thenAnswer(x->{calls.incrementAndGet();throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,"QA simulated timeout");});
        service.execute(id);
        assertThat(settlements.findById(id).orElseThrow().getTransferStatus()).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(BusinessException.class);
        assertThat(calls.get()).isEqualTo(1);
        System.out.println("AUDIT unknownOutcome bankCalls=1 retryBlocked=true");
    }

    @Test void claimedFailedTransferMustLeaveAutomaticRetryQueue() {
        long id=fixture();
        records.failTransfer(id,"QA prior explicit failure");
        assertThat(settlements.findAutoRetryTargets(org.springframework.data.domain.Pageable.unpaged())).extracting(Settlement::getSettlementId).contains(id);
        records.loadExecutable(id);
        assertThat(settlements.findAutoRetryTargets(org.springframework.data.domain.Pageable.unpaged())).extracting(Settlement::getSettlementId).doesNotContain(id);
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(bank);
    }

    @Test void holdBeforeExecutionMustNotCallBank() {
        long id=fixture();records.hold(id,"QA before execution");
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(bank);
    }

    @Test void callerTransactionCannotWrapBankCall() {
        long id=fixture();
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.executeWithoutResult(status->service.execute(id)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        verifyNoInteractions(bank);
    }

    @Test void unexpectedProviderFailureAlsoBlocksRetryAndHold() {
        long id=fixture();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenThrow(new IllegalStateException("simulated lost provider response"));
        service.execute(id);
        assertThat(settlements.findById(id).orElseThrow().getTransferStatus()).isEqualTo("UNKNOWN");
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->records.hold(id,"QA")).isInstanceOf(BusinessException.class);
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void loggingFailureAfterSuccessDoesNotPermitAnotherTransfer() {
        long id=fixture();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString())).thenReturn(success(1));
        doThrow(new IllegalStateException("simulated log store failure")).when(logs).record(anyString(),anyString(),anyString(),anyLong(),any(),any(),any(),any());
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.execute(id)).isInstanceOf(BusinessException.class);
        assertThat(settlements.findById(id).orElseThrow().getInstitutionTxNo()).isNotBlank();
        verify(bank,times(1)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }

    @Test void explicitRejectionCanRetryAfterCauseResolved() {
        long id=fixture();
        when(bank.transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString()))
                .thenReturn(new SsafyTransferClient.TransferResponse(new SsafyTransferClient.TransferResponse.Header("H1000","QA rejected","QA"),List.of()))
                .thenReturn(success(2));
        assertThat(service.execute(id).getTransferStatus()).isEqualTo("FAILED");
        assertThat(service.execute(id).getStatus()).isEqualTo("COMPLETED");
        verify(bank,times(2)).transfer(anyString(),anyString(),anyString(),anyString(),anyString(),anyString(),anyLong(),anyString(),anyString());
    }
}
