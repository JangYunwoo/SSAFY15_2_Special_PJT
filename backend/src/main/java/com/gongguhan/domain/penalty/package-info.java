/**
 * 판매자 제재 도메인.
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: PEN-01~06 (공구한_API_명세서_v1.md 13장)
 * <p>관련 테이블: seller_penalties
 *
 * <p>3회 누적 시 신규 상품 등록·공동구매 생성을 차단하되, 기존 거래의 배송·반품·정산
 * 처리는 계속 허용한다.
 */
package com.gongguhan.domain.penalty;
