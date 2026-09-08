/**
 * 인증/인가 인프라 (JWT 발급·검증, 인증 필터, 권한 검사).
 *
 * <p>담당: 카드결제 담당자 ({@code domain.account}와 함께 가장 먼저 착수)
 * <p>역할: USER, SELLER, ADMIN (API 명세서 1.1). 판매자도 동일 users 계정으로 구매 API를
 * 사용하므로, 권한 검사는 역할뿐 아니라 자원 소유권(인증 주체 기준)도 함께 검증한다.
 */
package com.gongguhan.global.security;
