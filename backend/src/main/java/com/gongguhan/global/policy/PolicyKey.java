package com.gongguhan.global.policy;

/**
 * {@code platform_policies} 테이블의 정책 키.
 *
 * <p>V1__init_schema.sql의 시드 행과 1:1로 대응한다. 조회는 이 enum으로만 하고
 * 문자열 상수를 쓰지 않는다 - 오타는 런타임 조회 실패로만 드러나는데, 금액·기한을
 * 결정하는 값이라 그 시점에는 이미 늦다.
 *
 * <p>키를 추가할 때는 새 마이그레이션(V2 이후)에 시드 행을 함께 넣는다.
 * 여기에만 추가하면 {@link PolicyService}가 조회 시점에 예외를 던진다.
 */
public enum PolicyKey {

    // --- 수수료 및 인원 ---
    PLATFORM_FEE_RATE,
    MIN_TARGET_PARTICIPANTS,

    // --- 결제 슬롯 (payments.PROCESSING) ---
    SLOT_TTL_CARD_SEC,
    SLOT_TTL_CRYPTO_SEC,

    // --- 코인 결제 ---
    QUOTE_TTL_SEC,
    CRYPTO_BUFFER_RATE,
    CHAIN_SCAN_INTERVAL_MS,
    CHAIN_SCAN_BATCH_BLOCKS,

    // --- 기한 ---
    SHIPPING_DEADLINE_DAYS,
    AUTO_CONFIRM_DAYS,
    WITHDRAWAL_PERIOD_DAYS,
    SETTLEMENT_ELIGIBLE_DAYS,
    RETURN_APPROVE_DAYS,
    RETURN_AUTO_REFUND_DAYS,
    CARD_BILLING_CUTOFF_DAYS,
    PENALTY_LIMIT,
    SETTLEMENT_BATCH_HOUR,

    // --- SSAFY 금융망 (apiKey·플랫폼 userKey는 환경변수로 관리) ---
    SSAFY_MERCHANT_ID,
    SSAFY_PLATFORM_ACCOUNT_NO,
    SSAFY_INSTITUTION_CODE,
    SSAFY_FINTECH_APP_NO
}
