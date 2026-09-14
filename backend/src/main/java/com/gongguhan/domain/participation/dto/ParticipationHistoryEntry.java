package com.gongguhan.domain.participation.dto;

import java.time.OffsetDateTime;

/** PAR-02 상태 이력 항목. 별도 이력 테이블 없이 각 단계의 기록 시각에서 유도한다. */
public record ParticipationHistoryEntry(
        String status,
        OffsetDateTime occurredAt,
        String note
) {
}
