package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.participation.service.ParticipationCancelService;
import com.gongguhan.domain.payment.common.port.CardRefundPort;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cancelsafety;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false","app.scheduling.enabled=false"})
@ActiveProfiles("test")
class ParticipationCancelSafetyIntegrationTest {
    @Autowired ParticipationCancelService cancels;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired com.gongguhan.domain.refund.service.RefundOrchestrationService refunds;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean CardRefundPort provider;
    @MockitoBean GroupBuySlotGate gate;
    @MockitoSpyBean RefundLedgerService ledger;
    static AtomicLong ids=new AtomicLong(940000);
    record Fixture(long group,long participation) {}
    Fixture fixture() {
        long id=ids.incrementAndGet();
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,5,1000,0,0,1,?,'RECRUITING',0,NOW(),NOW())",id,LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at) VALUES(?,?,100,501,'CARD',1000,?,'Y',?,'PROCESSING',NOW(),NOW())",id,id,LocalDateTime.now(clock).plusMinutes(5),UUID.randomUUID().toString());
        jdbc.update("UPDATE payments SET shipping_recipient_name='QA',shipping_recipient_phone='01000000000',shipping_zipcode='00000',shipping_address1='QA',shipping_address2='QA' WHERE payment_id=?",id);
        var paid=payments.completePayment(id);
        when(provider.cancel(any())).thenReturn(CardRefundPort.CardRefundResult.canceled(1000,"H0000","QA"));
        return new Fixture(id,Long.parseLong(paid.participationId()));
    }
    String status(Fixture f) {return jdbc.queryForObject("SELECT status FROM participations WHERE participation_id=?",String.class,f.participation());}
    int count(Fixture f) {return jdbc.queryForObject("SELECT current_participants FROM group_buys WHERE group_buy_id=?",Integer.class,f.group());}
    void unchanged(Fixture f) {
        assertThat(status(f)).isEqualTo("PAID");assertThat(count(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=?",Integer.class,f.participation())).isZero();
        verifyNoInteractions(provider);verify(gate,never()).release(f.group());
    }
    @Test void missingCardInformationMustNotCommitCancellation() {
        Fixture f=fixture();assertThatThrownBy(()->cancels.cancel(100L,f.participation(),null,null)).isInstanceOf(BusinessException.class);unchanged(f);
    }
    @Test void malformedCardInformationMustNotCommitCancellation() {
        Fixture f=fixture();assertThatThrownBy(()->cancels.cancel(100L,f.participation(),"not-a-card","bad")).isInstanceOf(BusinessException.class);unchanged(f);
    }
    @Test void failedLedgerInsertMustRollBackParticipationAndCapacity() {
        Fixture f=fixture();doThrow(new IllegalStateException("QA ledger unavailable")).when(ledger).openForParticipation(eq(f.participation()),anyLong(),any(),any());
        assertThatThrownBy(()->cancels.cancel(100L,f.participation(),"0000000000000000","000")).isInstanceOf(IllegalStateException.class);unchanged(f);
    }
    @Test void correctRetryAfterMissingInputCanStillCancel() {
        Fixture f=fixture();assertThatThrownBy(()->cancels.cancel(100L,f.participation(),null,null)).isInstanceOf(BusinessException.class);
        cancels.cancel(100L,f.participation(),"0000000000000000","000");assertThat(status(f)).isEqualTo("REFUNDED");assertThat(count(f)).isZero();verify(provider,times(1)).cancel(any());
    }
    @Test void concurrentCancellationMustCallProviderAndReleaseCapacityOnce() throws Exception {
        Fixture f=fixture();CountDownLatch start=new CountDownLatch(1);
        Callable<Boolean> task=()->{start.await();try{cancels.cancel(100L,f.participation(),"0000000000000000","000");return true;}catch(BusinessException e){return false;}};
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<Boolean> a=pool.submit(task),b=pool.submit(task);start.countDown();assertThat(java.util.List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(status(f)).isEqualTo("REFUNDED");assertThat(count(f)).isZero();verify(provider,times(1)).cancel(any());verify(gate,times(1)).release(f.group());
    }

    @Test void providerRunsOutsideTransactionWithDurableRefundIntentAlreadyVisible() {
        Fixture f=fixture();when(provider.cancel(any())).thenAnswer(invocation->{
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(status(f)).isEqualTo("REFUNDING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=? AND status='PROCESSING'",Integer.class,f.participation())).isEqualTo(1);
            return CardRefundPort.CardRefundResult.canceled(1000,"H0000","QA");
        });
        cancels.cancel(100L,f.participation(),"0000000000000000","000");assertThat(status(f)).isEqualTo("REFUNDED");
    }

    @Test void gateFailureAfterCommitMustNotSkipReservedRefund() {
        Fixture f=fixture();doThrow(new IllegalStateException("QA Redis unavailable")).when(gate).release(f.group());
        cancels.cancel(100L,f.participation(),"0000000000000000","000");assertThat(status(f)).isEqualTo("REFUNDED");verify(provider,times(1)).cancel(any());
    }

    @Test void foreignUserCannotCancelOrReserveRefund() {
        Fixture f=fixture();assertThatThrownBy(()->cancels.cancel(101L,f.participation(),"0000000000000000","000")).isInstanceOf(BusinessException.class);unchanged(f);
    }

    @Test void refundReservationRequiresTheCancellationTransaction() {
        assertThatThrownBy(() -> refunds.reserveParticipationCancellation(999L, 1000, "CARD"))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        verifyNoInteractions(provider);
    }

    @Test void providerExecutionRejectsAnOpenCallerTransaction() {
        Fixture f = fixture();
        var opened = ledger.openForParticipation(f.participation(), 1000,
                com.gongguhan.domain.refund.entity.RefundReason.PARTICIPATION_CANCEL,
                com.gongguhan.domain.refund.entity.RefundMethod.CARD_CANCEL);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored ->
                refunds.executeParticipationCancellation(f.participation(), opened,
                        "0000000000000000", "000")))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThat(status(f)).isEqualTo("PAID");
        assertThat(count(f)).isEqualTo(1);
        verifyNoInteractions(provider);
    }
}
