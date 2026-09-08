/**
 * 공동구매 도메인.
 *
 * <p>담당: 카카오페이결제 담당자
 * <p>관련 API: GRP-01~10 (공구한_API_명세서_v1.md 4장)
 * <p>관련 테이블: group_buys
 *
 * <p>분담금(unit_share_amount)은 생성 시 원 단위로 올림해 확정하며 이후 재계산하지 않는다.
 * 참여자 수 동시성 제어(target_count 초과 방지)는 {@code domain.payment.common}의 결제
 * 준비 트랜잭션에서 함께 처리한다.
 */
package com.gongguhan.domain.groupbuy;
