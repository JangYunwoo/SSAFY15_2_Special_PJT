package com.gongguhan.domain.escrow.dto;

import com.gongguhan.domain.escrow.entity.Escrow;
import java.time.ZoneOffset;

/**
 * ESC-01 참여자 에스크로 상태 (FR-ESC-04, FR-ESC-07).
 *
 * <p>구매자가 "내 돈이 지금 어떤 상태인지" 확인하는 용도입니다.
 * 상태를 바꾸는 API 는 제공하지 않습니다 — 해제·환불은 결제·정산·환불 유스케이스
 * 내부에서만 일어납니다.
 *
 * <p>식별자를 문자열로 내보내는 것은 명세 1.5 규칙입니다(JSON Number 범위 고려).
 * 금액은 원 단위 정수라 숫자로 둡니다.
 */
public record EscrowResponse(
        String escrowId,
        String participationId,
        String groupBuyId,
        long amount,
        String currency,
        String status,
        String heldAt,
        String releasedAt,
        String refundedAt
) {

    private static final String CURRENCY_KRW = "KRW";

    public static EscrowResponse from(Escrow escrow) {
        return new EscrowResponse(
                String.valueOf(escrow.getEscrowId()),
                String.valueOf(escrow.getParticipationId()),
                String.valueOf(escrow.getGroupBuyId()),
                escrow.getAmount(),
                CURRENCY_KRW,
                escrow.getStatus().name(),
                toIso(escrow.getHeldAt()),
                toIso(escrow.getReleasedAt()),
                toIso(escrow.getRefundedAt()));
    }

    /** 명세 1.5: 일시는 ISO-8601 UTC. null 은 그대로 null 로 두어 직렬화에서 생략된다. */
    private static String toIso(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }
}
