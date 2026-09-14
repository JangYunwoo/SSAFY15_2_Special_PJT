package com.gongguhan.domain.delivery.dto;

/** DLV-04 일괄 상태 변경 결과. 전이 조건에 맞지 않는 행은 건너뛰고 개수로만 알린다. */
public record DeliveryBulkStatusResponse(
        int totalCount,
        int updatedCount,
        int skippedCount
) {
}
