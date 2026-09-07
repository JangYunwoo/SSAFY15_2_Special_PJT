/**
 * 참여·구매확정·이의제기 도메인.
 *
 * <p>담당: 카드결제 담당자
 * <p>관련 API: PAR-01~03, CFM-01~02 (공구한_API_명세서_v1.md 7장)
 * <p>관련 테이블: participations
 *
 * <p>구매확정은 DELIVERED 상태에서만 가능하고 이후 취소할 수 없다. 반품 신청과의 동시
 * 실행은 참여 행 잠금 또는 낙관적 버전 검증으로 하나만 성공시킨다({@code domain.returns}
 * 참고).
 */
package com.gongguhan.domain.participation;
