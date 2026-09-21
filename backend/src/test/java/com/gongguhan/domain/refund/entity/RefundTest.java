package com.gongguhan.domain.refund.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 환불 원장의 상태 전이 규칙.
 *
 * <p><b>이 테스트가 지키는 것은 "돈이 두 번 나가지 않는다" 입니다.</b> 환불은 실제
 * 자금이 밖으로 나가는 동작이라, 상태 전이를 한 번 잘못 허용하면 되돌릴 방법이
 * 없습니다. 그래서 허용되는 전이보다 <b>막혀야 하는 전이</b>를 더 촘촘히 봅니다.
 */
class RefundTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-11T10:00:00");
    private static final LocalDateTime LATER = NOW.plusMinutes(5);

    private static final Long PARTICIPATION_ID = 501L;
    private static final Long RETURN_ID = 9001L;
    private static final Long DEPOSIT_ID = 7001L;
    private static final long AMOUNT = 54_000L;
    private static final String BUYER_WALLET = "0x3989ed99f2d2cb64106ed71d8cdaaeecbd671cd6";
    private static final String TX_HASH =
            "0xa18e6822603f143775eb06fc6597ccb06b8256b21aedcd4bdb24f66c85f22134";

    private Refund participationRefund() {
        return Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW);
    }

    private Refund cryptoRefund() {
        return Refund.forUnmatchedDeposit(DEPOSIT_ID, AMOUNT, BUYER_WALLET, NOW);
    }

    @Test
    void zeroReturnCanCompleteWithoutExternalTransfer() {
        Refund refund = Refund.forReturn(RETURN_ID, PARTICIPATION_ID, 0, RefundMethod.KAKAOPAY, NOW);
        refund.completeWithoutTransfer(LATER);
        assertTrue(refund.isCompleted());
        assertNull(refund.getInstitutionTxNo());
        assertThrows(BusinessException.class, () -> refund.completeWithoutTransfer(LATER));
    }

    @Test
    void zeroCompletionCannotHidePositiveRefundOrNegativeReturn() {
        Refund refund = Refund.forReturn(RETURN_ID, PARTICIPATION_ID, 1, RefundMethod.KAKAOPAY, NOW);
        assertThrows(BusinessException.class, () -> refund.completeWithoutTransfer(LATER));
        assertThrows(BusinessException.class, () -> Refund.forReturn(RETURN_ID, PARTICIPATION_ID, -1, RefundMethod.KAKAOPAY, NOW));
        assertThrows(BusinessException.class, () -> Refund.forParticipation(PARTICIPATION_ID, 0, RefundReason.RECRUIT_FAILED, RefundMethod.ACCOUNT_TRANSFER, NOW));
    }

    @Nested
    @DisplayName("개시")
    class Opening {

        @Test
        @DisplayName("만들어지는 순간 이미 진행 중이다 - 환불 여부는 호출 측이 이미 판단했다")
        void startsProcessing() {
            Refund refund = participationRefund();

            assertTrue(refund.isProcessing());
            assertEquals(RefundStatus.PROCESSING, refund.getStatus());
            assertEquals(NOW, refund.getCreatedAt());
            assertNull(refund.getProcessedAt());
        }

        @Test
        @DisplayName("반품 환불은 반품 ID 를 남긴다 - 환불액 계산 근거를 되짚기 위해")
        void returnRefundKeepsReturnId() {
            Refund refund = Refund.forReturn(RETURN_ID, PARTICIPATION_ID, 42_000L,
                    RefundMethod.KAKAOPAY, NOW);

            assertEquals(RETURN_ID, refund.getReturnId());
            assertEquals(PARTICIPATION_ID, refund.getParticipationId());
            assertEquals(RefundReason.RETURN, refund.getReasonType());
        }

        /**
         * 반품 환불을 일반 경로로 만들면 {@code return_id} 가 비어 나중에 "왜 42,000원만
         * 돌려줬는지" 를 되짚을 수 없다. 조용히 허용하는 대신 막는다.
         */
        @Test
        @DisplayName("반품 사유를 일반 개시 경로로 쓰면 거부한다")
        void rejectsReturnReasonOnGenericFactory() {
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> Refund.forParticipation(PARTICIPATION_ID, AMOUNT,
                            RefundReason.RETURN, RefundMethod.KAKAOPAY, NOW));

            assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, exception.getErrorCode());
        }

        @Test
        @DisplayName("입금 환불은 참여 건 없이 만들어진다 - 자리를 얻지 못한 입금")
        void depositRefundHasNoParticipation() {
            Refund refund = Refund.forExpiredSlot(DEPOSIT_ID, AMOUNT, BUYER_WALLET, NOW);

            assertNull(refund.getParticipationId());
            assertEquals(DEPOSIT_ID, refund.getDepositId());
            assertEquals(BUYER_WALLET, refund.getToAddress());
            assertEquals(RefundMethod.CRYPTO, refund.getRefundMethod());
        }

        /**
         * 보낼 곳을 모르는 환불은 만들 수 없다. 목적지를 송금 직전에 정하면, 송금 후
         * 장애가 났을 때 어디로 갔는지 원장에 남지 않는다.
         */
        @Test
        @DisplayName("수취 주소 없는 코인 환불은 개시 자체를 거부한다")
        void rejectsCryptoRefundWithoutAddress() {
            assertThrows(BusinessException.class,
                    () -> Refund.forUnmatchedDeposit(DEPOSIT_ID, AMOUNT, "  ", NOW));
            assertThrows(BusinessException.class,
                    () -> Refund.forUnmatchedDeposit(DEPOSIT_ID, AMOUNT, null, NOW));
        }

        @Test
        @DisplayName("0원 이하 환불은 거부한다 - 환불액 계산 버그를 여기서 막는다")
        void rejectsNonPositiveAmount() {
            assertThrows(BusinessException.class,
                    () -> Refund.forParticipation(PARTICIPATION_ID, 0L,
                            RefundReason.RECRUIT_FAILED, RefundMethod.CARD_CANCEL, NOW));
            assertThrows(BusinessException.class,
                    () -> Refund.forParticipation(PARTICIPATION_ID, -1L,
                            RefundReason.RECRUIT_FAILED, RefundMethod.CARD_CANCEL, NOW));
        }
    }

    @Nested
    @DisplayName("완료")
    class Completing {

        @Test
        @DisplayName("계좌 이체 완료는 기관거래번호와 거래번호를 남긴다")
        void completesByAccountTransfer() {
            Refund refund = participationRefund();

            refund.completeByAccountTransfer("INST-0001", 300123456L, LATER);

            assertTrue(refund.isCompleted());
            assertEquals("INST-0001", refund.getInstitutionTxNo());
            assertEquals(300123456L, refund.getSsafyTransactionNo());
            assertEquals(LATER, refund.getProcessedAt());
        }

        @Test
        @DisplayName("코인 송금 완료는 해시와 토큰 수량을 남긴다")
        void completesByCrypto() {
            Refund refund = cryptoRefund();

            refund.completeByCrypto(TX_HASH, new BigDecimal("39.528990"), LATER);

            assertTrue(refund.isCompleted());
            assertEquals(TX_HASH, refund.getTxHash());
            assertEquals(0, new BigDecimal("39.528990").compareTo(refund.getAssetAmount()));
        }

        /**
         * 해시 없는 완료를 허용하면 "돈이 나갔다" 는 기록만 남고 증거가 사라진다.
         * 나중에 구매자가 못 받았다고 하면 확인할 방법이 없다.
         */
        @Test
        @DisplayName("해시 없이 코인 환불을 완료할 수 없다")
        void rejectsCryptoCompletionWithoutHash() {
            Refund refund = cryptoRefund();

            assertThrows(BusinessException.class,
                    () -> refund.completeByCrypto(null, BigDecimal.ONE, LATER));
            assertThrows(BusinessException.class,
                    () -> refund.completeByCrypto("   ", BigDecimal.ONE, LATER));
            assertTrue(refund.isProcessing(), "실패한 완료 시도가 상태를 바꾸면 안 된다");
        }

        /**
         * 수단을 섞으면 결과 컬럼이 엉킨다. 계좌이체 환불에 코인 해시를 넣으면
         * 조회 화면은 코인 환불로 보이는데 실제로는 계좌로 나간 상태가 된다.
         */
        @Test
        @DisplayName("개시 때 정한 수단과 다른 방식으로 완료할 수 없다")
        void rejectsMethodMismatch() {
            Refund transfer = participationRefund();
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> transfer.completeByCrypto(TX_HASH, BigDecimal.ONE, LATER));
            assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());

            Refund crypto = cryptoRefund();
            assertThrows(BusinessException.class,
                    () -> crypto.completeByCardCancel(1L, LATER));
        }

        @Test
        @DisplayName("이미 완료된 환불을 두 번 완료할 수 없다 - 배치 재실행 이중 지급 차단")
        void rejectsDoubleCompletion() {
            Refund refund = cryptoRefund();
            refund.completeByCrypto(TX_HASH, new BigDecimal("39.528990"), LATER);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> refund.completeByCrypto(TX_HASH, new BigDecimal("39.528990"), LATER));

            assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("실패와 재처리")
    class FailingAndRetrying {

        @Test
        @DisplayName("실패하면 사유를 남기고 재처리 대상이 된다")
        void failKeepsReason() {
            Refund refund = cryptoRefund();

            refund.markFailed("가스비가 부족합니다.", LATER);

            assertTrue(refund.isFailed());
            assertEquals("가스비가 부족합니다.", refund.getFailReason());
            assertEquals(LATER, refund.getProcessedAt());
        }

        @Test
        @DisplayName("재처리하면 진행 중으로 돌아가고 실패 사유가 지워진다")
        void retryClearsFailure() {
            Refund refund = cryptoRefund();
            refund.markFailed("노드 응답 없음", LATER);

            refund.retry();

            assertTrue(refund.isProcessing());
            assertNull(refund.getFailReason());
            assertNull(refund.getProcessedAt());
        }

        /**
         * 완료된 건을 재처리하면 이미 나간 돈을 또 보낸다. 관리자 화면에서 실수로
         * 누르는 것이 가장 흔한 경로라 엔티티가 막는다.
         */
        @Test
        @DisplayName("완료된 환불은 재처리할 수 없다 - 이중 지급 차단")
        void rejectsRetryOfCompleted() {
            Refund refund = cryptoRefund();
            refund.completeByCrypto(TX_HASH, BigDecimal.ONE, LATER);

            BusinessException exception = assertThrows(BusinessException.class, refund::retry);

            assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
            assertTrue(refund.isCompleted());
        }

        @Test
        @DisplayName("진행 중인 환불은 재처리 대상이 아니다")
        void rejectsRetryOfProcessing() {
            assertThrows(BusinessException.class, () -> cryptoRefund().retry());
        }

        /**
         * 완료 뒤 실패로 표시하면 재처리 대상이 되어 결국 두 번 나간다.
         * {@code markFailed} 도 진행 중일 때만 허용해야 한다.
         */
        @Test
        @DisplayName("완료된 환불을 실패로 되돌릴 수 없다")
        void rejectsFailAfterCompletion() {
            Refund refund = cryptoRefund();
            refund.completeByCrypto(TX_HASH, BigDecimal.ONE, LATER);

            assertThrows(BusinessException.class, () -> refund.markFailed("착오", LATER));
            assertTrue(refund.isCompleted());
        }
    }

    @Nested
    @DisplayName("계좌 지정")
    class AssigningAccount {

        @Test
        @DisplayName("계좌이체 환불에 대상 계좌를 정한다")
        void assignsAccount() {
            Refund refund = participationRefund();

            refund.assignAccount("0011234567890");

            assertEquals("0011234567890", refund.getToAccountNo());
        }

        @Test
        @DisplayName("계좌이체가 아닌 환불에는 계좌를 정할 수 없다")
        void rejectsAccountOnNonTransfer() {
            assertThrows(BusinessException.class,
                    () -> cryptoRefund().assignAccount("0011234567890"));
        }

        @Test
        @DisplayName("빈 계좌번호는 거부한다")
        void rejectsBlankAccount() {
            assertThrows(BusinessException.class, () -> participationRefund().assignAccount(" "));
        }
    }
}
