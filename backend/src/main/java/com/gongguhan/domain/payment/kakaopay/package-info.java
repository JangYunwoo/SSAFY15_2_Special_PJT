/**
 * 카카오페이 결제 도메인.
 *
 * <p>담당: 카카오페이결제 담당자
 * <p>관련 API: KAKA-01~04 (공구한_API_명세서_v1.md 6장)
 * <p>관련 테이블: kakaopay_payments
 *
 * <p>Approve 성공 응답을 받아도 반드시 결제 금액·TID·주문 식별자를 서버에서 재검증한
 * 뒤에 참여(participation)를 생성한다.
 */
package com.gongguhan.domain.payment.kakaopay;
