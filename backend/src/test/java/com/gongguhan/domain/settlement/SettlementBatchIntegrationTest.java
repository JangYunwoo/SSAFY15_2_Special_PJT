package com.gongguhan.domain.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.settlement.dto.SettlementDetailResponse;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.entity.SettlementItem;
import com.gongguhan.domain.settlement.repository.SettlementItemRepository;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.service.SettlementBatchService;
import com.gongguhan.domain.settlement.service.SettlementQueryService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 정산 배치 통합 검증 (FR-STL-01~05).
 *
 * <p><b>mock 으로는 확인할 수 없는 것들을 봅니다.</b>
 *
 * <ul>
 *   <li>대상 조회 SQL — 참여·공동구매·에스크로·정산항목 4중 조인이라 리포지토리를
 *       mock 하면 한 번도 실행되지 않습니다</li>
 *   <li>{@code settlement_items.UNIQUE(participation_id)} — 배치를 두 번 돌렸을 때
 *       같은 참여 건이 두 정산에 들어가지 않는지</li>
 * </ul>
 *
 * <p>이중 정산이 통과하면 판매자에게 같은 돈을 두 번 지급합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SettlementBatchIntegrationTest {

    private static final Long GROUP_BUY_ID = 1L;
    private static final Long SELLER_ID = 201L;
    private static final long SHARE = 50_000L;
    private static final long SHIPPING = 4_000L;

    @Autowired
    private SettlementBatchService batchService;

    @Autowired
    private SettlementQueryService queryService;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementItemRepository settlementItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM settlement_items");
        jdbcTemplate.update("DELETE FROM settlements");
        jdbcTemplate.update("DELETE FROM escrows");
        jdbcTemplate.update("DELETE FROM participations");
        jdbcTemplate.update("DELETE FROM group_buys");

        jdbcTemplate.update("""
                INSERT INTO group_buys
                  (group_buy_id, product_id, seller_id, product_price, target_participants,
                   unit_share_amount, shipping_fee, rounding_diff, platform_fee_rate,
                   expected_platform_fee, expected_settlement_amount,
                   recruit_start_at, recruit_end_at, current_participants, reserved_slots,
                   status, version, created_at, updated_at)
                VALUES (?, 1, ?, 1000000, 20, ?, ?, 0, 0.0500, 50000, 1030000,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 3, 0, 'DELIVERED', 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, GROUP_BUY_ID, SELLER_ID, SHARE, SHIPPING);
    }

    /**
     * @param eligibleDaysAgo 정산 가능 시점이 며칠 전인지. 양수면 정산 대상
     */
    private void insertConfirmed(Long participationId, int eligibleDaysAgo) {
        jdbcTemplate.update("""
                INSERT INTO participations
                  (participation_id, group_buy_id, user_id, recipient_name, recipient_phone,
                   zipcode, address1, share_amount, shipping_fee, total_amount,
                   status, settlement_eligible_at, joined_at)
                VALUES (?, ?, 1, '받는사람', '01000000000', '06236', '서울시', ?, ?, ?,
                        'CONFIRMED', DATEADD('DAY', ?, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)
                """, participationId, GROUP_BUY_ID, SHARE, SHIPPING, SHARE + SHIPPING,
                -eligibleDaysAgo);
        insertEscrow(participationId, "HELD", 0L);
    }

    private void insertEscrow(Long participationId, String status, long refundedAmount) {
        jdbcTemplate.update("""
                INSERT INTO escrows
                  (participation_id, group_buy_id, amount, refunded_amount, status, held_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, participationId, GROUP_BUY_ID, SHARE + SHIPPING, refundedAmount, status);
    }

    private void insertWithStatus(Long participationId, String status) {
        jdbcTemplate.update("""
                INSERT INTO participations
                  (participation_id, group_buy_id, user_id, recipient_name, recipient_phone,
                   zipcode, address1, share_amount, shipping_fee, total_amount,
                   status, settlement_eligible_at, joined_at)
                VALUES (?, ?, 1, '받는사람', '01000000000', '06236', '서울시', ?, ?, ?,
                        ?, DATEADD('DAY', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)
                """, participationId, GROUP_BUY_ID, SHARE, SHIPPING, SHARE + SHIPPING, status);
    }

    // ------------------------------------------------------------------
    // 집계
    // ------------------------------------------------------------------

    @Test
    @DisplayName("구매확정되고 기한이 지난 건을 모아 정산을 만든다")
    void aggregatesEligibleParticipations() {
        insertConfirmed(501L, 1);
        insertConfirmed(502L, 1);

        int created = batchService.run();

        assertThat(created).isEqualTo(1);
        Settlement settlement = settlementRepository.findAll().get(0);
        assertThat(settlement.getConfirmedCount()).isEqualTo(2);
        assertThat(settlement.getShareTotal()).isEqualTo(100_000L);
        assertThat(settlement.getShippingTotal()).isEqualTo(8_000L);
        // 수수료는 배송비를 뺀 분담금에만 5%
        assertThat(settlement.getPlatformFee()).isEqualTo(5_000L);
        assertThat(settlement.getSettlementAmount()).isEqualTo(103_000L);
    }

    /**
     * 배치를 두 번 돌려도 같은 참여 건이 두 정산에 들어가면 안 됩니다
     * ({@code settlement_items.UNIQUE(participation_id)}, NFR-OP-03).
     */
    @Test
    @DisplayName("배치를 두 번 돌려도 같은 건이 재정산되지 않는다")
    void secondRunDoesNotResettle() {
        insertConfirmed(501L, 1);
        batchService.run();

        int created = batchService.run();

        assertThat(created).isZero();
        assertThat(settlementRepository.count()).isEqualTo(1);
        assertThat(settlementItemRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("정산 가능 시점 전인 건은 제외한다 - 배송완료 7일 안에 반품이 들어올 수 있다")
    void excludesNotYetEligible() {
        insertConfirmed(501L, -3);

        assertThat(batchService.run()).isZero();
        assertThat(settlementRepository.count()).isZero();
    }

    @Test
    @DisplayName("구매확정되지 않은 건은 제외한다 - 배송완료만으로는 정산하지 않는다")
    void excludesUnconfirmed() {
        insertWithStatus(501L, "DELIVERED");
        insertEscrow(501L, "HELD", 0L);

        assertThat(batchService.run()).isZero();
    }

    @Test
    @DisplayName("이미 환불된 건은 제외한다 - 보관 중인 돈이 없다")
    void excludesRefunded() {
        insertWithStatus(501L, "CONFIRMED");
        insertEscrow(501L, "REFUNDED", SHARE + SHIPPING);

        assertThat(batchService.run()).isZero();
    }

    /**
     * 54,000원 중 42,000원을 환불하면 남은 12,000원은 원배송비와 반품배송비이지
     * 분담금이 아닙니다. 수수료는 분담금에만 매기는데 그 배분 규칙이 명세에 없어,
     * 임의로 정하면 판매자가 받을 돈이 조용히 달라집니다. 자동 배치에서 빼고
     * 제외 사유로 드러냅니다.
     */
    @Test
    @DisplayName("부분 환불된 건은 자동 배치에서 제외한다 - 잔액 배분 규칙이 아직 없다")
    void excludesPartiallyRefunded() {
        insertWithStatus(501L, "CONFIRMED");
        insertEscrow(501L, "HELD", 42_000L);

        assertThat(batchService.run()).isZero();
    }

    // ------------------------------------------------------------------
    // 회차
    // ------------------------------------------------------------------

    /**
     * 같은 공동구매의 참여자들이 동시에 확정하지 않습니다. 전원을 기다리면 판매자는
     * 한 명 때문에 전체 대금을 못 받습니다.
     */
    @Test
    @DisplayName("나중에 확정된 건은 다음 회차로 정산된다")
    void laterConfirmationsFormNextRound() {
        insertConfirmed(501L, 1);
        batchService.run();

        insertConfirmed(502L, 1);
        batchService.run();

        List<Settlement> settlements =
                settlementRepository.findByGroupBuyIdOrderByRoundNoAsc(GROUP_BUY_ID);
        assertThat(settlements).hasSize(2);
        assertThat(settlements).extracting(Settlement::getRoundNo).containsExactly(1, 2);
    }

    /** 회차마다 더하면 판매자가 같은 차액을 여러 번 받습니다. */
    @Test
    @DisplayName("절상 차액은 1회차에만 가산한다")
    void roundingDiffOnlyOnFirstRound() {
        jdbcTemplate.update("UPDATE group_buys SET rounding_diff = 500 WHERE group_buy_id = ?",
                GROUP_BUY_ID);
        insertConfirmed(501L, 1);
        batchService.run();
        insertConfirmed(502L, 1);
        batchService.run();

        List<Settlement> settlements =
                settlementRepository.findByGroupBuyIdOrderByRoundNoAsc(GROUP_BUY_ID);
        assertThat(settlements.get(0).getRoundingDiff()).isEqualTo(500L);
        assertThat(settlements.get(1).getRoundingDiff()).isZero();
    }

    // ------------------------------------------------------------------
    // 연쇄 효과
    // ------------------------------------------------------------------

    @Test
    @DisplayName("정산되면 에스크로가 해제되고 참여 건이 정산 완료가 된다")
    void releasesEscrowAndMarksParticipation() {
        insertConfirmed(501L, 1);

        batchService.run();

        String escrowStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM escrows WHERE participation_id = 501", String.class);
        String participationStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM participations WHERE participation_id = 501", String.class);

        assertThat(escrowStatus).isEqualTo("RELEASED");
        assertThat(participationStatus).isEqualTo("SETTLED");
    }

    // ------------------------------------------------------------------
    // 조회 (STL-02)
    // ------------------------------------------------------------------

    /**
     * 판매자가 가장 먼저 묻는 것은 "왜 3명 중 2명만 정산됐나" 입니다. 포함 목록만
     * 주면 답할 수 없습니다.
     */
    @Test
    @DisplayName("상세 조회는 제외된 건과 사유를 함께 보여준다")
    void detailShowsExcludedWithReason() {
        insertConfirmed(501L, 1);
        insertWithStatus(502L, "DELIVERED");
        insertEscrow(502L, "HELD", 0L);
        batchService.run();

        Long settlementId = settlementRepository.findAll().get(0).getSettlementId();
        SettlementDetailResponse detail = queryService.getSettlement(SELLER_ID, settlementId);

        assertThat(detail.items())
                .extracting(SettlementDetailResponse.Item::participationId,
                        SettlementDetailResponse.Item::included,
                        SettlementDetailResponse.Item::excludedReason)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("501", true, null),
                        org.assertj.core.groups.Tuple.tuple("502", false,
                                SettlementTargetRepository.ExcludedReason.NOT_CONFIRMED));
    }

    @Test
    @DisplayName("포함된 건의 금액은 분담금과 배송비의 합이다")
    void includedItemAmountIsShareePlusShipping() {
        insertConfirmed(501L, 1);
        batchService.run();

        Long settlementId = settlementRepository.findAll().get(0).getSettlementId();
        SettlementDetailResponse detail = queryService.getSettlement(SELLER_ID, settlementId);

        assertThat(detail.items().get(0).amount()).isEqualTo(SHARE + SHIPPING);
    }

    /**
     * 이중 정산의 <b>최종 방어선</b>을 직접 확인합니다.
     *
     * <p>애플리케이션 조회로 거르는 것과 에스크로 해제 가드가 먼저 막아주지만, 배치
     * 두 개가 동시에 돌면 둘 다 그 검사를 통과할 수 있습니다. 통과하면 판매자에게 같은
     * 돈을 두 번 지급합니다. 그래서 마지막은 DB 가 막아야 합니다.
     */
    @Test
    @DisplayName("같은 참여 건을 두 정산에 넣을 수 없다 - DB 제약이 최종 방어선")
    void databaseRejectsParticipationInTwoSettlements() {
        insertConfirmed(501L, 1);
        batchService.run();

        Long otherSettlementId = settlementRepository.save(Settlement.create(
                GROUP_BUY_ID, SELLER_ID, 99, 1, SHARE, SHIPPING, 0L,
                new java.math.BigDecimal("0.0500"), java.time.LocalDateTime.now()))
                .getSettlementId();

        assertThatThrownBy(() -> settlementItemRepository.saveAndFlush(
                SettlementItem.of(otherSettlementId, 501L, SHARE, SHIPPING)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
