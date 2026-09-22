package com.gongguhan.domain.payment.common.service;

import static org.assertj.core.api.Assertions.*;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:terminalslots;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.admin.seed-enabled=false", "app.slot-gate.local-fallback-enabled=true"})
@ActiveProfiles("test")
class TerminalGroupSlotIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired GroupBuySlotService slots;
    @Autowired GroupBuyRepository groups;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    private static final AtomicLong IDS = new AtomicLong(997000);

    @ParameterizedTest @ValueSource(strings={"CANCELED", "FAILED", "FULFILLED"})
    void abortAfterTerminalCleanupDoesNotRollback(String status) {
        long id=fixture(status, false);
        payments.abort(id,id);
        assertState(id,"FAILED");
    }
    @ParameterizedTest @ValueSource(strings={"CANCELED", "FAILED", "FULFILLED"})
    void expireAfterTerminalCleanupDoesNotRollback(String status) {
        long id=fixture(status,true);
        payments.expireSlotIfDue(id);
        payments.expireSlotIfDue(id);
        assertState(id,"EXPIRED");
    }
    @ParameterizedTest @ValueSource(strings={"CANCELED", "FAILED", "FULFILLED"})
    void failureCallbackAfterTerminalCleanupDoesNotRollback(String status) {
        long id=fixture(status,false);
        payments.failPayment(id,"QA provider rejection");
        payments.failPayment(id,"QA repeated callback");
        assertState(id,"FAILED");
    }
    @Test void recruitingCounterCorruptionStillFailsInsteadOfBeingHidden() {
        long id=fixture("RECRUITING",false);
        assertThatThrownBy(()->payments.abort(id,id)).isInstanceOf(IllegalStateException.class);
        assertState(id,"PROCESSING");
    }
    @ParameterizedTest @ValueSource(strings={"CANCELED", "FAILED", "FULFILLED"})
    void terminalGroupCannotConfirmEvenWithStalePositiveCounter(String status) {
        long id=fixture(status,false);
        jdbc.update("UPDATE group_buys SET reserved_slots=1 WHERE group_buy_id=?",id);
        assertThatThrownBy(()->slots.confirm(id)).isInstanceOf(com.gongguhan.global.error.BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT current_participants FROM group_buys WHERE group_buy_id=?",Integer.class,id)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"CANCELED,PROCESSING", "CANCELED,UNKNOWN", "FAILED,PROCESSING",
            "FAILED,UNKNOWN", "FULFILLED,PROCESSING", "FULFILLED,UNKNOWN"})
    void terminalCleanupDoesNotReleaseUnresolvedCardApproval(String status, String approval) {
        long id=fixture(status,true);
        jdbc.update("UPDATE payments SET method='CARD',card_approval_status=?,slot_marker='Y' WHERE payment_id=?",approval,id);
        assertThatThrownBy(()->payments.abort(id,id))
                .isInstanceOfSatisfying(com.gongguhan.global.error.BusinessException.class,
                        e->assertThat(e.getErrorCode()).isEqualTo(com.gongguhan.global.error.ErrorCode.PAYMENT_ALREADY_PROCESSED));
        payments.expireSlotIfDue(id);
        payments.failPayment(id,"QA callback cannot override unresolved approval");
        assertState(id,"PROCESSING");
        assertThat(jdbc.queryForObject("SELECT card_approval_status FROM payments WHERE payment_id=?",String.class,id)).isEqualTo(approval);
        assertThat(jdbc.queryForObject("SELECT slot_marker FROM payments WHERE payment_id=?",String.class,id)).isEqualTo("Y");
    }

    private void assertState(long id,String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=?",String.class,id)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,id)).isZero();
    }
    private long fixture(String terminal,boolean expired) {
        long id=IDS.incrementAndGet(); LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("INSERT INTO users(user_id,email,password_hash,nickname,phone_verified,is_seller,status,created_at,updated_at) VALUES(?,?,'unused','QA',FALSE,TRUE,'ACTIVE',NOW(),NOW())",id,UUID.randomUUID()+"@example.test");
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order) VALUES(?,'QA',1,1)",id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'QA',1000,FALSE,FALSE,'NONE','ACTIVE',NOW(),NOW())",id,id,id);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,2,1000,0,0,1,?,'RECRUITING',0,NOW(),NOW())",id,id,id,now.minusMinutes(1));
        jdbc.update("INSERT INTO payments(payment_id,group_buy_id,user_id,method,krw_amount,slot_expires_at,idempotency_key,status,created_at,updated_at) VALUES(?,?,?,'KAKAOPAY',1000,?,?,'PROCESSING',NOW(),NOW())",id,id,id,expired?now.minusMinutes(1):now.plusMinutes(5),UUID.randomUUID().toString());
        new TransactionTemplate(manager).execute(tx->{
            var group=groups.findWithLockByGroupBuyId(id).orElseThrow();
            if("CANCELED".equals(terminal)) group.withdrawBySeller();
            else if("FAILED".equals(terminal)) group.judgeRecruitment(now,null);
            return null;
        });
        if("FULFILLED".equals(terminal)||"RECRUITING".equals(terminal))
            jdbc.update("UPDATE group_buys SET status=?,reserved_slots=0 WHERE group_buy_id=?",terminal,id);
        return id;
    }
}
