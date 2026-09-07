/**
 * 에스크로 도메인.
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: ESC-01~03 (공구한_API_명세서_v1.md 8장)
 * <p>관련 테이블: escrows
 *
 * <p>상태 변경(HELD → RELEASED/REFUNDED)은 결제·환불·정산 유스케이스 내부에서만 수행하며,
 * 사용자가 직접 상태를 바꾸는 API는 제공하지 않는다.
 */
package com.gongguhan.domain.escrow;
