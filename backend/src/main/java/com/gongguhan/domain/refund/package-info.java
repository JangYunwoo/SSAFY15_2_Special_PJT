/**
 * 환불 도메인.
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: RFD-01~04 (공구한_API_명세서_v1.md 11장)
 * <p>관련 테이블: refunds
 *
 * <p>원 결제수단 환불을 우선하고, 카드 청구 확정 등으로 승인 취소가 불가능하면 SSAFY
 * 계좌이체 환불로 폴백한다. 재처리 API는 Idempotency-Key가 필수다.
 */
package com.gongguhan.domain.refund;
