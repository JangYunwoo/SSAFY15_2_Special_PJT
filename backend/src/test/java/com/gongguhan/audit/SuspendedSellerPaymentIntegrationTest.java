package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.card.dto.CardApproveRequest;
import com.gongguhan.domain.payment.card.service.CardPaymentService;
import com.gongguhan.domain.payment.card.service.SsafyCardApiClient;
import com.gongguhan.domain.payment.kakaopay.dto.KakaoPayApproveRequest;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayGateway;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayStateSigner;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoWalletChallengeRequest;
import com.gongguhan.domain.payment.crypto.service.CryptoQuoteService;
import com.gongguhan.domain.payment.crypto.service.CryptoWalletProofService;
import com.gongguhan.domain.payment.crypto.service.ExchangeRateProvider;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:suspendedsellerpayment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.admin.seed-enabled=false", "app.scheduling.enabled=false",
    "app.crypto.confirmation-poll-enabled=false",
    "app.kakaopay.secret-key=suspended-seller-test-only-secret-at-least-32"})
@ActiveProfiles("test")
class SuspendedSellerPaymentIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    @Autowired CardPaymentService cards;
    @Autowired KakaoPayService kakao;
    @Autowired KakaoPayStateSigner signer;
    @Autowired CryptoQuoteService cryptoQuotes;
    @Autowired CryptoWalletProofService cryptoProofs;
    @MockitoBean SsafyCardApiClient cardGateway;
    @MockitoBean KakaoPayGateway kakaoGateway;
    @MockitoBean ExchangeRateProvider rates;
    private static final AtomicLong IDS = new AtomicLong(980000);

    private long fixture(String sellerStatus) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order,active) VALUES(?,'Audit',1,0,TRUE)", id);
        jdbc.update("INSERT INTO seller_profiles(seller_id,business_name,status,created_at,updated_at) VALUES(?,'Audit seller',?,NOW(),NOW())", id, sellerStatus);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'Audit product',5000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())", id, id, id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,product_price,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,5000,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())", id, id, id, LocalDateTime.now(clock).minusHours(1), LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) VALUES(?,?,'Local audit','01000000000','00000','Local','Test',TRUE,NOW(),NOW())", id, id + 100);
        when(gate.tryReserve(anyLong(), anyInt(), anyInt())).thenReturn(true);
        return id;
    }

    private PaymentPrepareRequest request(long id, String method) {
        return new PaymentPrepareRequest(method, id, method.equals("CRYPTO") ? 7L : null);
    }

    private void rejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SELLER_SUSPENDED));
    }

    private long prepareThenSuspend(long id, String method) {
        var first = payments.prepare(id + 100, id, request(id, method), UUID.randomUUID().toString());
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED' WHERE seller_id=?", id);
        return Long.parseLong(first.paymentId());
    }

    @Test void cardAdapterRejectsBeforeExternalCall() {
        long id = fixture("ACTIVE");
        long paymentId = prepareThenSuspend(id, "CARD");
        rejected(() -> cards.approve(id + 100, paymentId, new CardApproveRequest("0000000000000000", "000")));
        verifyNoInteractions(cardGateway);
    }

    @Test void kakaoReadyRejectsBeforeExternalCall() {
        long id = fixture("ACTIVE");
        long paymentId = prepareThenSuspend(id, "KAKAOPAY");
        rejected(() -> kakao.ready(id + 100, paymentId));
        verifyNoInteractions(kakaoGateway);
    }

    @Test void kakaoApproveRejectsBeforeExternalCall() {
        long id = fixture("ACTIVE");
        long paymentId = prepareThenSuspend(id, "KAKAOPAY");
        String state = signer.sign(signer.issue(paymentId, id + 100, LocalDateTime.now(clock).plusMinutes(3)));
        rejected(() -> kakao.approve(id + 100, new KakaoPayApproveRequest(paymentId, "test-only-token", state)));
        verifyNoInteractions(kakaoGateway);
    }

    @Test void cryptoQuoteRejectsBeforeRateLookup() {
        long id = fixture("ACTIVE");
        long paymentId = prepareThenSuspend(id, "CRYPTO");
        rejected(() -> cryptoQuotes.issueQuote(id + 100, paymentId, new CryptoQuoteRequest(7L)));
        verifyNoInteractions(rates);
    }

    @Test void cryptoProofIssuanceRejectsSuspendedSeller() {
        long id = fixture("ACTIVE");
        long paymentId = prepareThenSuspend(id, "CRYPTO");
        rejected(() -> cryptoProofs.issue(id + 100, paymentId,
                new CryptoWalletChallengeRequest(7L, "0x" + "1".repeat(40), "0x" + "2".repeat(64))));
    }

    @ParameterizedTest @ValueSource(strings = {"CARD", "KAKAOPAY", "CRYPTO"})
    void suspendedSellerCannotIssueNewSlotAndReturnsProvisionalGate(String method) {
        long id = fixture("SUSPENDED");
        rejected(() -> payments.prepare(id + 100, id, request(id, method), UUID.randomUUID().toString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?", Integer.class, id)).isZero();
        verify(gate).release(id);
    }

    @ParameterizedTest @ValueSource(strings = {"CARD", "KAKAOPAY", "CRYPTO"})
    void suspendedAfterPrepareBlocksReuseAndApprovalButPreservesReadAndAbort(String method) {
        long id = fixture("ACTIVE");
        String key = UUID.randomUUID().toString();
        var first = payments.prepare(id + 100, id, request(id, method), key);
        long paymentId = Long.parseLong(first.paymentId());
        assertThat(payments.getApprovalContext(id + 100, paymentId, method)).isNotNull();
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED' WHERE seller_id=?", id);
        rejected(() -> payments.prepare(id + 100, id, request(id, method), key));
        rejected(() -> payments.prepare(id + 100, id, request(id, method), UUID.randomUUID().toString()));
        rejected(() -> payments.getApprovalContext(id + 100, paymentId, method));
        assertThat(payments.getPayment(id + 100, paymentId).paymentId()).isEqualTo(first.paymentId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?", Integer.class, id)).isEqualTo(1);
        payments.abort(id + 100, paymentId);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?", Integer.class, id)).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"CARD", "KAKAOPAY", "CRYPTO"})
    void reactivatedSellerCanPrepareReuseAndApproveAgain(String method) {
        long id = fixture("SUSPENDED");
        String key = UUID.randomUUID().toString();
        rejected(() -> payments.prepare(id + 100, id, request(id, method), key));
        jdbc.update("UPDATE seller_profiles SET status='ACTIVE' WHERE seller_id=?", id);
        var first = payments.prepare(id + 100, id, request(id, method), key);
        assertThat(payments.prepare(id + 100, id, request(id, method), key).paymentId()).isEqualTo(first.paymentId());
        assertThat(payments.getApprovalContext(id + 100, Long.parseLong(first.paymentId()), method)).isNotNull();
    }

    @ParameterizedTest @ValueSource(strings = {"CARD", "KAKAOPAY", "CRYPTO"})
    void paidPaymentRemainsReadableAfterSuspension(String method) {
        long id = fixture("ACTIVE");
        var first = payments.prepare(id + 100, id, request(id, method), UUID.randomUUID().toString());
        long paymentId = Long.parseLong(first.paymentId());
        jdbc.update("UPDATE payments SET status='PAID',slot_marker=NULL WHERE payment_id=?", paymentId);
        jdbc.update("UPDATE seller_profiles SET status='SUSPENDED' WHERE seller_id=?", id);
        assertThat(payments.getCompletedPayment(id + 100, paymentId, method).paymentId()).isEqualTo(first.paymentId());
    }
}
