package com.gongguhan.domain.penalty.event;

import java.time.LocalDateTime;

/**
 * 판매자 제재가 확정될 때 penalty 도메인(S15P21B205-83)이 발행하는 이벤트.
 *
 * <p>notification 도메인(S15P21B205-110)이 소비하여 판매자에게 경고·정지·쓰리아웃
 * 알림을 저장한다. 위반 횟수 계산이나 제재 정책 결정은 발행 측 책임이고, 소비 측은
 * 이 계약만 의존하므로 두 작업은 독립적으로 진행할 수 있다.
 *
 * <p>{@code penaltyId}는 확정된 위반 레코드 식별자로, 동일 제재에 대한 중복 알림을
 * 막는 멱등 키의 근거가 된다.
 */
public record SellerSanctionedEvent(
        Long sellerId,
        Long penaltyId,
        SanctionLevel level,
        int violationCount,
        String reasonType,
        LocalDateTime occurredAt
) {
}
