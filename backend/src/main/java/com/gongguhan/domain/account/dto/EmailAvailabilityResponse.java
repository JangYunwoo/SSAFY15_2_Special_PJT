package com.gongguhan.domain.account.dto;

/**
 * 이메일 사용 가능 여부 응답(C안).
 *
 * <p>{@code available}은 "이 상태로 가입을 진행해도 되는지"이고, {@code status}가 구체
 * 사유다. 프론트는 {@code status}별로 다른 안내 문구를 보여준다.
 *
 * <ul>
 *   <li>{@code AVAILABLE} - 사용 가능(available=true)</li>
 *   <li>{@code TAKEN} - 이미 가입된 이메일(available=false)</li>
 *   <li>{@code UNAVAILABLE_SSAFY} - 금융망 선점으로 사용 불가(available=false)</li>
 *   <li>{@code UNKNOWN} - 판별 실패, 진행은 허용(available=true)</li>
 * </ul>
 */
public record EmailAvailabilityResponse(boolean available, EmailAvailabilityStatus status) {
}
