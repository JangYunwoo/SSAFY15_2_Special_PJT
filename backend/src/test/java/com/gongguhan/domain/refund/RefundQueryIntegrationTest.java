package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.refund.dto.RefundResponse;
import com.gongguhan.domain.refund.service.RefundQueryService;
import com.gongguhan.domain.refund.service.RefundQueryService.RefundListResult;
import com.gongguhan.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * RFD-01~03 조회 검증.
 *
 * <p><b>mock 으로는 검증할 수 없는 부분이 핵심입니다.</b> {@code refunds} 에는
 * {@code user_id} 가 없어 환불의 주인을 두 갈래로 찾습니다.
 *
 * <pre>
 *   참여 건 환불 : refunds.participation_id -> participations.user_id
 *   입금 환불    : refunds.deposit_id -> crypto_deposits.payment_id -> payments.user_id
 * </pre>
 *
 * <p>{@code COALESCE} 로 두 갈래를 합치는 SQL 은 리포지토리를 mock 하면 한 번도
 * 실행되지 않습니다. 실제로 쿼리를 돌려 <b>두 경로 모두에서 주인을 찾는지</b>,
 * 그리고 <b>남의 환불이 섞이지 않는지</b>를 확인합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RefundQueryIntegrationTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;

    @Autowired
    private RefundQueryService refundQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM refunds");
        jdbcTemplate.update("DELETE FROM crypto_deposits");
        jdbcTemplate.update("DELETE FROM participations");
        jdbcTemplate.update("DELETE FROM payments");

        // 참여 건 경로: 내 참여 1건, 남의 참여 1건
        insertParticipation(501L, ME);
        insertParticipation(502L, OTHER);

        // 입금 경로: 결제 -> 입금. 참여 건이 없는 환불의 주인을 여기서 찾는다.
        insertPayment(900L, ME);
        insertDeposit(7001L, 900L);

        insertParticipationRefund(1L, 501L, "COMPLETED");   // 내 것
        insertParticipationRefund(2L, 502L, "COMPLETED");   // 남의 것
        insertDepositRefund(3L, 7001L, "FAILED");           // 내 것 (참여 건 없음)
    }

    private void insertParticipation(Long participationId, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO participations
                  (participation_id, group_buy_id, user_id, recipient_name, recipient_phone,
                   zipcode, address1, share_amount, shipping_fee, total_amount, status, joined_at)
                VALUES (?, 1, ?, '받는사람', '01000000000', '06236', '서울시',
                        50000, 4000, 54000, 'PAID', CURRENT_TIMESTAMP)
                """, participationId, userId);
    }

    private void insertPayment(Long paymentId, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO payments
                  (payment_id, group_buy_id, user_id, method, krw_amount, slot_expires_at,
                   idempotency_key, status, created_at, updated_at)
                VALUES (?, 1, ?, 'CRYPTO', 54000, CURRENT_TIMESTAMP, ?, 'PAID',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, paymentId, userId, "idem-" + paymentId);
    }

    private void insertDeposit(Long depositId, Long paymentId) {
        jdbcTemplate.update("""
                INSERT INTO crypto_deposits
                  (deposit_id, payment_id, chain_id, tx_hash, log_index, block_number,
                   token_contract, from_address, to_address, raw_amount, asset_amount,
                   confirmations, status, detected_at)
                VALUES (?, ?, 80002, ?, 2, 47281903, '0xtoken', '0xfrom', '0xto',
                        '39528990', 39.528990, 5, 'MATCHED', CURRENT_TIMESTAMP)
                """, depositId, paymentId, "0xhash" + depositId);
    }

    private void insertParticipationRefund(Long refundId, Long participationId, String status) {
        jdbcTemplate.update("""
                INSERT INTO refunds
                  (refund_id, participation_id, amount, reason_type, refund_method,
                   to_account_no, status, refund_marker, created_at)
                VALUES (?, ?, 54000, 'RECRUIT_FAILED', 'ACCOUNT_TRANSFER',
                        '0016174648358792', ?, 'Y', CURRENT_TIMESTAMP)
                """, refundId, participationId, status);
    }

    private void insertDepositRefund(Long refundId, Long depositId, String status) {
        jdbcTemplate.update("""
                INSERT INTO refunds
                  (refund_id, deposit_id, amount, reason_type, refund_method,
                   to_address, status, refund_marker, fail_reason, created_at)
                VALUES (?, ?, 54000, 'UNMATCHED_DEPOSIT', 'CRYPTO',
                        '0xbuyer', ?, NULL, '코인 환불 송금은 아직 지원되지 않습니다.',
                        CURRENT_TIMESTAMP)
                """, refundId, depositId, status);
    }

    // ------------------------------------------------------------------
    // 소유자 판정 (COALESCE 두 갈래)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("참여 건 환불과 입금 환불을 모두 내 것으로 찾는다 - 주인을 찾는 경로가 둘이다")
    void findsRefundsThroughBothOwnershipPaths() {
        RefundListResult result = refundQueryService.getMyRefunds(ME, null, PageRequest.of(0, 20));

        assertThat(result.body())
                .extracting(RefundResponse::refundId)
                .containsExactlyInAnyOrder("1", "3");
        assertThat(result.meta().totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("남의 환불은 목록에 섞이지 않는다")
    void excludesOtherUsersRefunds() {
        RefundListResult result = refundQueryService.getMyRefunds(ME, null, PageRequest.of(0, 20));

        assertThat(result.body())
                .extracting(RefundResponse::refundId)
                .doesNotContain("2");
    }

    /**
     * count 쿼리에서 조인을 빼면 소유자 조건이 동작하지 않아 전체 건수를 세게 됩니다.
     * 목록은 2건인데 총계가 3건으로 나오면 페이지 수가 틀립니다.
     */
    @Test
    @DisplayName("총 건수도 소유자 기준으로 센다")
    void countsOnlyMyRefunds() {
        RefundListResult result = refundQueryService.getMyRefunds(ME, null, PageRequest.of(0, 1));

        assertThat(result.body()).hasSize(1);
        assertThat(result.meta().totalElements()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // 상태 필터
    // ------------------------------------------------------------------

    @Test
    @DisplayName("상태로 거를 수 있다")
    void filtersByStatus() {
        RefundListResult result = refundQueryService.getMyRefunds(ME, "FAILED", PageRequest.of(0, 20));

        assertThat(result.body()).extracting(RefundResponse::refundId).containsExactly("3");
    }

    @Test
    @DisplayName("소문자 상태도 받아준다")
    void acceptsLowercaseStatus() {
        RefundListResult result = refundQueryService.getMyRefunds(ME, "failed", PageRequest.of(0, 20));

        assertThat(result.body()).hasSize(1);
    }

    /**
     * 정의되지 않은 값을 그대로 SQL 에 넘기면 조용히 빈 목록이 나와 사용자는 "환불이
     * 없다" 고 오해합니다. 오타를 400 으로 되돌립니다.
     */
    @Test
    @DisplayName("없는 상태값은 400 으로 거부한다 - 빈 목록으로 오해시키지 않는다")
    void rejectsUnknownStatus() {
        assertThatThrownBy(() ->
                refundQueryService.getMyRefunds(ME, "DONE", PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("지원하지 않는 환불 상태");
    }

    // ------------------------------------------------------------------
    // 상세 조회와 소유권
    // ------------------------------------------------------------------

    @Test
    @DisplayName("내 환불 상세를 조회한다")
    void getsMyRefundDetail() {
        RefundResponse response = refundQueryService.getMyRefund(ME, 1L);

        assertThat(response.refundId()).isEqualTo("1");
        assertThat(response.amount()).isEqualTo(54_000L);
        assertThat(response.reasonType()).isEqualTo("RECRUIT_FAILED");
    }

    /**
     * 403 을 주면 "그 ID 는 존재한다" 를 알려주는 셈이라 식별자를 바꿔가며 자원을
     * 열거할 수 있습니다. 에스크로 조회와 같은 규칙입니다.
     */
    @Test
    @DisplayName("남의 환불 상세는 403 이 아니라 404 - 자원 열거 차단")
    void hidesOtherUsersRefundDetail() {
        assertThatThrownBy(() -> refundQueryService.getMyRefund(ME, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("참여 건 없는 입금 환불도 상세 조회된다")
    void getsDepositRefundDetail() {
        RefundResponse response = refundQueryService.getMyRefund(ME, 3L);

        assertThat(response.participationId()).isNull();
        assertThat(response.toAddress()).isEqualTo("0xbuyer");
        assertThat(response.failReason()).contains("아직 지원되지 않습니다");
    }

    // ------------------------------------------------------------------
    // 마스킹
    // ------------------------------------------------------------------

    /**
     * 본인 조회라도 계좌 전체를 돌려줄 이유가 없습니다. 응답은 로그나 브라우저 캐시에
     * 남을 수 있습니다.
     */
    @Test
    @DisplayName("계좌번호는 뒤 4자리만 내보낸다")
    void masksAccountNumber() {
        RefundResponse response = refundQueryService.getMyRefund(ME, 1L);

        assertThat(response.toAccountNo()).isEqualTo("************8792");
        assertThat(response.toAccountNo()).doesNotContain("0016174648");
    }

    /** 온체인 데이터는 공개 정보이고 구매자가 탐색기에서 확인해야 하므로 가리지 않습니다. */
    @Test
    @DisplayName("코인 수취 주소는 가리지 않는다 - 공개 정보다")
    void doesNotMaskWalletAddress() {
        RefundResponse response = refundQueryService.getMyRefund(ME, 3L);

        assertThat(response.toAddress()).isEqualTo("0xbuyer");
    }

    // ------------------------------------------------------------------
    // 관리자 조회 (RFD-03)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("관리자는 모든 사용자의 환불을 본다")
    void adminSeesEveryRefund() {
        RefundListResult result = refundQueryService.getAllRefunds(null, PageRequest.of(0, 20));

        assertThat(result.body())
                .extracting(RefundResponse::refundId)
                .containsExactlyInAnyOrder("1", "2", "3");
    }

    @Test
    @DisplayName("관리자는 실패 건만 추려 재처리 대상을 찾는다")
    void adminFiltersFailedRefunds() {
        RefundListResult result = refundQueryService.getAllRefunds("FAILED", PageRequest.of(0, 20));

        assertThat(result.body()).extracting(RefundResponse::refundId).containsExactly("3");
    }
}
