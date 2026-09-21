package com.gongguhan.domain.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.repository.RefundRepository;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 이중 환불이 <b>DB 수준에서</b> 막히는지 검증.
 *
 * <p><b>왜 단위 테스트로는 부족한가.</b> "먼저 조회해서 있으면 만들지 않는다" 는
 * 애플리케이션 로직은 두 요청이 동시에 조회를 통과하면 둘 다 INSERT 됩니다. mock
 * 리포지토리로는 그 경쟁을 재현할 수 없고, 통과하는 테스트만 남습니다. 실제로 두
 * 행을 넣어보고 DB 가 거부하는지 확인해야 의미가 있습니다.
 *
 * <p>환불은 실제 자금이 밖으로 나가는 동작이라, 중복이 통과하면 그만큼 돈이 두 번
 * 나가고 되돌릴 수 없습니다. 그래서 마지막 방어선을 DB 에 둡니다
 * ({@code UNIQUE(participation_id, refund_marker)}).
 *
 * <p><b>이 테스트가 확인하는 전제 하나.</b> 마커 패턴은 "UNIQUE 인덱스에서 NULL 은
 * 서로 충돌하지 않는다" 는 성질에 기댑니다. 참여 건이 없는 입금 환불이 여러 건
 * 만들어질 수 있어야 하므로, 그 성질이 실제로 성립하는지도 함께 봅니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefundDuplicateGuardIntegrationTest {

    private static final Long PARTICIPATION_ID = 501L;
    private static final long AMOUNT = 54_000L;
    private static final String BUYER_WALLET = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-11T10:00:00");

    @Autowired
    private RefundLedgerService ledgerService;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 트랜잭션을 열지 않는 테스트라 직접 비운다. @Transactional 로 롤백시키면
        // 제약 위반이 커밋 시점에 드러나 어느 단계에서 막혔는지 알 수 없다.
        jdbcTemplate.update("DELETE FROM refunds");
    }

    @Test
    @DisplayName("같은 참여 건에 환불을 두 번 개시하면 기존 건을 그대로 돌려준다")
    void reusesExistingRefund() {
        Refund first = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        Refund second = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();

        assertThat(second.getRefundId()).isEqualTo(first.getRefundId());
        assertThat(refundRepository.count()).isEqualTo(1);
    }

    @Test
    void claimedFailureRetainsMarkerAndCannotBeRetriedOrReplaced() {
        Refund refund = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        ledgerService.claimExecution(refund.getRefundId(), "QA-CORRELATION");
        ledgerService.fail(refund.getRefundId(), "response lost");
        assertThatThrownBy(() -> ledgerService.retry(refund.getRefundId())).hasMessageContaining("대조");
        assertThat(ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).created()).isFalse();
        assertThatThrownBy(() -> refundRepository.saveAndFlush(Refund.forParticipation(PARTICIPATION_ID,
                AMOUNT, RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void explicitRejectionReleasesClaimAtomicallyAndAllowsRetry() {
        Refund refund = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        ledgerService.claimExecution(refund.getRefundId(), "QA-CORRELATION");
        ledgerService.failRejected(refund.getRefundId(), "rejected");
        ledgerService.retry(refund.getRefundId());
        ledgerService.claimExecution(refund.getRefundId(), "QA-CORRELATION-RETRY");
        assertThatThrownBy(() -> ledgerService.claimExecution(refund.getRefundId(), null)).hasMessageContaining("대조");
    }

    @Test
    void concurrentRetriesHaveOneProcessingOwner() throws Exception {
        Refund refund = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        ledgerService.fail(refund.getRefundId(), "not sent");
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> retry = () -> {
            gate.await();
            try { ledgerService.retry(refund.getRefundId()); return true; }
            catch (com.gongguhan.global.error.BusinessException rejected) { return false; }
        };
        try {
            var first = executor.submit(retry);
            var second = executor.submit(retry);
            gate.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
    }

    @Test
    void concurrentResumeClaimsHaveOneWinnerWithoutFailingLosersLedger() throws Exception {
        Refund refund = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> claim = () -> { gate.await(); return ledgerService.tryClaimExecution(refund.getRefundId(), "QA"); };
        try {
            var first = executor.submit(claim);var second = executor.submit(claim);gate.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),second.get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(ledgerService.get(refund.getRefundId()).isProcessing()).isTrue();
        } finally { executor.shutdownNow(); }
    }

    /**
     * 애플리케이션 조회를 건너뛰고 두 행을 직접 넣어봅니다. 동시 요청이 둘 다 조회를
     * 통과한 상황과 같습니다.
     */
    @Test
    @DisplayName("활성 환불이 있는 참여 건에 두 번째 행을 넣으면 DB 가 거부한다")
    void databaseRejectsSecondActiveRefund() {
        refundRepository.saveAndFlush(Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW));

        Refund duplicate = Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.PARTICIPATION_CANCEL, RefundMethod.ACCOUNT_TRANSFER, NOW);

        assertThatThrownBy(() -> refundRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("완료된 환불이 있어도 두 번째 환불은 막힌다 - 이미 돈이 나갔다")
    void databaseRejectsRefundAfterCompletion() {
        Refund completed = refundRepository.saveAndFlush(
                Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                        RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW));
        completed.completeByAccountTransfer("INST-1", 1L, NOW);
        refundRepository.saveAndFlush(completed);

        Refund duplicate = Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.SELLER_FAULT, RefundMethod.ACCOUNT_TRANSFER, NOW);

        assertThatThrownBy(() -> refundRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 실패한 환불은 자금 흐름을 차지하지 않으므로 유일성 검사에서 빠져야 합니다.
     * 그렇지 않으면 한 번 실패한 참여 건은 영원히 환불받을 수 없습니다.
     */
    @Test
    @DisplayName("실패한 환불이 있으면 새 환불을 만들 수 있다")
    void allowsNewRefundAfterFailure() {
        Refund failed = refundRepository.saveAndFlush(
                Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                        RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW));
        failed.markFailed("잔액 부족", NOW);
        refundRepository.saveAndFlush(failed);

        Refund retryRow = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();

        assertThat(retryRow.getRefundId()).isNotEqualTo(failed.getRefundId());
        assertThat(refundRepository.count()).isEqualTo(2);
    }

    /**
     * 마커 패턴의 전제입니다. 참여 건이 없는 입금 환불은 {@code (NULL, 'Y')} 가 되는데,
     * NULL 끼리 충돌한다면 두 번째 입금 환불이 만들어지지 않습니다.
     */
    @Test
    @DisplayName("참여 건 없는 입금 환불은 여러 건 만들 수 있다 - UNIQUE 에서 NULL 은 서로 다르다")
    void allowsMultipleDepositRefunds() {
        ledgerService.openForUnmatchedDeposit(7001L, AMOUNT, BUYER_WALLET).refund();
        ledgerService.openForExpiredSlot(7002L, AMOUNT, BUYER_WALLET).refund();

        assertThat(refundRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 입금을 두 번 돌려보내려 하면 기존 건을 돌려준다")
    void reusesExistingDepositRefund() {
        Refund first = ledgerService.openForUnmatchedDeposit(7001L, AMOUNT, BUYER_WALLET).refund();
        Refund second = ledgerService.openForUnmatchedDeposit(7001L, AMOUNT, BUYER_WALLET).refund();

        assertThat(second.getRefundId()).isEqualTo(first.getRefundId());
        assertThat(refundRepository.count()).isEqualTo(1);
    }

    /**
     * 온체인 송금은 되돌릴 수 없으므로, 한 번의 송금이 두 건의 환불로 세어지면
     * 장부와 실제 자금이 영구히 어긋납니다.
     */
    @Test
    @DisplayName("같은 트랜잭션 해시를 두 환불에 기록할 수 없다")
    void rejectsSharedTxHash() {
        String txHash = "0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134";

        Refund first = ledgerService.openForUnmatchedDeposit(7001L, AMOUNT, BUYER_WALLET).refund();
        ledgerService.completeByCrypto(first.getRefundId(), txHash, null);

        Refund second = ledgerService.openForUnmatchedDeposit(7002L, AMOUNT, BUYER_WALLET).refund();

        assertThatThrownBy(() ->
                ledgerService.completeByCrypto(second.getRefundId(), txHash, null))
                .hasMessageContaining("이미 다른 환불에 기록된 트랜잭션");
    }

    @Test
    @DisplayName("재처리는 실패 건을 다시 진행 중으로 되돌린다")
    void retryReopensFailedRefund() {
        Refund refund = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        ledgerService.fail(refund.getRefundId(), "노드 응답 없음");

        Refund reopened = ledgerService.retry(refund.getRefundId());

        assertThat(reopened.isProcessing()).isTrue();
        assertThat(refundRepository.findById(refund.getRefundId()).orElseThrow().isProcessing())
                .isTrue();
    }

    /**
     * 실패한 사이에 같은 참여 건으로 새 환불이 만들어졌다면, 재처리하면 두 건이
     * 동시에 진행됩니다. 조용히 진행시키는 대신 막아야 합니다.
     */
    @Test
    @DisplayName("다른 환불이 진행 중인 참여 건은 재처리할 수 없다")
    void rejectsRetryWhenAnotherRefundIsActive() {
        Refund failed = ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER).refund();
        ledgerService.fail(failed.getRefundId(), "잔액 부족");
        ledgerService.openForParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.SELLER_FAULT, RefundMethod.ACCOUNT_TRANSFER).refund();

        assertThatThrownBy(() -> ledgerService.retry(failed.getRefundId()))
                .hasMessageContaining("다른 환불이 진행 중");
    }

    @Test
    @DisplayName("반품 환불은 반품 ID 를 남기고 같은 참여 건 중복을 막는다")
    void returnRefundKeepsReturnIdAndBlocksDuplicate() {
        Refund refund = ledgerService.openForReturn(9001L, PARTICIPATION_ID, 42_000L,
                RefundMethod.KAKAOPAY).refund();

        assertThat(refund.getReturnId()).isEqualTo(9001L);
        assertThat(refundRepository.findByReturnId(9001L)).isPresent();

        // 같은 참여 건에 다른 반품으로 또 환불을 걸면 기존 건이 돌아온다.
        Refund again = ledgerService.openForReturn(9002L, PARTICIPATION_ID, 10_000L,
                RefundMethod.KAKAOPAY).refund();
        assertThat(again.getRefundId()).isEqualTo(refund.getRefundId());
        assertThat(refundRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("재처리 대상은 오래된 순서로 가져온다 - 먼저 실패한 구매자가 먼저 받는다")
    void findFailedReturnsOldestFirst() {
        Refund older = refundRepository.saveAndFlush(Refund.forParticipation(601L, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW));
        Refund newer = refundRepository.saveAndFlush(Refund.forParticipation(602L, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW.plusHours(1)));
        ledgerService.fail(newer.getRefundId(), "나중 실패");
        ledgerService.fail(older.getRefundId(), "먼저 실패");

        assertThat(ledgerService.findFailed(10))
                .extracting(Refund::getRefundId)
                .containsExactly(older.getRefundId(), newer.getRefundId());
    }
}
