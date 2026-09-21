package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:prepareboundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class PaymentPrepareBoundaryIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    static AtomicLong ids = new AtomicLong(930000);
    record Fixture(long id, String key) {
        PaymentPrepareRequest card() { return new PaymentPrepareRequest("CARD", id, null); }
        PaymentPrepareRequest crypto(long asset) { return new PaymentPrepareRequest("CRYPTO", id, asset); }
    }
    Fixture fixture() {
        long id=ids.incrementAndGet();
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())",id,LocalDateTime.now(clock).minusHours(1),LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) VALUES(?,?,'QA','01000000000','00000','QA','QA',TRUE,NOW(),NOW())",id,id);
        when(gate.tryReserve(anyLong(),anyInt(),anyInt())).thenReturn(true);
        return new Fixture(id,UUID.randomUUID().toString());
    }
    void code(Runnable action, String expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode().name()).isEqualTo(expected));
    }
    @Test void cryptoOriginalAssetSurvivesDatabaseReloadAndRejectsDifferentAsset() {
        Fixture f=fixture();
        PaymentResponse first=payments.prepare(f.id(),f.id(),f.crypto(7),f.key());
        assertThat(payments.prepare(f.id(),f.id(),f.crypto(7),f.key()).paymentId()).isEqualTo(first.paymentId());
        code(()->payments.prepare(f.id(),f.id(),f.crypto(8),f.key()),"PAYMENT_REQUEST_CONFLICT");
        code(()->payments.prepare(f.id(),f.id(),f.crypto(8),"another-"+f.key()),"PAYMENT_REQUEST_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT prepare_crypto_asset_id FROM payments WHERE payment_id=?",Long.class,Long.parseLong(first.paymentId()))).isEqualTo(7);
    }
    @Test void legacyCryptoOriginalAssetIsNotGuessedFromLaterQuote() {
        Fixture f=fixture();PaymentResponse first=payments.prepare(f.id(),f.id(),f.crypto(7),f.key());
        jdbc.update("UPDATE payments SET prepare_crypto_asset_id=NULL WHERE payment_id=?",Long.parseLong(first.paymentId()));
        code(()->payments.prepare(f.id(),f.id(),f.crypto(7),f.key()),"PAYMENT_REQUEST_CONFLICT");
    }
    @Test void foreignKeyAndBodyMismatchCannotReadOrChangeOwnersSlot() {
        Fixture f=fixture();payments.prepare(f.id(),f.id(),f.card(),f.key());
        code(()->payments.prepare(f.id()+100,f.id(),f.card(),f.key()),"RESOURCE_ACCESS_DENIED");
        code(()->payments.prepare(f.id(),f.id()+1,f.card(),f.key()),"PAYMENT_REQUEST_CONFLICT");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest("KAKAOPAY",f.id(),null),f.key()),"PAYMENT_REQUEST_CONFLICT");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest("CARD",f.id()+1,null),f.key()),"PAYMENT_REQUEST_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.id())).isEqualTo(1);
    }
    @Test void terminalKeyCannotStartNewPaymentButNewKeyCan() {
        Fixture f=fixture();PaymentResponse p=payments.prepare(f.id(),f.id(),f.card(),f.key());
        payments.abort(f.id(),Long.parseLong(p.paymentId()));
        code(()->payments.prepare(f.id(),f.id(),f.card(),f.key()),"PAYMENT_ALREADY_PROCESSED");
        assertThat(payments.prepare(f.id(),f.id(),f.card(),"new-"+f.key()).paymentId()).isNotEqualTo(p.paymentId());
    }
    @Test void closedRecruitmentCannotIssueSlotAndProvisionalGateIsReturned() {
        Fixture f=fixture();jdbc.update("UPDATE group_buys SET recruit_end_at=? WHERE group_buy_id=?",LocalDateTime.now(clock).minusSeconds(1),f.id());
        code(()->payments.prepare(f.id(),f.id(),f.card(),f.key()),"GROUP_BUY_NOT_RECRUITING");
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.id())).isZero();
        verify(gate).release(f.id());
    }
    @Test void issuedUnexpiredSlotCanBeRetriedAfterRecruitmentEnd() {
        Fixture f=fixture();PaymentResponse first=payments.prepare(f.id(),f.id(),f.card(),f.key());
        jdbc.update("UPDATE group_buys SET recruit_end_at=? WHERE group_buy_id=?",LocalDateTime.now(clock).minusSeconds(1),f.id());
        assertThat(payments.prepare(f.id(),f.id(),f.card(),f.key()).paymentId()).isEqualTo(first.paymentId());
    }
    @Test void simultaneousSameKeyReturnsOnePersistedPayment() throws Exception {
        Fixture f=fixture();CountDownLatch start=new CountDownLatch(1);
        Callable<String> task=()->{start.await();return payments.prepare(f.id(),f.id(),f.card(),f.key()).paymentId();};
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<String> a=pool.submit(task),b=pool.submit(task);start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?",Integer.class,f.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,f.id())).isEqualTo(1);
    }

    @Test void expiredKeyIsNotReturnedAsReadyAndDoesNotReserveAgain() {
        Fixture f=fixture();PaymentResponse p=payments.prepare(f.id(),f.id(),f.card(),f.key());
        jdbc.update("UPDATE payments SET slot_expires_at=? WHERE payment_id=?",LocalDateTime.now(clock).minusSeconds(1),Long.parseLong(p.paymentId()));
        code(()->payments.prepare(f.id(),f.id(),f.card(),f.key()),"PAYMENT_SLOT_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?",Integer.class,f.id())).isEqualTo(1);
    }

    @Test void invalidKeyMethodAndAssetNeverIssuePaymentOrAcquireGate() {
        Fixture f=fixture();
        code(()->payments.prepare(f.id(),f.id(),f.card(),null),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),f.card()," "),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),f.card(),"x".repeat(65)),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),null,f.key()),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest(null,f.id(),null),f.key()),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest("OTHER",f.id(),null),f.key()),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest("CARD",f.id(),7L),f.key()),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),new PaymentPrepareRequest("CRYPTO",f.id(),null),f.key()),"VALIDATION_ERROR");
        code(()->payments.prepare(f.id(),f.id(),f.crypto(0),f.key()),"VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?",Integer.class,f.id())).isZero();
        verify(gate,never()).tryReserve(eq(f.id()),anyInt(),anyInt());
    }
}
