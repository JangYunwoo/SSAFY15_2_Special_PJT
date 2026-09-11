package com.gongguhan.domain.escrow.dto;

import com.gongguhan.domain.escrow.entity.EscrowStatus;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 에스크로 상태별 건수·금액 집계 (ESC-02, FR-ESC-04·08).
 *
 * <p><b>목록을 페이지로 끊어도 이 합계는 전체 기준입니다.</b> FR-ESC-08 이
 * "보관 총액 = 미해제 참여 건 결제금액 합계"를 요구하므로, 한 페이지만 더한 값으로는
 * 그 불변식을 확인할 수 없습니다.
 *
 * <p>{@code heldAmount} 가 판매자에게 가장 중요한 값입니다 — 아직 정산되지 않고
 * 보관 중인 금액입니다. {@code releasedAmount} 는 구매확정을 거쳐 정산 대상이 된 금액,
 * {@code refundedAmount} 는 환불로 빠진 금액입니다.
 */
public record EscrowSummaryResponse(
        long heldCount,
        long heldAmount,
        long releasedCount,
        long releasedAmount,
        long refundedCount,
        long refundedAmount,
        long totalCount,
        long totalAmount,
        String currency
) {

    private static final String CURRENCY_KRW = "KRW";

    /**
     * 상태별 집계 행을 응답으로 접습니다.
     *
     * <p>해당 상태의 행이 하나도 없으면 쿼리 결과에 나타나지 않으므로 0 으로 채웁니다.
     * 그러지 않으면 "보관 건이 없는 공동구매"의 응답에서 필드가 빠져 프론트가
     * undefined 를 만납니다.
     */
    public static EscrowSummaryResponse from(List<EscrowRepository.StatusAggregate> aggregates) {
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

        return new EscrowSummaryResponse(
                heldCount, heldAmount,
                releasedCount, releasedAmount,
                refundedCount, refundedAmount,
                heldCount + releasedCount + refundedCount,
                heldAmount + releasedAmount + refundedAmount,
                CURRENCY_KRW);
    }
}
