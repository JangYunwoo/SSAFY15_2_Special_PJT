package com.gongguhan.domain.escrow.dto;

import java.util.List;

/**
 * ESC-02 판매자 공동구매별 보관 내역 (FR-ESC-04, FR-ESC-07·08).
 *
 * <p>판매자에게 <b>구매자 개인정보는 담지 않습니다</b>(NFR-SC-05). 에스크로 금액과
 * 상태만 내보냅니다. 참여자 정보는 이미 {@code /sellers/group-buys/{id}/participants}
 * 가 제공하며, 판매자는 {@code participationId} 로 두 응답을 연결할 수 있습니다.
 *
 * <p>{@code escrows} 는 페이지로 끊긴 목록이고 {@code summary} 는 전체 기준입니다.
 * 페이지 정보는 응답 봉투의 {@code meta} 에 담깁니다.
 */
public record GroupBuyEscrowResponse(
        String groupBuyId,
        EscrowSummaryResponse summary,
        List<EscrowResponse> escrows
) {

    public static GroupBuyEscrowResponse of(Long groupBuyId, EscrowSummaryResponse summary,
                                             List<EscrowResponse> escrows) {
        return new GroupBuyEscrowResponse(String.valueOf(groupBuyId), summary, escrows);
    }
}
