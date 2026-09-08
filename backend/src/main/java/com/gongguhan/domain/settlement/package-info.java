/**
 * 판매자 정산 도메인.
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: STL-01~07 (공구한_API_명세서_v1.md 12장)
 * <p>관련 테이블: settlements, settlement_items
 *
 * <p>정산 가능 시점({@code settlement_eligible_at} = 배송완료 + 7일) 이전에는 수동
 * 구매확정 여부와 무관하게 정산을 실행하지 않는다. 실제 계좌이체 호출은 DB 트랜잭션과
 * 길게 묶지 않고 기관거래고유번호로 재처리 안전성을 확보한다.
 */
package com.gongguhan.domain.settlement;
