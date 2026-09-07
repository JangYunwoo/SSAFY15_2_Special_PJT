/**
 * 배송 도메인.
 *
 * <p>담당: 카드결제 담당자
 * <p>관련 API: DLV-01~05 (공구한_API_명세서_v1.md 9장)
 * <p>관련 테이블: deliveries
 *
 * <p>실시간 택배사 API는 호출하지 않는다. 동일 (courier_code, tracking_number) 중복 등록은
 * 거부한다.
 */
package com.gongguhan.domain.delivery;
