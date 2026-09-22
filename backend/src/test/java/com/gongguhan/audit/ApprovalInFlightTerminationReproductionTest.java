package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.CardPaymentService;
import com.gongguhan.domain.payment.card.service.SsafyApiLogService;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayGateway;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayRecordService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayStateSigner;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * §6 재현: 외부 승인(카드/카카오)이 성공한 뒤, 내부 completePayment 전에 공구가
 * 철회(CANCELED)되거나 모집이 실패(FAILED)로 판정되면 confirm()이 GROUP_BUY_NOT_RECRUITING로
 * 롤백되어 "외부로 돈은 나갔는데 내부는 미확정"인 고립 청구가 남는지 관측한다.
 *
 * <p>스레드/latch 대신 외부 게이트웨이 mock의 thenAnswer 안에서 공구를 종료시켜,
 * "외부 HTTP 왕복 중 공구가 종료됐다"를 결정적으로 재현한다. 이 클래스는 <b>수정 전</b>
 * 실제 동작을 고정한 재현 증거다. 제품 결함과 fixture 문제를 구분하기 위해 정상 대조군
 * (공구가 계속 RECRUITING이면 PAID로 확정)을 함께 둔다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:approvalinflight;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false",
        "app.kakaopay.secret-key=inflight-repro-only-not-a-real-provider-key"})
@ActiveProfiles("test")
class ApprovalInFlightTerminationReproductionTest {

    @Autowired PaymentService payments;
    @Autowired CardPaymentService cards;
    @Autowired KakaoPayService kakao;
    @Autowired KakaoPayRecordService kakaoRecords;
    @Autowired KakaoPayStateSigner signer;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @MockitoBean GroupBuySlotGate gate;
    @MockitoBean SsafyCardApiClient bank;
    @MockitoBean SsafyApiLogService logs;
    @MockitoBean PolicyService policy;
    @MockitoBean KakaoPayGateway gateway;

    static final AtomicLong IDS = new AtomicLong(930000);

    record Fixture(long id, long payment, String method) {}

    @BeforeEach
    void stubExternalBoundary() {
        when(gate.tryReserve(anyLong(), anyInt(), anyInt())).thenReturn(true);
        when(policy.getLong(PolicyKey.SSAFY_MERCHANT_ID)).thenReturn(1L);
        when(policy.getString(PolicyKey.SSAFY_INSTITUTION_CODE)).thenReturn("00100");
        when(policy.getString(PolicyKey.SSAFY_FINTECH_APP_NO)).thenReturn("001");
        AtomicInteger tx = new AtomicInteger();
        when(logs.generateInstitutionTransactionUniqueNo(anyString()))
                .thenAnswer(x -> "20260922" + String.format("%012d", tx.incrementAndGet()));
    }

    Fixture prepare(String method) {
        long id = IDS.incrementAndGet();
        LocalDateTime now = LocalDateTime.now(clock);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,ssafy_user_key,created_at,updated_at) "
                + "VALUES(?,?,'test-only','QA',FALSE,FALSE,'ACTIVE','mock-member',NOW(),NOW())", id, "inflight-" + id + "@example.test");
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) "
                + "VALUES(?,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())", id, now.minusHours(1), now.plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) "
                + "VALUES(?,?,'QA','01000000000','00000','QA','QA',TRUE,NOW(),NOW())", id, id);
        long payment = Long.parseLong(payments.prepare(id, id,
                new PaymentPrepareRequest(method, id, null), UUID.randomUUID().toString()).paymentId());
        return new Fixture(id, payment, method);
    }

    SsafyCardApiClient.SsafyCardTransactionResponse cardSuccess() {
        return new SsafyCardApiClient.SsafyCardTransactionResponse(
                new SsafyCardApiClient.SsafyCardTransactionResponse.Header("H0000", "OK"),
                new SsafyCardApiClient.SsafyCardTransactionResponse.Rec("1", "1", "QA", "1", "QA", "20260922", "110000", "1000"));
    }

    /** recordReady가 저장한 tid/주문/사용자/금액과 일치해야 verifyApproval을 통과한다. */
    KakaoPayGateway.ApproveResult kakaoApproved(Fixture f) {
        return new KakaoPayGateway.ApproveResult(
                "A" + f.payment, "T" + f.payment, "PAYMENT-" + f.payment, String.valueOf(f.id),
                1000, 0, "MONEY");
    }

    void terminate(long groupBuyId, String state) {
        jdbc.update("UPDATE group_buys SET status=?,reserved_slots=0 WHERE group_buy_id=?", state, groupBuyId);
    }

    String errorOf(Runnable action) {
        try {
            action.run();
            return "returned";
        } catch (BusinessException e) {
            return e.getErrorCode().name();
        }
    }

    String status(long payment) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?", String.class, payment);
    }

    long count(String table, String column, long value) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Long.class, value);
    }

    /**
     * 정상 대조군: 공구가 끝까지 RECRUITING이면 카드/카카오 모두 PAID로 확정되고 참여가 1건 생긴다.
     * fixture 자체가 결제를 완주할 수 있음을 먼저 증명한다.
     */
    @ParameterizedTest
    @CsvSource({"CARD", "KAKAOPAY"})
    void positiveControlCompletesWhenGroupStaysRecruiting(String method) {
        Fixture f = prepare(method);
        when(bank.createCreditCardTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(cardSuccess());
        when(gateway.approve(any())).thenReturn(kakaoApproved(f));

        assertThat(errorOf(action(f))).isEqualTo("returned");
        assertThat(status(f.payment)).isEqualTo("PAID");
        assertThat(count("participations", "group_buy_id", f.id)).isEqualTo(1);
    }

    /**
     * §6 수정 검증: 외부 승인 성공 순간 공구가 종료되면, 외부 청구 성공 사실을 결과 불명(UNKNOWN)이
     * 아니라 환불 대상(status=FAILED, reconcile_state=REFUND_REQUIRED)으로 durable하게 기록한다.
     * 사용자에게는 환불 예약을 알린다. 재호출로 중복 청구가 발생하지 않는다. 실제 환불 송금은 Phase 2.
     */
    @ParameterizedTest
    @CsvSource({
            "CARD,CANCELED", "CARD,FAILED", "CARD,FULFILLED",
            "KAKAOPAY,CANCELED", "KAKAOPAY,FAILED", "KAKAOPAY,FULFILLED"})
    void externalChargeMarkedRefundRequiredWhenGroupTerminatesDuringApproval(String method, String terminalState) {
        Fixture f = prepare(method);
        AtomicInteger externalCalls = new AtomicInteger();

        when(bank.createCreditCardTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenAnswer(inv -> {
                    externalCalls.incrementAndGet();
                    terminate(f.id, terminalState); // 외부 HTTP 왕복 중 판매 철회/모집 종료가 커밋된 상황
                    return cardSuccess();
                });
        when(gateway.approve(any())).thenAnswer(inv -> {
            externalCalls.incrementAndGet();
            terminate(f.id, terminalState);
            return kakaoApproved(f);
        });

        String first = errorOf(action(f));

        System.out.printf("AUDIT inflight method=%s terminal=%s firstResult=%s externalCalls=%d finalStatus=%s reconcile=%s cardApproval=%s participations=%d%n",
                method, terminalState, first, externalCalls.get(), status(f.payment),
                reconcileState(f.payment), cardApprovalStatus(f.payment), count("participations", "group_buy_id", f.id));

        // 외부 승인은 정확히 1회 성공했다(돈은 나갔다).
        assertThat(externalCalls.get()).isEqualTo(1);
        // 사용자에게는 승인 후 공구 종료로 환불이 예약됐음을 알린다(카드·카카오 공통).
        assertThat(first).isEqualTo("GROUP_BUY_TERMINATED_REFUND_SCHEDULED");
        // 참여/에스크로는 만들지 않는다.
        assertThat(count("participations", "group_buy_id", f.id)).isZero();

        // === 외부 청구 성공을 환불 대상으로 durable하게 기록한다 ===
        assertThat(status(f.payment)).isEqualTo("FAILED");
        assertThat(reconcileState(f.payment)).isEqualTo("REFUND_REQUIRED");
        if ("CARD".equals(method)) {
            // 결과 불명(UNKNOWN)이 아니라 완료로 확정한다(외부 청구는 명백히 성공).
            assertThat(cardApprovalStatus(f.payment)).isEqualTo("COMPLETED");
            // 청구 상세 행이 남아 환불에 쓸 증거가 된다.
            assertThat(count("card_payments", "payment_id", f.payment)).isEqualTo(1);
        } else {
            // 카카오 승인 aid가 같은 트랜잭션에서 durable하게 기록돼 환불에 쓸 증거가 된다.
            assertThat(jdbc.queryForObject("SELECT aid FROM kakaopay_payments WHERE payment_id=?", String.class, f.payment))
                    .isNotNull();
        }

        // 재호출은 새 외부 청구를 만들지 않고, 이미 기록한 환불 대상 상태를 그대로 유지한다.
        String second = errorOf(action(f));
        assertThat(externalCalls.get()).as("재시도가 새로운 외부 청구를 발생시키면 안 된다").isEqualTo(1);
        assertThat(second).isEqualTo("PAYMENT_ALREADY_PROCESSED");
        assertThat(status(f.payment)).isEqualTo("FAILED");
        assertThat(reconcileState(f.payment)).isEqualTo("REFUND_REQUIRED");
    }

    String cardApprovalStatus(long payment) {
        return jdbc.queryForObject("SELECT card_approval_status FROM payments WHERE payment_id=?", String.class, payment);
    }

    String reconcileState(long payment) {
        return jdbc.queryForObject("SELECT reconcile_state FROM payments WHERE payment_id=?", String.class, payment);
    }

    // --- 결제수단별 진입 액션 ---

    Runnable action(Fixture f) {
        if ("CARD".equals(f.method)) {
            return () -> cards.approve(f.id, f.payment, new CardApproveRequest("1005518816096479", "725"));
        }
        return () -> {
            LocalDateTime expires = LocalDateTime.now(clock).plusMinutes(3);
            KakaoPayStateSigner.StateClaims claims = signer.issue(f.payment, f.id, expires);
            kakaoRecords.recordReady(f.payment, "T" + f.payment, "PAYMENT-" + f.payment,
                    String.valueOf(f.id), 1000, "https://pay.example.test", claims.nonce(), expires);
            KakaoPayApproveRequest request = new KakaoPayApproveRequest(f.payment, "mock-token", signer.sign(claims));
            kakao.approve(f.id, request);
        };
    }
}
