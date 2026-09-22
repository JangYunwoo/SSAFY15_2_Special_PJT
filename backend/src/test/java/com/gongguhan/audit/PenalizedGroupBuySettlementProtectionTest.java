package com.gongguhan.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gongguhan.domain.refund.service.SsafyTransferClient;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.service.SettlementBatchService;
import com.gongguhan.domain.settlement.service.SettlementRecordService;
import com.gongguhan.domain.settlement.service.SettlementTransferService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * §8 검증: 위반이 확정(CONFIRMED)된 공동구매의 정산은 판매자에게 자동 지급하지 않고
 * 환불 가능한 상태로 묶는다.
 *
 * <ul>
 *   <li>Part 1 — 정산 배치 대상에서 제외해 에스크로 HELD를 유지(미정산). 제외 사유는 SELLER_PENALIZED.
 *   <li>Part 2 — 확정 전에 이미 만들어진 PENDING 정산은 송금 직전에 보류(HOLD)해 판매자에게 나가지 않게 한다.
 * </ul>
 *
 * <p>이미 지급 완료(COMPLETED)된 정산은 실행 대상이 아니라 건드리지 않는다(클로백 없음).
 * 판매자의 다른 공구에는 영향이 없다(group_buy 단위). 실제 환불은 관리자 기존 도구로 처리한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:penaltysettlement;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.admin.seed-enabled=false"})
@ActiveProfiles("test")
class PenalizedGroupBuySettlementProtectionTest {

    private static final Long GROUP_BUY_ID = 720001L;
    private static final Long SELLER_ID = 720201L;
    private static final long SHARE = 50_000L;
    private static final long SHIPPING = 4_000L;

    @Autowired SettlementBatchService batchService;
    @Autowired SettlementTargetRepository targetRepository;
    @Autowired SettlementRepository settlementRepository;
    @Autowired SettlementRecordService recordService;
    @Autowired SettlementTransferService transferService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean SsafyTransferClient transferClient;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM settlement_items");
        jdbc.update("DELETE FROM settlements");
        jdbc.update("DELETE FROM escrows");
        jdbc.update("DELETE FROM participations");
        jdbc.update("DELETE FROM seller_penalties WHERE group_buy_id = ?", GROUP_BUY_ID);
        jdbc.update("DELETE FROM group_buys WHERE group_buy_id = ?", GROUP_BUY_ID);
        jdbc.update("""
                INSERT INTO group_buys
                  (group_buy_id, product_id, seller_id, product_price, target_participants,
                   unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                   expected_platform_fee, expected_settlement_amount,
                   recruit_start_at, recruit_end_at, current_participants, reserved_slots,
                   status, version, created_at, updated_at)
                VALUES (?, 1, ?, 1000000, 20, ?, ?, 0, 0.0500, 50000, 1030000,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 0, 'DELIVERED', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, GROUP_BUY_ID, SELLER_ID, SHARE, SHIPPING);
    }

    private void insertConfirmed(long participationId) {
        jdbc.update("""
                INSERT INTO participations
                  (participation_id, group_buy_id, user_id, recipient_name, recipient_phone,
                   zipcode, address1, share_amount, shipping_fee, total_amount,
                   status, settlement_eligible_at, joined_at)
                VALUES (?, ?, 1, '받는사람', '01000000000', '06236', '서울시', ?, ?, ?,
                        'CONFIRMED', DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)
                """, participationId, GROUP_BUY_ID, SHARE, SHIPPING, SHARE + SHIPPING);
        jdbc.update("""
                INSERT INTO escrows
                  (participation_id, group_buy_id, amount, refunded_amount, status, held_at)
                VALUES (?, ?, ?, 0, 'HELD', CURRENT_TIMESTAMP)
                """, participationId, GROUP_BUY_ID, SHARE + SHIPPING);
    }

    private void insertPenalty(String status) {
        jdbc.update("""
                INSERT INTO seller_penalties
                  (seller_id, group_buy_id, reason_type, status, detected_at)
                VALUES (?, ?, 'ITEM_MISMATCH', ?, CURRENT_TIMESTAMP)
                """, SELLER_ID, GROUP_BUY_ID, status);
    }

    private Long createPendingSettlement() {
        return settlementRepository.save(Settlement.create(
                GROUP_BUY_ID, SELLER_ID, 1, 1, SHARE, SHIPPING, 0L,
                new BigDecimal("0.0500"), LocalDateTime.now())).getSettlementId();
    }

    private String settlementStatus(Long settlementId) {
        return jdbc.queryForObject("SELECT status FROM settlements WHERE settlement_id = ?",
                String.class, settlementId);
    }

    // ------------------------------------------------------------------
    // Part 1 — 정산 대상 제외
    // ------------------------------------------------------------------

    @Test
    void confirmedPenaltyExcludesGroupBuyFromSettlementBatch() {
        insertConfirmed(720501L);
        insertPenalty(SellerPenaltyStatus.CONFIRMED);

        int created = batchService.run();

        System.out.printf("AUDIT part1 penalty=CONFIRMED batchCreated=%d targets=%d%n",
                created, targetRepository.findTargets(LocalDateTime.now()).size());
        assertThat(created).as("확정 위반 공구는 정산 대상에서 제외된다").isZero();
        assertThat(settlementRepository.count()).isZero();
        // 에스크로가 HELD로 남아 환불 가능한 상태를 유지한다.
        assertThat(jdbc.queryForObject("SELECT status FROM escrows WHERE participation_id = 720501",
                String.class)).isEqualTo("HELD");
    }

    @Test
    void unconfirmedPenaltyDoesNotExcludeGroupBuy() {
        insertConfirmed(720502L);
        insertPenalty(SellerPenaltyStatus.CANDIDATE); // 후보 상태는 확정이 아니므로 정산 진행

        int created = batchService.run();

        System.out.printf("AUDIT part1 penalty=CANDIDATE batchCreated=%d%n", created);
        assertThat(created).as("후보(미확정) 위반은 정산을 막지 않는다").isEqualTo(1);
    }

    @Test
    void excludedReportShowsSellerPenalizedReason() {
        insertConfirmed(720503L);
        insertPenalty(SellerPenaltyStatus.CONFIRMED);

        List<SettlementTargetRepository.ExcludedParticipation> excluded =
                targetRepository.findExcluded(GROUP_BUY_ID);

        assertThat(excluded).hasSize(1);
        assertThat(excluded.get(0).excludedReason())
                .isEqualTo(SettlementTargetRepository.ExcludedReason.SELLER_PENALIZED);
    }

    // ------------------------------------------------------------------
    // Part 2 — 이미 만들어진 PENDING 정산의 송금 보류
    // ------------------------------------------------------------------

    @Test
    void confirmedPenaltyHoldsPendingSettlementInsteadOfTransferring() {
        Long settlementId = createPendingSettlement();
        insertPenalty(SellerPenaltyStatus.CONFIRMED);

        Settlement result = transferService.execute(settlementId);

        System.out.printf("AUDIT part2 penalty=CONFIRMED execute->status=%s%n", result.getStatus());
        assertThat(result.getStatus()).isEqualTo(Settlement.STATUS_HOLD);
        assertThat(settlementStatus(settlementId)).isEqualTo(Settlement.STATUS_HOLD);
        // 판매자에게 실제 송금은 일어나지 않는다.
        verify(transferClient, never()).transfer(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void noPenaltyLeavesPendingSettlementExecutable() {
        Long settlementId = createPendingSettlement();
        // 위반 없음: holdIfSellerPenalized는 아무것도 하지 않고 null을 돌려준다.

        Settlement held = recordService.holdIfSellerPenalized(settlementId);

        assertThat(held).isNull();
        assertThat(settlementStatus(settlementId)).isEqualTo(Settlement.STATUS_PENDING);
    }

    /** SellerPenalty 상태 상수를 테스트에서 직접 참조하지 않기 위한 로컬 별칭. */
    private static final class SellerPenaltyStatus {
        static final String CANDIDATE = "CANDIDATE";
        static final String CONFIRMED = "CONFIRMED";
    }
}
