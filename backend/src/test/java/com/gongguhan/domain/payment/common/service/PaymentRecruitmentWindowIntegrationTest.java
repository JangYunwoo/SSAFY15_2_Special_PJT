package com.gongguhan.domain.payment.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:recruitwindow;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.scheduling.enabled=false", "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
@Transactional
class PaymentRecruitmentWindowIntegrationTest {
    @Autowired PaymentSlotReservationTransaction transaction;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void actualJpaPathDoesNotPersistSlotOrPaymentOutsideWindow(boolean beforeStart) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = beforeStart ? now.plusDays(1) : now.minusDays(2);
        LocalDateTime end = beforeStart ? now.plusDays(2) : now.minusDays(1);
        jdbc.update("""
                INSERT INTO group_buys (group_buy_id, product_id, seller_id, target_participants,
                unit_share_amount, shipping_fee, current_participants, reserved_slots, recruit_start_at,
                recruit_end_at, status, version, created_at, updated_at)
                VALUES (9781,501,10,5,1000,0,0,0,?,?,'RECRUITING',0,NOW(),NOW())
                """, start, end);
        jdbc.update("""
                INSERT INTO user_addresses (address_id,user_id,recipient_name,recipient_phone,zipcode,
                address1,address2,is_default,created_at,updated_at)
                VALUES (9581,9181,'QA','01000000000','00000','QA','',TRUE,NOW(),NOW())
                """);
        assertThatThrownBy(() -> transaction.execute(9181L, 9781L,
                new PaymentPrepareRequest("CARD", 9581L, null), "recruit-window-integration"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.GROUP_BUY_NOT_RECRUITING));
        assertThat(jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=9781", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE group_buy_id=9781", Integer.class))
                .isZero();
    }
}
