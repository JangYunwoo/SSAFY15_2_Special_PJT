/**
 * 계정·인증 도메인.
 *
 * <p>담당: 카드결제 담당자 (프로젝트 공통 기반이므로 가장 먼저 착수)
 * <p>관련 API: AUTH-01~05, USR-01~06, SEL-01~07 (공구한_API_명세서_v1.md 2장)
 * <p>관련 테이블: users, user_addresses, seller_profiles, seller_verifications, admins
 *
 * <p>JWT 발급·검증({@code global.security})과 공통 응답 포맷({@code global.common})을
 * 가장 먼저 소비하는 도메인이므로, 두 패키지를 변경할 때는 반드시 다른 담당자와 공유한다.
 */
package com.gongguhan.domain.account;
