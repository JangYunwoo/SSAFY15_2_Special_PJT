package com.gongguhan.domain.returnrequest.dto;

import java.time.OffsetDateTime;

public record ReturnHistoryResponse(String status, OffsetDateTime occurredAt, String note) {
}
