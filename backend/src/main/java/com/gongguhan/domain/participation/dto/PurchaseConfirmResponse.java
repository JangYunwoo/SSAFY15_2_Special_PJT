package com.gongguhan.domain.participation.dto;

import com.gongguhan.domain.participation.entity.Participation;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** CFM-01 처리 결과. settlementEligibleAt은 수동 확정과 무관하게 배송완료+정책일로 유지된다. */
public record PurchaseConfirmResponse(
        Long participationId,
        String status,
        String confirmType,
        OffsetDateTime confirmedAt,
        OffsetDateTime settlementEligibleAt
) {

    public static PurchaseConfirmResponse from(Participation participation) {
        return new PurchaseConfirmResponse(
                participation.getParticipationId(),
                participation.getStatus(),
                participation.getConfirmType(),
                toUtc(participation.getConfirmedAt()),
                toUtc(participation.getSettlementEligibleAt()));
    }

    private static OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
