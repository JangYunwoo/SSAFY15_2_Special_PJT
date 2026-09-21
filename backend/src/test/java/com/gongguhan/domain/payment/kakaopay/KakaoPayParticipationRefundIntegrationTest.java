package com.gongguhan.domain.payment.kakaopay;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.participation.service.ParticipationCancelService;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayGateway;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.entity.RefundStatus;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.domain.refund.service.RefundOrchestrationService;
import com.gongguhan.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** B27: real transactions/adapter/ledger, mocked provider only; never uses a live payment. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kakaoParticipationRefund;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
class KakaoPayParticipationRefundIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(810000);
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PaymentService payments;
    @Autowired ParticipationCancelService cancellations;
    @Autowired RefundLedgerService ledger;
    @Autowired RefundOrchestrationService refunds;
    @MockitoBean KakaoPayGateway gateway;
    @MockitoBean GroupBuySlotGate slotGate;

    record Fixture(long paymentId, long participationId) {}

    Fixture approved() {
        long id = IDS.incrementAndGet();
        jdbc.update("""
                INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,
                unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,
                status,version,created_at,updated_at)
                VALUES (?,501,10,5,1000,0,0,1,?,'RECRUITING',0,NOW(),NOW())
                """, id, LocalDateTime.now(clock).plusDays(1));
        jdbc.update("""
                INSERT INTO payments(payment_id,group_buy_id,user_id,address_id,method,krw_amount,
                slot_expires_at,slot_marker,idempotency_key,status,created_at,updated_at)
                VALUES (?,?,100,501,'KAKAOPAY',1000,?,'Y',?,'PROCESSING',NOW(),NOW())
                """, id, id, LocalDateTime.now(clock).plusMinutes(5), UUID.randomUUID().toString());
        jdbc.update("UPDATE payments SET shipping_recipient_name='QA',shipping_recipient_phone='01000000000',shipping_zipcode='00000',shipping_address1='QA',shipping_address2='QA' WHERE payment_id=?",id);
        var paid = payments.completePayment(id);
        jdbc.update("""
                INSERT INTO kakaopay_payments(payment_id,tid,aid,total_amount,tax_free_amount)
                VALUES (?,?,?,1000,0)
                """, id, "QA-T" + id, "QA-A" + id);
        when(gateway.cancel(any())).thenAnswer(invocation -> {
            KakaoPayGateway.CancelCommand command = invocation.getArgument(0);
            return new KakaoPayGateway.CancelResult("QA-CANCEL", command.tid(),
                    command.cancelAmount() == 1000 ? "CANCEL_PAYMENT" : "PART_CANCEL_PAYMENT",
                    command.cancelAmount(), command.cancelAmount(), 1000 - command.cancelAmount());
        });
        return new Fixture(id, Long.parseLong(paid.participationId()));
    }

    void assertFinalized(Fixture f) {
        assertThat(jdbc.queryForObject("SELECT status FROM participations WHERE participation_id=?",
                String.class, f.participationId())).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM escrows WHERE participation_id=?",
                String.class, f.participationId())).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT current_participants FROM group_buys WHERE group_buy_id=?",
                Integer.class, f.paymentId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE participation_id=? AND status='COMPLETED' AND return_id IS NULL",
                Long.class, f.participationId())).isEqualTo(1L);
    }

    @Test void participationCancellationFinalizesLedgerEscrowAndParticipationOnlyOnce() {
        Fixture f = approved();
        cancellations.cancel(100L, f.participationId(), null, null);
        assertFinalized(f);
        assertThatThrownBy(() -> cancellations.cancel(100L, f.participationId(), null, null))
                .isInstanceOf(BusinessException.class);
        verify(gateway, times(1)).cancel(any());
    }

    @Test void retryOfPreviouslyBlockedRefundFinalizesWithoutDecrementingCountAgain() {
        Fixture f = approved();
        cancellations.prepareCancel(100L, f.participationId());
        var opened = ledger.openForParticipation(f.participationId(), 1000,
                RefundReason.PARTICIPATION_CANCEL, RefundMethod.KAKAOPAY);
        long refundId = opened.refund().getRefundId();
        // Recreate the persisted B27 outcome, not an ambiguous provider timeout.
        ledger.fail(refundId, "카카오페이 취소 요청 정보가 올바르지 않습니다.");
        assertThat(refunds.retry(refundId).getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertFinalized(f);
        assertThatThrownBy(() -> refunds.retry(refundId)).isInstanceOf(BusinessException.class);
        verify(gateway, times(1)).cancel(any());
    }

    @Test void returnRefundStillPreservesReturnIdAndPartialAmount() {
        Fixture f = approved();
        var refund = refunds.refundReturn(900L, f.participationId(), 800);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getReturnId()).isEqualTo(900L);
        assertThat(refund.getAmount()).isEqualTo(800);
        verify(gateway).cancel(new KakaoPayGateway.CancelCommand("QA-T" + f.paymentId(), 800, 0));
        // Return domain owns finalization; the participation-cancel path must not run here.
        assertThat(jdbc.queryForObject("SELECT status FROM escrows WHERE participation_id=?",
                String.class, f.participationId())).isEqualTo("HELD");
    }
}
