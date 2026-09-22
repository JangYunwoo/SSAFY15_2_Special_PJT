package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayGateway;
import com.gongguhan.domain.refund.service.RefundRecoveryService;
import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * §6 Phase 2 검증: 승인 후 공구 종료로 고립된 결제(status=FAILED, reconcile_state=REFUND_REQUIRED,
 * 참여행 없음)를 복구 스캔이 기존 수단별 실행기로 자동 환불한다. 카드는 계좌이체, 카카오는 원거래 취소.
 * 외부 금융망/제공사만 대역으로 두고 실제 원장·상태 전이·중복 차단을 검증한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:strandedreconcile;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class StrandedPaymentRefundReconcileIntegrationTest {

    @Autowired RefundRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @MockitoBean SsafyTransferClient transferClient;
    @MockitoBean SsafyApiLogService ssafyApiLogService;
    @MockitoBean PolicyService policy;
    @MockitoBean KakaoPayGateway kakaoGateway;

    static final AtomicLong IDS = new AtomicLong(940000);

    @BeforeEach
    void stubBoundary() {
        when(policy.getString(PolicyKey.SSAFY_PLATFORM_ACCOUNT_NO)).thenReturn("0019876543");
        when(policy.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policy.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        when(ssafyApiLogService.generateInstitutionTransactionUniqueNo(anyString()))
                .thenAnswer(x -> "20260922" + String.format("%012d", IDS.incrementAndGet()));
        // 계좌이체 성공 응답(거래고유번호 1건 포함).
        when(transferClient.transfer(anyString(), any(), any(), any(), anyString(), anyString(),
                anyLong(), anyString(), anyString()))
                .thenReturn(new SsafyTransferClient.TransferResponse(
                        new SsafyTransferClient.TransferResponse.Header("H0000", "정상", "TXN"),
                        List.of(new SsafyTransferClient.TransferResponse.Record("55501", "20260922", "1", "0019876543"))));
    }

    /** 고립된 CARD 결제 → 계좌이체 환불로 COMPLETED, reconcile_state=REFUND_RESERVED. */
    @Test
    void strandedCardPaymentReconciledByAccountTransfer() {
        long id = seedBuyerAndGroupBuy();
        long payment = seedStrandedPayment(id, "CARD");

        recovery.run();

        assertThat(refundStatus(payment)).isEqualTo("COMPLETED");
        assertThat(refundMethod(payment)).isEqualTo("ACCOUNT_TRANSFER");
        assertThat(refundReason(payment)).isEqualTo("GROUP_BUY_TERMINATED");
        assertThat(reconcileState(payment)).isEqualTo("REFUND_RESERVED");
        verify(transferClient, times(1)).transfer(anyString(), any(), any(), any(), anyString(), anyString(),
                anyLong(), anyString(), anyString());
    }

    /** 고립된 KAKAOPAY 결제 → 원거래 취소로 COMPLETED. */
    @Test
    void strandedKakaoPaymentReconciledByCancel() {
        long id = seedBuyerAndGroupBuy();
        long payment = seedStrandedPayment(id, "KAKAOPAY");
        jdbc.update("INSERT INTO kakaopay_payments(payment_id,tid,aid,total_amount,tax_free_amount) VALUES(?,?,?,?,0)",
                payment, "T-" + payment, "A-" + payment, 1000);
        when(kakaoGateway.cancel(any())).thenReturn(new KakaoPayGateway.CancelResult(
                "CANCEL-" + payment, "T-" + payment, "CANCEL_PAYMENT", 1000, 1000, 0));

        recovery.run();

        assertThat(refundStatus(payment)).isEqualTo("COMPLETED");
        assertThat(refundMethod(payment)).isEqualTo("KAKAOPAY");
        assertThat(reconcileState(payment)).isEqualTo("REFUND_RESERVED");
        verify(kakaoGateway, times(1)).cancel(any());
    }

    /** 두 번 돌려도 환불은 1건, 외부 송금도 1회(중복 차단). */
    @Test
    void reconcileIsIdempotentAcrossRuns() {
        long id = seedBuyerAndGroupBuy();
        long payment = seedStrandedPayment(id, "CARD");

        recovery.run();
        recovery.run();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE payment_id=?", Integer.class, payment))
                .isEqualTo(1);
        verify(transferClient, times(1)).transfer(anyString(), any(), any(), any(), anyString(), anyString(),
                anyLong(), anyString(), anyString());
    }

    // --- fixture ---

    long seedBuyerAndGroupBuy() {
        long id = IDS.incrementAndGet();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,ssafy_account_no,created_at,updated_at) "
                + "VALUES(?,?,'test-only','QA',FALSE,FALSE,'ACTIVE','mock-member','0011234567',NOW(),NOW())", id, "stranded-" + id + "@example.test");
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) "
                + "VALUES(?,501,10,5,1000,0,0,0,?,?,'CANCELED',0,NOW(),NOW())", id, now.minusHours(2), now.minusHours(1));
        return id;
    }

    long seedStrandedPayment(long buyerAndGroup, String method) {
        long payment = IDS.incrementAndGet();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,method,krw_amount,slot_expires_at,slot_marker,idempotency_key,status,card_approval_status,reconcile_state,created_at,updated_at) "
                + "VALUES(?,?,?,?,1000,?,NULL,?,'FAILED',?,'REFUND_REQUIRED',NOW(),NOW())",
                payment, buyerAndGroup, buyerAndGroup, method, now.minusMinutes(10), UUID.randomUUID().toString(),
                "CARD".equals(method) ? "COMPLETED" : null);
        return payment;
    }

    String refundStatus(long payment) {
        return jdbc.queryForObject("SELECT status FROM refunds WHERE payment_id=?", String.class, payment);
    }

    String refundMethod(long payment) {
        return jdbc.queryForObject("SELECT refund_method FROM refunds WHERE payment_id=?", String.class, payment);
    }

    String refundReason(long payment) {
        return jdbc.queryForObject("SELECT reason_type FROM refunds WHERE payment_id=?", String.class, payment);
    }

    String reconcileState(long payment) {
        return jdbc.queryForObject("SELECT reconcile_state FROM payments WHERE payment_id=?", String.class, payment);
    }
}
