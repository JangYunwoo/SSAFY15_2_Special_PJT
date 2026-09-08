/**
 * 결제 공통(슬롯) 도메인 - 결제수단에 무관한 오케스트레이션 계층.
 *
 * <p>담당: 카드결제 담당자가 초안을 리드하되, 이 패키지가 정의하는 결제수단 전략 인터페이스는
 * 반드시 3인 합의로 설계한다({@code domain.payment.card}/{@code kakaopay}/{@code crypto}가
 * 이 인터페이스의 구현체가 된다).
 * <p>관련 API: PAY-01~04 (공구한_API_명세서_v1.md 5장)
 * <p>관련 테이블: payments (PROCESSING 상태가 곧 결제 슬롯), ssafy_api_logs
 *
 * <p>슬롯 선점 → 외부 결제 호출(트랜잭션 밖) → 짧은 트랜잭션으로 PAID/participations/escrow
 * 확정까지의 원자성 규칙은 API 명세서 5장 "원자성 규칙"을 그대로 따른다.
 */
package com.gongguhan.domain.payment.common;
