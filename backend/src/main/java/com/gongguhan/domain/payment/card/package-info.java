/**
 * 카드결제 도메인 (SSAFY 금융망 createCreditCardTransaction 연동).
 *
 * <p>담당: 카드결제 담당자
 * <p>관련 API: CARD-01 (공구한_API_명세서_v1.md 6장)
 * <p>관련 테이블: card_payments
 *
 * <p>카드번호·CVC는 절대 저장·로깅하지 않는다(마스킹된 카드번호만 DB에 저장). 청구서 발행
 * 이후에는 결제 취소가 불가능하므로 계좌이체 환불로 전환하는 분기를 반드시 구현한다.
 */
package com.gongguhan.domain.payment.card;
