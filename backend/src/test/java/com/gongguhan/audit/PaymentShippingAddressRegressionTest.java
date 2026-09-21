package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.service.PaymentService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 기존 API만 사용해 수정 이전 코드에서도 동일하게 실행할 수 있는 재현 시험. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:shippingregression;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class PaymentShippingAddressRegressionTest {
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean GroupBuySlotGate gate;
    static final AtomicLong IDS = new AtomicLong(960000);
    record Fixture(long id, long payment, String key, PaymentPrepareRequest request) {}
    Fixture prepare(String method) {
        long id=IDS.incrementAndGet();
        jdbc.update("INSERT INTO group_buys(group_buy_id,product_id,seller_id,target_participants,unit_share_amount,shipping_fee,current_participants,reserved_slots,recruit_start_at,recruit_end_at,status,version,created_at,updated_at) VALUES(?,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())",id,LocalDateTime.now(clock).minusHours(1),LocalDateTime.now(clock).plusDays(1));
        jdbc.update("INSERT INTO user_addresses(address_id,user_id,recipient_name,recipient_phone,zipcode,address1,address2,delivery_request,is_default,created_at,updated_at) VALUES(?,?,'최초 수령인','01000000000','00000','최초 주소','상세 주소','문 앞',TRUE,NOW(),NOW())",id,id);
        when(gate.tryReserve(anyLong(),anyInt(),anyInt())).thenReturn(true);
        String key=UUID.randomUUID().toString();
        var request=new PaymentPrepareRequest(method,id,"CRYPTO".equals(method)?7L:null);
        return new Fixture(id,Long.parseLong(payments.prepare(id,id,request,key).paymentId()),key,request);
    }
    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void editingAddressBookCannotChangePreparedOrder(String method) {
        Fixture f=prepare(method);
        jdbc.update("UPDATE user_addresses SET recipient_name='변경 수령인',address1='변경 주소',delivery_request='경비실' WHERE address_id=?",f.id);
        var paid=payments.completeOwnedPayment(f.id,f.payment,method);
        assertThat(jdbc.queryForObject("SELECT recipient_name FROM participations WHERE participation_id=?",String.class,Long.parseLong(paid.participationId()))).isEqualTo("최초 수령인");
        assertThat(jdbc.queryForObject("SELECT address1 FROM participations WHERE participation_id=?",String.class,Long.parseLong(paid.participationId()))).isEqualTo("최초 주소");
    }
    @ParameterizedTest @ValueSource(strings={"CARD","KAKAOPAY","CRYPTO"})
    void deletingAddressBookStillAllowsIdenticalRetryAndCompletion(String method) {
        Fixture f=prepare(method);
        jdbc.update("DELETE FROM user_addresses WHERE address_id=?",f.id);
        assertThat(payments.prepare(f.id,f.id,f.request,f.key).paymentId()).isEqualTo(String.valueOf(f.payment));
        assertThat(payments.completeOwnedPayment(f.id,f.payment,method).status()).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT address_id FROM payments WHERE payment_id=?",Long.class,f.payment)).isEqualTo(f.id);
    }
}
