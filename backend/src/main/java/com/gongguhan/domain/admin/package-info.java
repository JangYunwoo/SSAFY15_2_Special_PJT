/**
 * 관리자·정책·운영 도메인.
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: ADM-01~07, WDR-02~03 (공구한_API_명세서_v1.md 15장)
 * <p>관련 테이블: platform_policies, audit_logs
 *
 * <p>정책값 변경은 반드시 audit_logs에 기록한다. 다른 도메인의 관리자 전용 조회 API
 * (예: RTN-10/11, PEN-02~06, STL-03~07)는 각 도메인 패키지에 그대로 둔다 - 이 패키지는
 * 특정 도메인에 속하지 않는 전역 관리자 기능(정책, 감사로그, 배치 재실행)만 담당한다.
 */
package com.gongguhan.domain.admin;
