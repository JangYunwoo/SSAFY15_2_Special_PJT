package com.gongguhan.domain.settlement.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 정산 금액 계산과 상태 전이.
 *
 * <p><b>계산이 틀리면 판매자가 받을 돈이 틀립니다.</b> 그래서 API 명세서 12장의
 * 예시를 그대로 테스트에 넣어 문서와 코드가 같은 답을 내는지 고정합니다.
 */
class SettlementTest {

    private static final Long GROUP_BUY_ID = 701L;
    private static final Long SELLER_ID = 201L;
    private static final BigDecimal RATE = new BigDecimal("0.0500");
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-09-14T03:00:00");
    private static final LocalDateTime LATER = NOW.plusHours(2);

    private Settlement pending() {
        return Settlement.create(GROUP_BUY_ID, SELLER_ID, 1, 19,
                950_000L, 76_000L, 0L, RATE, NOW);
    }

    @Nested
    @DisplayName("금액 계산")
    class Calculation {

        /**
         * API 명세서 12장 "정산 상세 응답" 예시입니다. 19명 × 분담금 50,000 = 950,000,
         * 배송비 19 × 4,000 = 76,000, 수수료 5% = 47,500, 지급액 978,500.
         */
        @Test
        @DisplayName("명세서 예시와 같은 금액을 계산한다 - 19명 확정, 지급액 978,500원")
        void matchesSpecExample() {
            Settlement settlement = pending();

            assertEquals(950_000L, settlement.getShareTotal());
            assertEquals(76_000L, settlement.getShippingTotal());
            assertEquals(47_500L, settlement.getPlatformFee());
            assertEquals(978_500L, settlement.getSettlementAmount());
        }

        /**
         * 배송비는 판매자가 택배사에 내는 실비입니다. 거기서 수수료를 떼면 판매자가
         * 손해를 봅니다.
         */
        @Test
        @DisplayName("수수료는 배송비를 제외한 분담금에만 매긴다")
        void feeExcludesShipping() {
            Settlement settlement = Settlement.create(GROUP_BUY_ID, SELLER_ID, 1, 1,
                    100_000L, 900_000L, 0L, RATE, NOW);

            // 배송비까지 포함했다면 1,000,000 × 5% = 50,000 이 된다.
            assertEquals(5_000L, settlement.getPlatformFee());
        }

        /** 올리면 판매자가 받을 돈이 줄어드는 쪽으로 오차가 쌓입니다. */
        @Test
        @DisplayName("수수료는 원 단위로 버린다")
        void feeRoundsDown() {
            Settlement settlement = Settlement.create(GROUP_BUY_ID, SELLER_ID, 1, 1,
                    19_999L, 0L, 0L, RATE, NOW);

            // 19,999 × 0.05 = 999.95 -> 999
            assertEquals(999L, settlement.getPlatformFee());
            assertEquals(19_000L, settlement.getSettlementAmount());
        }

        @Test
        @DisplayName("절상 차액은 판매자에게 가산된다")
        void roundingDiffIsAdded() {
            Settlement settlement = Settlement.create(GROUP_BUY_ID, SELLER_ID, 1, 19,
                    950_000L, 76_000L, 12L, RATE, NOW);

            assertEquals(978_512L, settlement.getSettlementAmount());
        }

        /**
         * 나중에 수수료율 정책이 바뀌어도 이미 만들어진 정산의 계산 근거는 흔들리면
         * 안 됩니다.
         */
        @Test
        @DisplayName("수수료율을 행에 복사해 둔다 - 정책이 바뀌어도 근거가 유지된다")
        void keepsFeeRateSnapshot() {
            assertEquals(RATE, pending().getPlatformFeeRate());
        }

        @Test
        @DisplayName("대상이 없으면 정산을 만들지 않는다")
        void rejectsEmptySettlement() {
            assertThrows(BusinessException.class, () -> Settlement.create(
                    GROUP_BUY_ID, SELLER_ID, 1, 0, 0L, 0L, 0L, RATE, NOW));
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transitions {

        @Test
        @DisplayName("생성 직후에는 실행 대기 상태다")
        void startsPending() {
            Settlement settlement = pending();

            assertEquals(Settlement.STATUS_PENDING, settlement.getStatus());
            assertEquals(Settlement.TRANSFER_PENDING, settlement.getTransferStatus());
            assertTrue(settlement.isExecutable());
        }

        @Test
        @DisplayName("이체가 확정되면 거래번호를 남기고 완료된다")
        void completesTransfer() {
            Settlement settlement = pending();

            settlement.completeTransfer("INST-1", 100L, 200L, LATER);

            assertTrue(settlement.isCompleted());
            assertEquals(Settlement.TRANSFER_COMPLETED, settlement.getTransferStatus());
            assertEquals("INST-1", settlement.getInstitutionTxNo());
            assertEquals(LATER, settlement.getTransferredAt());
            assertFalse(settlement.isExecutable());
        }

        /** 재처리 배치를 두 번 돌려도 같은 정산이 두 번 지급되지 않아야 합니다. */
        @Test
        @DisplayName("완료된 정산을 다시 완료할 수 없다 - 이중 지급 차단")
        void rejectsDoubleCompletion() {
            Settlement settlement = pending();
            settlement.completeTransfer("INST-1", 100L, 200L, LATER);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> settlement.completeTransfer("INST-2", 101L, 201L, LATER));

            assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        }

        /**
         * 이체만 실패했을 뿐 정산 자체는 유효합니다. 상태를 유지해야 재시도로 같은
         * 금액을 다시 보낼 수 있습니다.
         */
        @Test
        @DisplayName("이체 실패는 정산을 무효로 만들지 않는다 - 재처리 대상으로 남는다")
        void failedTransferStaysExecutable() {
            Settlement settlement = pending();

            settlement.failTransfer("잔액 부족");

            assertEquals(Settlement.STATUS_PENDING, settlement.getStatus());
            assertEquals(Settlement.TRANSFER_FAILED, settlement.getTransferStatus());
            assertTrue(settlement.isExecutable());
            assertEquals("잔액 부족", settlement.getTransferFailReason());
        }

        @Test
        @DisplayName("분쟁으로 보류하면 실행할 수 없다")
        void holdBlocksExecution() {
            Settlement settlement = pending();

            settlement.hold("반품 분쟁 진행 중");

            assertTrue(settlement.isHeld());
            assertFalse(settlement.isExecutable());
        }

        @Test
        @DisplayName("보류를 해제하면 다시 실행 대기가 된다")
        void releaseHoldRestoresPending() {
            Settlement settlement = pending();
            settlement.hold("분쟁");

            settlement.releaseHold();

            assertTrue(settlement.isExecutable());
            assertEquals(null, settlement.getTransferFailReason());
        }

        /** 돈이 이미 나갔으므로 보류가 아니라 회수 문제입니다. */
        @Test
        @DisplayName("이미 이체된 정산은 보류할 수 없다")
        void rejectsHoldAfterCompletion() {
            Settlement settlement = pending();
            settlement.completeTransfer("INST-1", 100L, 200L, LATER);

            assertThrows(BusinessException.class, () -> settlement.hold("뒤늦은 분쟁"));
        }

        @Test
        @DisplayName("보류 중인 정산은 이체 완료 처리할 수 없다")
        void rejectsCompletionWhileHeld() {
            Settlement settlement = pending();
            settlement.hold("분쟁");

            assertThrows(BusinessException.class,
                    () -> settlement.completeTransfer("INST-1", 100L, 200L, LATER));
        }
    }
}
