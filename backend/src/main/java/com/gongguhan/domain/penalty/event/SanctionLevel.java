package com.gongguhan.domain.penalty.event;

/**
 * 판매자 제재 단계.
 *
 * <p>제재 정책(위반 누적·쓰리아웃 판정)은 penalty 도메인(S15P21B205-83)이 결정하고,
 * notification 도메인은 이 단계만 보고 알림 문구를 만든다.
 */
public enum SanctionLevel {

    /** 위반 확정으로 경고가 부과됨(누적 정지 임계 미만). */
    WARNING,

    /** 판매 정지가 부과됨. */
    SUSPENDED,

    /** 위반 3회 누적(쓰리아웃)으로 신규 등록·공동구매 생성이 영구 차단됨. */
    THREE_STRIKE
}
