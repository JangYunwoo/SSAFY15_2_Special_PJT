package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:deletedproductpayment;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.admin.seed-enabled=false", "app.scheduling.enabled=false",
    "app.crypto.confirmation-poll-enabled=false"})
@ActiveProfiles("test")
class DeletedProductPaymentIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired GroupBuyQueryRepository publicQueries;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    static AtomicLong ids = new AtomicLong(970000);

    long fixture(String productStatus) {
        long id = ids.incrementAndGet();
        jdbc.update("INSERT INTO categories(category_id,name,depth,sort_order,active) VALUES(?,'Audit',1,0,TRUE)",id);
        jdbc.update("INSERT INTO seller_profiles(seller_id,business_name,status,created_at,updated_at) VALUES(?,'Audit seller','ACTIVE',NOW(),NOW())",id);
        jdbc.update("INSERT INTO products(product_id,seller_id,category_id,name,price,ai_extracted,withdrawal_restricted,restriction_review_status,status,created_at,updated_at) VALUES(?,?,?,'Audit product',5000,FALSE,FALSE,'NONE',?,NOW(),NOW())",id,id,id,productStatus);
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,product_price,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,?,?,5000,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())",id,id,id,LocalDateTime.now(clock).minusHours(1),LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,is_default,created_at,updated_at) VALUES(?,?,'Local audit','01000000000','00000','Local','Test',TRUE,NOW(),NOW())",id,id+100);
        when(gate.tryReserve(anyLong(),anyInt(),anyInt())).thenReturn(true);
        return id;
    }
    PaymentPrepareRequest request(long id, String method) {
        return new PaymentPrepareRequest(method,id,method.equals("CRYPTO") ? 7L : null);
    }
    void rejected(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void deletedProductCannotIssueNewSlotAndReturnsProvisionalGate(String method) {
        long id = fixture("DELETED");
        assertThat(publicQueries.findPublicDetail(id)).isEmpty();
        rejected(()->payments.prepare(id+100,id,request(id,method),UUID.randomUUID().toString()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,id)).isZero();
        verify(gate).release(id);
    }

    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void deletedAfterPrepareCannotReuseSlotOrStartExternalApprovalButCanReadAndAbort(String method) {
        long id=fixture("ACTIVE");
        String key=UUID.randomUUID().toString();
        var first=payments.prepare(id+100,id,request(id,method),key);
        long paymentId=Long.parseLong(first.paymentId());
        assertThat(payments.prepare(id+100,id,request(id,method),key).paymentId()).isEqualTo(first.paymentId());
        jdbc.update("UPDATE products SET status='DELETED' WHERE product_id=?",id);
        rejected(()->payments.prepare(id+100,id,request(id,method),key));
        rejected(()->payments.prepare(id+100,id,request(id,method),UUID.randomUUID().toString()));
        rejected(()->payments.getApprovalContext(id+100,paymentId,method));
        assertThat(payments.getPayment(id+100,paymentId).paymentId()).isEqualTo(first.paymentId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,id)).isEqualTo(1);
        payments.abort(id+100,paymentId);
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=?",Integer.class,id)).isZero();
    }

    @Test void completedPaymentRemainsReadableAfterDeletion() {
        long id=fixture("ACTIVE");
        var first=payments.prepare(id+100,id,request(id,"CARD"),UUID.randomUUID().toString());
        jdbc.update("UPDATE payments SET status='PAID',slot_marker=NULL WHERE payment_id=?",Long.parseLong(first.paymentId()));
        jdbc.update("UPDATE products SET status='DELETED' WHERE product_id=?",id);
        assertThat(payments.getCompletedPayment(id+100,Long.parseLong(first.paymentId()),"CARD").paymentId()).isEqualTo(first.paymentId());
    }
}
