package com.gongguhan.domain.returnrequest.repository;

import java.time.LocalDateTime;

/**
 * 반품 목록에 필요한 값만 한 번에 읽는 JPA 인터페이스 프로젝션이다.
 *
 * <p>목록 행을 엔티티로 만든 뒤 공동구매·상품·구매자를 행마다 다시 조회하지 않도록
 * 조회 전용 모양을 저장소 경계에 둔다.
 */
interface ReturnSummaryView {

    Long getId();

    Long getParticipationId();

    Long getGroupBuyId();

    String getProductName();

    String getBuyerName();

    String getReasonType();

    String getDetailReason();

    String getStatus();

    long getRefundAmount();

    long getReturnShippingFee();

    String getShippingFeeBearer();

    LocalDateTime getRequestedAt();

    LocalDateTime getAutoApproveAt();

    boolean getPostConfirm();

    boolean getAdminReviewRequired();
}
