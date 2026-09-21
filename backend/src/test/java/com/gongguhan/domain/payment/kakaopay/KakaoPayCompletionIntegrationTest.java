package com.gongguhan.domain.payment.kakaopay;

import static org.assertj.core.api.Assertions.assertThat;

import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayGateway;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayRecordService;
import com.gongguhan.domain.payment.kakaopay.service.KakaoPayStateSigner;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 외부 승인 검증 뒤 결제·참여·에스크로·인원 확정이 함께 커밋되는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KakaoPayCompletionIntegrationTest {

    private static final Long PAYMENT_ID = 9_001L;
    private static final Long USER_ID = 100L;
    private static final Long GROUP_BUY_ID = 701L;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KakaoPayRecordService recordService;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Test
    void verifiedApprovalAtomicallyCreatesParticipationAndEscrow() {
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(5);
        insertPaymentFixture(expiresAt);
        recordService.recordReady(
                PAYMENT_ID, "T123", "PAYMENT-9001", "100", 54_000,
                "https://pay.example/bridge", "nonce", expiresAt);

        KakaoPayStateSigner.StateClaims claims = new KakaoPayStateSigner.StateClaims(
                PAYMENT_ID, USER_ID, expiresAt.toEpochSecond(ZoneOffset.UTC), "nonce");
        KakaoPayGateway.ApproveResult approved = new KakaoPayGateway.ApproveResult(
                "A123", "T123", "PAYMENT-9001", "100", 54_000, 4_909, "MONEY");

        PaymentResponse response = recordService.complete(USER_ID, claims, approved);
        entityManager.flush();
        entityManager.clear();

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(response.participationId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM participations WHERE group_buy_id = ? AND user_id = ?",
                Long.class, GROUP_BUY_ID, USER_ID)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM escrows WHERE group_buy_id = ? AND status = 'HELD'",
                Long.class, GROUP_BUY_ID)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE payment_id = ?", String.class, PAYMENT_ID))
                .isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state_consumed_at IS NOT NULL FROM kakaopay_payments WHERE payment_id = ?",
                Boolean.class, PAYMENT_ID)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT current_participants, reserved_slots FROM group_buys WHERE group_buy_id = ?",
                GROUP_BUY_ID))
                .containsEntry("current_participants", 1)
                .containsEntry("reserved_slots", 0);
    }

    private void insertPaymentFixture(LocalDateTime expiresAt) {
        jdbcTemplate.update("""
                INSERT INTO group_buys
                  (group_buy_id, product_id, seller_id, target_participants, unit_share_amount,
                   shipping_fee, current_participants, reserved_slots, recruit_end_at, status,
                   version, created_at, updated_at)
                VALUES (?, 501, 10, 10, 50000, 4000, 0, 1, ?, 'RECRUITING', 0, NOW(), NOW())
                """, GROUP_BUY_ID, expiresAt.plusDays(1));
        jdbcTemplate.update("""
                INSERT INTO user_addresses
                  (address_id, user_id, recipient_name, recipient_phone, zipcode, address1,
                   address2, is_default, created_at, updated_at)
                VALUES (501, ?, '김구매', '01012345678', '06236', '서울시 강남구',
                        '101호', TRUE, NOW(), NOW())
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO payments
                  (payment_id, group_buy_id, user_id, address_id, method, krw_amount,
                   slot_expires_at, slot_marker, idempotency_key, status, created_at, updated_at)
                VALUES (?, ?, ?, 501, 'KAKAOPAY', 54000, ?, 'Y', 'ready-idem',
                        'PROCESSING', NOW(), NOW())
                """, PAYMENT_ID, GROUP_BUY_ID, USER_ID, expiresAt);
        // ready/approve 오케스트레이터를 건너뛰는 원자성 시험이므로 준비 스냅샷을 명시한다.
        jdbcTemplate.update("UPDATE payments SET shipping_recipient_name='김구매',shipping_recipient_phone='01012345678',shipping_zipcode='06236',shipping_address1='서울시 강남구',shipping_address2='101호' WHERE payment_id=?",PAYMENT_ID);
    }
}
