package com.gongguhan.domain.account.dto;

/** HTTP 입력 형식. 전화번호 소유·실재 여부 인증과는 별개의 경계 검증이다. */
public final class AccountInputPatterns {
    private AccountInputPatterns() { }

    // 기존 FE/API의 숫자 전용 계약. 02 일반전화(9자리)도 배제하지 않는다.
    public static final String PHONE_DIGITS = "0[0-9]{8,10}";
    public static final String PHONE_MESSAGE = "전화번호는 0으로 시작하는 9~11자리 숫자여야 합니다.";
    // @Pattern은 null을 허용하므로 PATCH 미전달/null=유지 계약을 보존한다.
    public static final String NON_BLANK = "(?s).*[^\\p{Z}\\s].*";
}
