/**
 * 반품 도메인.
 *
 * <p>담당: 카카오페이결제 담당자
 * <p>관련 API: RTN-01~11 (공구한_API_명세서_v1.md 10장)
 * <p>관련 테이블: return_requests
 *
 * <p>패키지명은 {@code return}이 자바 예약어이므로 {@code returns}를 사용한다. 단순변심은
 * 최초 배송비 미환불 + 반품 배송비(왕복) 구매자 부담, 판매자 귀책은 전액 환불 + 반품
 * 배송비 판매자 부담이다. 구매확정 후 청약철회 기간 내 반품은
 * {@code post_confirm=true, admin_review_required=true}로 접수한다.
 */
package com.gongguhan.domain.returns;
