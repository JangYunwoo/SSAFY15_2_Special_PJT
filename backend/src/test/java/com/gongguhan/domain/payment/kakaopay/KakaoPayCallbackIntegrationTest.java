package com.gongguhan.domain.payment.kakaopay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.kakaopay.service.*;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 실제 서비스·JPA·H2 상태 변경 검증. Redis와 외부 결제사는 대체하고 각 테스트를 롤백한다. */
@SpringBootTest(properties = {"app.kakaopay.secret-key=callback-fixture-secret", "app.scheduling.enabled=false"})
@ActiveProfiles("test")
@Transactional
class KakaoPayCallbackIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @Autowired KakaoPayService service;
    @Autowired KakaoPayRecordService records;
    @Autowired KakaoPayStateSigner signer;
    @MockitoBean GroupBuySlotGate gate;
    @MockitoBean KakaoPayGateway gateway;
    private LocalDateTime expiry;

    @BeforeEach
    void fixture() {
        expiry = LocalDateTime.now(clock).plusMinutes(5);
        jdbc.update("""
                INSERT INTO group_buys (group_buy_id, product_id, seller_id, target_participants,
                  unit_share_amount, shipping_fee, current_participants, reserved_slots, recruit_end_at,
                  status, version, created_at, updated_at)
                VALUES (9701, 501, 10, 5, 1000, 0, 0, 1, ?, 'RECRUITING', 0, NOW(), NOW())
                """, expiry.plusDays(1));
        jdbc.update("""
                INSERT INTO user_addresses (address_id, user_id, recipient_name, recipient_phone,
                  zipcode, address1, address2, is_default, created_at, updated_at)
                VALUES (9501, 9100, '테스트', '01000000000', '06236', '테스트 주소', '', TRUE, NOW(), NOW())
                """);
        jdbc.update("""
                INSERT INTO payments (payment_id, group_buy_id, user_id, address_id, method, krw_amount,
                  slot_expires_at, slot_marker, idempotency_key, status, created_at, updated_at)
                VALUES (9901, 9701, 9100, 9501, 'KAKAOPAY', 1000, ?, 'Y', 'callback-fixture',
                  'PROCESSING', NOW(), NOW())
                """, expiry);
        records.recordReady(9901L, "fixture-tid", "PAYMENT-9901", "9100", 1000,
                "https://pay.example/fixture", "current-nonce", expiry);
        em.flush();
        em.clear();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void validCallbackEndsSlotAndConsumesState(boolean cancel) {
        end(cancel, state("current-nonce"));
        em.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=9901", String.class))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT fail_reason FROM payments WHERE payment_id=9901", String.class))
                .isEqualTo(cancel ? "KAKAOPAY_USER_CANCELLED" : "KAKAOPAY_AUTH_FAILED");
        assertThat(slots()).isZero();
        assertThat(consumed()).isTrue();
        verify(gate).release(9701L);
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void oldNonceDoesNotChangePaymentOrSlot(boolean cancel) {
        var error = assertThrows(BusinessException.class, () -> end(cancel, state("old-nonce")));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertUnchanged();
    }

    @Test
    void storedExpiryIsCheckedEvenWhenSignedExpiryIsFuture() {
        jdbc.update("UPDATE kakaopay_payments SET state_expires_at=? WHERE payment_id=9901",
                LocalDateTime.now(clock).minusMinutes(1));
        var error = assertThrows(BusinessException.class, () -> end(true, state("current-nonce")));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_SLOT_EXPIRED);
        assertUnchanged();
    }

    @Test
    void missingReadyDoesNotChangePaymentOrSlot() {
        jdbc.update("DELETE FROM kakaopay_payments WHERE payment_id=9901");
        var error = assertThrows(BusinessException.class, () -> end(false, state("current-nonce")));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(slots()).isEqualTo(1);
        verifyNoInteractions(gate, gateway);
    }

    @Test
    void callbackReplayCannotReleaseTwice() {
        String state = state("current-nonce");
        end(true, state);
        em.flush();
        em.clear();
        assertThrows(BusinessException.class, () -> end(false, state));
        assertThat(slots()).isZero();
        verify(gate, times(1)).release(9701L);
        verifyNoInteractions(gateway);
    }

    private String state(String nonce) {
        return signer.sign(new KakaoPayStateSigner.StateClaims(9901L, 9100L,
                expiry.toEpochSecond(ZoneOffset.UTC), nonce));
    }
    private void end(boolean cancel, String state) {
        if (cancel) service.cancelFlow(9100L, 9901L, state);
        else service.failFlow(9100L, 9901L, state);
    }
    private int slots() {
        return jdbc.queryForObject("SELECT reserved_slots FROM group_buys WHERE group_buy_id=9701", Integer.class);
    }
    private boolean consumed() {
        return jdbc.queryForObject("SELECT state_consumed_at IS NOT NULL FROM kakaopay_payments WHERE payment_id=9901", Boolean.class);
    }
    private void assertUnchanged() {
        em.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE payment_id=9901", String.class))
                .isEqualTo("PROCESSING");
        assertThat(slots()).isEqualTo(1);
        assertThat(consumed()).isFalse();
        verifyNoInteractions(gate, gateway);
    }
}
