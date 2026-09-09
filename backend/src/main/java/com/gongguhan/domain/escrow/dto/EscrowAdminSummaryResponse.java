package com.gongguhan.domain.escrow.dto;

import com.gongguhan.domain.escrow.entity.EscrowStatus;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ESC-03 관리자 에스크로 집계 (FR-ADM-12).
 *
 * <p>플랫폼이 지금 얼마를 들고 있고, 얼마가 판매자에게 나갈 차례인지 한눈에 봅니다.
 *
 * <pre>
 *   heldAmount                보관 중 — 구매확정 전이라 아직 아무 데도 안 감
 *   awaitingSettlementAmount  정산 대기 — 구매확정으로 풀렸지만 아직 판매자에게 안 감
 *   settledAmount             정산 완료 — 판매자에게 나감
 *   refundedAmount            환불 — 구매자에게 돌아감
 * </pre>
 *
 * <p><b>{@code heldAmount} 와 {@code awaitingSettlementAmount} 의 합이 플랫폼이 실제로
 * 책임지고 있는 금액입니다.</b> 전자는 아직 누구 것인지 정해지지 않은 돈이고, 후자는
 * 판매자 몫으로 확정됐지만 아직 지급하지 않은 돈입니다.
 *
 * <p>정산 대기는 에스크로만으로 구할 수 없습니다. {@code RELEASED} 가 종료 상태라
 * 정산 이후에도 값이 바뀌지 않기 때문에, 참여 건 상태를 함께 봐야
 * "풀렸지만 안 나간 돈"을 분리할 수 있습니다.
 */
public record EscrowAdminSummaryResponse(
        long heldCount,
        long heldAmount,
        long awaitingSettlementCount,
        long awaitingSettlementAmount,
        long settledCount,
        long settledAmount,
        long refundedCount,
        long refundedAmount,
        long totalCount,
        long totalAmount,
        String currency
) {

    private static final String CURRENCY_KRW = "KRW";

    /**
     * @param aggregates 상태별 전체 집계
     * @param settled    {@code RELEASED} 중 정산까지 끝난 부분
     */
    public static EscrowAdminSummaryResponse of(
            List<EscrowRepository.StatusAggregate> aggregates,
            EscrowRepository.CountAmount settled) {

        Map<EscrowStatus, Long> counts = new EnumMap<>(EscrowStatus.class);
        Map<EscrowStatus, Long> amounts = new EnumMap<>(EscrowStatus.class);
        for (EscrowRepository.StatusAggregate row : aggregates) {
            counts.put(row.getStatus(), row.getCount());
            amounts.put(row.getStatus(), row.getAmount());
        }

        long heldCount = counts.getOrDefault(EscrowStatus.HELD, 0L);
        long heldAmount = amounts.getOrDefault(EscrowStatus.HELD, 0L);
        long releasedCount = counts.getOrDefault(EscrowStatus.RELEASED, 0L);
        long releasedAmount = amounts.getOrDefault(EscrowStatus.RELEASED, 0L);
        long refundedCount = counts.getOrDefault(EscrowStatus.REFUNDED, 0L);
        long refundedAmount = amounts.getOrDefault(EscrowStatus.REFUNDED, 0L);

        // 집계 쿼리 결과가 없으면 null 이 올 수 있다.
        long settledCount = settled == null ? 0L : settled.getCount();
        long settledAmount = settled == null ? 0L : settled.getAmount();

        // 음수 방지. 두 쿼리 사이에 정산이 진행되면 settled 가 released 를 넘길 수 있다.
        // 관리자 화면에 음수 금액이 뜨는 것보다 0 이 낫다.
        long awaitingCount = Math.max(0, releasedCount - settledCount);
        long awaitingAmount = Math.max(0, releasedAmount - settledAmount);

        return new EscrowAdminSummaryResponse(
                heldCount, heldAmount,
                awaitingCount, awaitingAmount,
                settledCount, settledAmount,
                refundedCount, refundedAmount,
                heldCount + releasedCount + refundedCount,
                heldAmount + releasedAmount + refundedAmount,
                CURRENCY_KRW);
    }
}
