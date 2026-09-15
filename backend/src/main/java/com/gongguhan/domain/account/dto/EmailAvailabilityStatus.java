package com.gongguhan.domain.account.dto;

/**
 * 이메일 사용 가능 여부 판정 결과(C안).
 *
 * <p>우리 서비스 DB 중복뿐 아니라 SSAFY 금융망 선점 충돌까지 <b>가입 전에</b> 판별한다.
 * 선점은 SSAFY가 읽기로 알려주지 않아 {@code createMember}(쓰기)로만 확인되므로,
 * 이 판정은 명시적 "확인·예약" 액션에서만 수행한다({@code POST /auth/email/availability}).
 */
public enum EmailAvailabilityStatus {

    /** 우리 DB에도 없고 SSAFY 연동도 가능(회원키 확보 → 예약 캐시에 담김). 가입 진행 가능. */
    AVAILABLE,

    /** 우리 서비스에 이미 가입된 이메일. */
    TAKEN,

    /** SSAFY 금융망을 다른 apiKey가 선점 → 우리로는 영영 연동 불가. 가입 불가. */
    UNAVAILABLE_SSAFY,

    /** SSAFY 장애 등으로 이번엔 판별하지 못함. 가입은 진행 가능하며 가입 시점에 재확인된다. */
    UNKNOWN;

    /** 이 상태에서 가입을 진행해도 되는지. 확인 불가(UNKNOWN)는 낙관적으로 진행을 허용한다. */
    public boolean canProceed() {
        return this == AVAILABLE || this == UNKNOWN;
    }
}
