package com.gongguhan.domain.account.dto;

/**
 * AUTH-05 이메일 사용 가능 여부 응답.
 *
 * <p>{@code available=true}면 아직 가입되지 않은 이메일이라 회원가입에 사용할 수 있다.
 * 단, 이 확인은 우리 서비스 DB 기준이며 SSAFY 금융망의 이메일 선점 충돌
 * ({@code SSAFY_EMAIL_UNAVAILABLE})은 가입 시점에만 판별된다.
 */
public record EmailAvailabilityResponse(boolean available) {
}
