package com.gongguhan.global.error;

import org.springframework.http.HttpStatus;

/**
 * API 명세서 16장 "주요 오류 코드" 표와 1:1로 대응한다.
 * 새 오류 코드가 필요하면 여기에 추가하고 API 명세서도 함께 갱신한다.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "필수값 누락 또는 형식이 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 만료되었습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_NOT_ACTIVE(HttpStatus.FORBIDDEN, "활성 상태가 아닌 계정입니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    ROLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 역할에서 접근할 수 없는 API입니다."),
    RESOURCE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 소유의 자원이 아닙니다."),
    SELLER_SUSPENDED(HttpStatus.FORBIDDEN, "정지된 판매자는 등록·생성을 할 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "대상 자원을 찾을 수 없습니다."),
    GROUP_BUY_NOT_RECRUITING(HttpStatus.CONFLICT, "모집 중인 공동구매가 아닙니다."),
    GROUP_BUY_FULL(HttpStatus.CONFLICT, "참여자와 유효 결제 슬롯이 목표 인원에 도달했습니다."),
    DUPLICATE_PARTICIPATION(HttpStatus.CONFLICT, "이미 참여한 공동구매입니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 완료되었거나 실패한 결제입니다."),
    PAYMENT_SLOT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "허용되지 않은 상태 전이입니다."),
    DUPLICATE_TRACKING_NUMBER(HttpStatus.CONFLICT, "이미 등록된 송장번호입니다."),
    SLOT_RESERVATION_CONFLICT(HttpStatus.CONFLICT, "일시적인 동시 요청 충돌로 처리하지 못했습니다. 다시 시도해주세요."),
    PURCHASE_CONFIRM_RETURN_CONFLICT(HttpStatus.CONFLICT, "구매확정과 반품 신청이 동시에 진행될 수 없습니다."),
    RETURN_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "반품 가능 기간이 아니거나 제한 사유에 해당합니다."),
    QUOTE_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "디지털 자산 Quote가 만료되었습니다."),
    CRYPTO_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "입금액이 일치하지 않거나 자산이 다릅니다."),
    EXTERNAL_PAYMENT_ERROR(HttpStatus.BAD_GATEWAY, "외부 결제기관 오류가 발생했습니다."),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI·시세·체인·금융망 연동이 일시적으로 불가능합니다."),
    SSAFY_MEMBER_NOT_LINKED(HttpStatus.UNPROCESSABLE_CONTENT, "SSAFY 금융망 회원 연동이 완료되지 않았습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
