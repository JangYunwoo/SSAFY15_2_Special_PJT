-- =====================================================================
-- 공구한 (Gonggu-han) Database Schema v1.0  (2026-09-01 개정)
-- MySQL 8.0 / InnoDB / utf8mb4  |  30 tables
--
-- 기준 문서
--   - 요구사항 명세서 v4.0
--   - 팀 의사결정 목록
--   - SSAFY 교육용 금융망 API 명세
--
-- 개정 내용
--   - ai_extractions 삭제 (AI 결과는 products에 저장, 동기 호출)
--   - slot_reservations 삭제 → payments의 PROCESSING 상태로 통합
--
-- 결제  : SSAFY 금융망 카드 / 카카오페이 / MetaMask + Polygon Amoy
-- 정산  : SSAFY 금융망 계좌 이체 (플랫폼 계좌 -> 판매자 계좌)
-- 수수료: 상품 분담금 합계의 5% (배송비 제외, 판매자 부담)
-- 배송비: 판매자가 공동구매 단위로 기입, 반품 배송비는 그 2배(왕복)
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- =====================================================================
-- 1. 계정
-- =====================================================================

CREATE TABLE buyers (
    buyer_id         BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    nickname         VARCHAR(50)  NOT NULL,
    phone            VARCHAR(20)  NULL,
    ssafy_user_key   VARCHAR(60)  NULL COMMENT 'SSAFY member 생성 응답 userKey',
    ssafy_account_no VARCHAR(16)  NULL COMMENT '카드 취소 불가 시 환불 수취 계좌',
    wallet_address   VARCHAR(42)  NULL COMMENT '최근 사용 MetaMask 주소',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                     COMMENT 'ACTIVE / DORMANT / WITHDRAWN',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (buyer_id),
    UNIQUE KEY uk_buyers_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '구매자 계정 (판매자와 분리)';


CREATE TABLE buyer_addresses (
    address_id      BIGINT       NOT NULL AUTO_INCREMENT,
    buyer_id        BIGINT       NOT NULL,
    alias           VARCHAR(50)  NULL,
    recipient_name  VARCHAR(50)  NOT NULL,
    recipient_phone VARCHAR(20)  NOT NULL,
    zipcode         CHAR(5)      NOT NULL,
    address1        VARCHAR(255) NOT NULL,
    address2        VARCHAR(255) NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (address_id),
    KEY idx_buyer_addresses_buyer (buyer_id, is_default),
    CONSTRAINT fk_buyer_addresses_buyer
        FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '구매자 배송지';


CREATE TABLE sellers (
    seller_id        BIGINT       NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    business_name    VARCHAR(100) NOT NULL,
    phone            VARCHAR(20)  NULL,
    ssafy_user_key   VARCHAR(60)  NULL,
    ssafy_account_no VARCHAR(16)  NULL COMMENT '정산 수취 계좌',
    account_holder   VARCHAR(50)  NULL,
    return_zipcode   CHAR(5)      NULL COMMENT '반품 수취 주소',
    return_address1  VARCHAR(255) NULL,
    return_address2  VARCHAR(255) NULL,
    penalty_count    INT          NOT NULL DEFAULT 0 COMMENT '3회 누적 시 정지',
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                     COMMENT 'ACTIVE / WARNED / SUSPENDED',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (seller_id),
    UNIQUE KEY uk_sellers_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '판매자 계정';


CREATE TABLE admins (
    admin_id      BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'OPERATOR',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (admin_id),
    UNIQUE KEY uk_admins_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '관리자 계정';


-- =====================================================================
-- 2. 상품
-- =====================================================================

CREATE TABLE categories (
    category_id BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id   BIGINT      NULL,
    name        VARCHAR(50) NOT NULL,
    depth       INT         NOT NULL DEFAULT 1,
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (category_id),
    KEY idx_categories_parent (parent_id, sort_order),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '상품 카테고리 (AI 추출 대상)';


CREATE TABLE withdrawal_restriction_reasons (
    reason_id         BIGINT       NOT NULL AUTO_INCREMENT,
    code              VARCHAR(30)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    description       VARCHAR(500) NULL,
    requires_evidence BOOLEAN      NOT NULL DEFAULT TRUE,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (reason_id),
    UNIQUE KEY uk_restriction_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '청약철회 제한 사유 마스터';


CREATE TABLE products (
    product_id                BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id                 BIGINT       NOT NULL,
    category_id               BIGINT       NULL COMMENT 'AI 추출',
    name                      VARCHAR(200) NOT NULL COMMENT 'AI 추출',
    brand                     VARCHAR(100) NULL COMMENT '판매자 직접 입력',
    model_name                VARCHAR(100) NULL COMMENT '판매자 직접 입력',
    color                     VARCHAR(50)  NULL COMMENT '판매자 직접 입력',
    description               TEXT         NULL,
    price                     BIGINT       NOT NULL COMMENT '원 단위',
    ai_extracted              BOOLEAN      NOT NULL DEFAULT FALSE
                              COMMENT 'AI 자동 등록 사용 여부',
    withdrawal_restricted     BOOLEAN      NOT NULL DEFAULT FALSE,
    restriction_reason_id     BIGINT       NULL,
    restriction_evidence_url  VARCHAR(500) NULL COMMENT '증빙자료',
    restriction_review_status VARCHAR(20)  NOT NULL DEFAULT 'NONE'
                              COMMENT 'NONE / PENDING / APPROVED / REJECTED',
    restriction_reviewed_by   BIGINT       NULL,
    restriction_reviewed_at   DATETIME     NULL,
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                              COMMENT 'ACTIVE / DELETED',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    KEY idx_products_seller (seller_id, status),
    KEY idx_products_category (category_id, status),
    KEY idx_products_restriction (restriction_review_status),
    CONSTRAINT fk_products_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (seller_id),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories (category_id),
    CONSTRAINT fk_products_restriction_reason
        FOREIGN KEY (restriction_reason_id)
        REFERENCES withdrawal_restriction_reasons (reason_id),
    CONSTRAINT fk_products_restriction_admin
        FOREIGN KEY (restriction_reviewed_by) REFERENCES admins (admin_id),
    CONSTRAINT ck_products_price CHECK (price > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '상품. AI 추출 결과는 검수 후 여기에만 저장';


CREATE TABLE product_images (
    image_id     BIGINT       NOT NULL AUTO_INCREMENT,
    product_id   BIGINT       NOT NULL,
    image_url    VARCHAR(500) NOT NULL,
    sort_order   INT          NOT NULL DEFAULT 0,
    is_thumbnail BOOLEAN      NOT NULL DEFAULT FALSE
                 COMMENT 'AI 분석 대상 대표 이미지 1장',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (image_id),
    KEY idx_product_images_product (product_id, sort_order),
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products (product_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '상품 이미지';


-- =====================================================================
-- 3. 정책
-- =====================================================================

CREATE TABLE platform_policies (
    policy_key   VARCHAR(50)  NOT NULL,
    policy_value VARCHAR(100) NOT NULL,
    description  VARCHAR(255) NULL,
    updated_by   BIGINT       NULL,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (policy_key),
    CONSTRAINT fk_platform_policies_admin
        FOREIGN KEY (updated_by) REFERENCES admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '운영 정책값';


-- =====================================================================
-- 4. 공동구매
-- =====================================================================

CREATE TABLE group_buys (
    group_buy_id               BIGINT        NOT NULL AUTO_INCREMENT,
    product_id                 BIGINT        NOT NULL,
    seller_id                  BIGINT        NOT NULL,
    product_price              BIGINT        NOT NULL,
    target_participants        INT           NOT NULL COMMENT '최소 2명',
    unit_share_amount          BIGINT        NOT NULL COMMENT '확정 분담금, 불변',
    shipping_fee               BIGINT        NOT NULL COMMENT '판매자 기입',
    return_shipping_fee        BIGINT        GENERATED ALWAYS AS (shipping_fee * 2) STORED
                               COMMENT '왕복이므로 배송비의 2배',
    rounding_diff              BIGINT        NOT NULL DEFAULT 0 COMMENT '절상 차액',
    platform_fee_rate          DECIMAL(5, 4) NOT NULL DEFAULT 0.0500
                               COMMENT '정률 5%, 상품 분담금 기준 (배송비 제외)',
    expected_platform_fee      BIGINT        NOT NULL,
    expected_settlement_amount BIGINT        NOT NULL,
    recruit_start_at           DATETIME      NOT NULL,
    recruit_end_at             DATETIME      NOT NULL,
    current_participants       INT           NOT NULL DEFAULT 0
                               COMMENT '결제 완료(PAID) 참여 수',
    reserved_slots             INT           NOT NULL DEFAULT 0
                               COMMENT '결제 진행중(PROCESSING) 건 수',
    fulfilled_at               DATETIME      NULL,
    shipping_deadline_at       DATETIME      NULL,
    status                     VARCHAR(20)   NOT NULL DEFAULT 'RECRUITING'
                               COMMENT 'RECRUITING/FULFILLED/SHIPPING/DELIVERED/COMPLETED/SETTLED/FAILED/CANCELED',
    version                    BIGINT        NOT NULL DEFAULT 0,
    created_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                               ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (group_buy_id),
    KEY idx_group_buys_status_end (status, recruit_end_at),
    KEY idx_group_buys_seller (seller_id, status),
    KEY idx_group_buys_deadline (status, shipping_deadline_at),
    KEY idx_group_buys_product (product_id),
    CONSTRAINT fk_group_buys_product
        FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT fk_group_buys_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (seller_id),
    CONSTRAINT ck_group_buys_target CHECK (target_participants >= 2),
    CONSTRAINT ck_group_buys_slots
        CHECK (current_participants + reserved_slots <= target_participants)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '공동구매';


CREATE TABLE participations (
    participation_id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_buy_id             BIGINT       NOT NULL,
    buyer_id                 BIGINT       NOT NULL,
    share_amount             BIGINT       NOT NULL COMMENT '분담금 스냅샷',
    shipping_fee             BIGINT       NOT NULL COMMENT '배송비 스냅샷',
    total_amount             BIGINT       NOT NULL COMMENT '개인 결제금액',
    recipient_name           VARCHAR(50)  NOT NULL,
    recipient_phone          VARCHAR(20)  NOT NULL,
    zipcode                  CHAR(5)      NOT NULL,
    address1                 VARCHAR(255) NOT NULL,
    address2                 VARCHAR(255) NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'PAID'
                             COMMENT 'PAID/FULFILLED/SHIPPING/DELIVERED/CONFIRMED/SETTLED/CANCELED/RETURNING/RETURNED/REFUNDED',
    joined_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at             DATETIME     NULL,
    withdrawal_period_end_at DATETIME     NULL
                             COMMENT '청약철회 종료 = 배송완료 + 7일',
    settlement_eligible_at   DATETIME     NULL
                             COMMENT '정산 자격 시각. 수동 확정해도 이 시각 전에는 정산 불가',
    confirmed_at             DATETIME     NULL,
    confirm_type             VARCHAR(10)  NULL COMMENT 'MANUAL / AUTO',
    confirm_notice_agreed    BOOLEAN      NOT NULL DEFAULT FALSE
                             COMMENT '수동 확정 시 반품 제한 고지 동의',
    confirm_hold             BOOLEAN      NOT NULL DEFAULT FALSE
                             COMMENT '반품 진행 중 자동확정 보류',
    settled                  BOOLEAN      NOT NULL DEFAULT FALSE,
    active_marker            CHAR(1)      NULL COMMENT '활성 Y, 취소 시 NULL',
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                             ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (participation_id),
    UNIQUE KEY uk_participation_active (group_buy_id, buyer_id, active_marker),
    KEY idx_participation_group (group_buy_id, status),
    KEY idx_participation_buyer (buyer_id, status),
    KEY idx_participation_autoconfirm (status, confirm_hold, withdrawal_period_end_at),
    KEY idx_participation_settle (status, settled, settlement_eligible_at),
    CONSTRAINT fk_participation_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id),
    CONSTRAINT fk_participation_buyer
        FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '공동구매 참여';


-- =====================================================================
-- 5. SSAFY 금융망 호출 이력
-- =====================================================================

CREATE TABLE ssafy_api_logs (
    log_id            BIGINT       NOT NULL AUTO_INCREMENT,
    api_service_code  VARCHAR(30)  NOT NULL COMMENT 'Header.apiServiceCode',
    institution_tx_no VARCHAR(20)  NOT NULL COMMENT 'Header.institutionTransactionUniqueNo',
    transmission_date CHAR(8)      NOT NULL COMMENT 'YYYYMMDD',
    target_type       VARCHAR(30)  NULL COMMENT 'PAYMENT / REFUND / SETTLEMENT',
    target_id         BIGINT       NULL,
    request_json      JSON         NULL,
    response_code     VARCHAR(10)  NULL COMMENT 'H0000 등',
    response_message  VARCHAR(255) NULL,
    response_json     JSON         NULL,
    called_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    UNIQUE KEY uk_ssafy_tx_no (api_service_code, institution_tx_no),
    KEY idx_ssafy_target (target_type, target_id),
    KEY idx_ssafy_called (called_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '기관거래고유번호 유일성 보장 및 호출 이력 (H1007 방지)';


-- =====================================================================
-- 6. 결제 (슬롯 선점 통합)
-- =====================================================================

CREATE TABLE payments (
    payment_id       BIGINT       NOT NULL AUTO_INCREMENT,
    group_buy_id     BIGINT       NOT NULL,
    buyer_id         BIGINT       NOT NULL,
    participation_id BIGINT       NULL COMMENT '결제 완료 후 연결',
    method           VARCHAR(20)  NOT NULL COMMENT 'CARD / KAKAOPAY / CRYPTO',
    krw_amount       BIGINT       NOT NULL COMMENT 'KRW 기준 거래가치',
    slot_expires_at  DATETIME     NOT NULL
                     COMMENT '슬롯 만료. 카드·페이 5분, 코인 10분',
    slot_marker      CHAR(1)      NULL
                     COMMENT 'PROCESSING일 때 Y, 그 외 NULL (중복 선점 차단)',
    idempotency_key  VARCHAR(64)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING'
                     COMMENT 'PROCESSING(슬롯 점유) / PAID / FAILED / EXPIRED / REFUNDED',
    approved_at      DATETIME     NULL,
    fail_reason      VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payments_idempotency (idempotency_key),
    UNIQUE KEY uk_payments_participation (participation_id),
    UNIQUE KEY uk_payments_slot (group_buy_id, buyer_id, slot_marker),
    KEY idx_payments_slot_expire (status, slot_expires_at),
    KEY idx_payments_group (group_buy_id, status),
    KEY idx_payments_buyer (buyer_id, status),
    CONSTRAINT fk_payments_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id),
    CONSTRAINT fk_payments_buyer
        FOREIGN KEY (buyer_id) REFERENCES buyers (buyer_id),
    CONSTRAINT fk_payments_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '결제 공통 + 슬롯 선점. PROCESSING 상태가 곧 선점된 슬롯';


-- ---------------------------------------------------------------------
-- 6-1. 카드 결제 (SSAFY createCreditCardTransaction)
-- ---------------------------------------------------------------------

CREATE TABLE card_payments (
    payment_id               BIGINT       NOT NULL,
    ssafy_user_key           VARCHAR(60)  NULL COMMENT '구매자 userKey',
    card_no_masked           VARCHAR(19)  NULL COMMENT '카드번호·CVC는 저장하지 않음',
    merchant_id              BIGINT       NULL COMMENT '공구한 가맹점 ID',
    merchant_name            VARCHAR(100) NULL,
    category_id              VARCHAR(20)  NULL,
    institution_tx_no        VARCHAR(20)  NULL COMMENT '결제 요청 기관거래고유번호',
    transaction_unique_no    BIGINT       NULL COMMENT 'SSAFY 거래고유번호. 취소 시 필요',
    transaction_date         CHAR(8)      NULL COMMENT 'YYYYMMDD',
    transaction_time         CHAR(6)      NULL COMMENT 'HHMMSS',
    payment_balance          BIGINT       NULL COMMENT '결제금액',
    billing_issued           BOOLEAN      NOT NULL DEFAULT FALSE
                             COMMENT '청구서 발행 시 deleteTransaction 불가',
    billing_issued_at        DATETIME     NULL,
    cancel_institution_tx_no VARCHAR(20)  NULL,
    cancel_status            VARCHAR(10)  NULL COMMENT 'SUCCESS / FAIL / CANCEL',
    canceled_at              DATETIME     NULL,
    raw_response             JSON         NULL,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_card_tx_no (transaction_unique_no),
    KEY idx_card_billing (billing_issued, transaction_date),
    CONSTRAINT fk_card_payments_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = 'SSAFY 카드 결제. 청구서 발행 후에는 취소 불가 → 계좌 이체 환불';


-- ---------------------------------------------------------------------
-- 6-2. 카카오페이 결제
-- ---------------------------------------------------------------------

CREATE TABLE kakaopay_payments (
    payment_id          BIGINT       NOT NULL,
    tid                 VARCHAR(100) NULL COMMENT '결제 준비 응답 TID',
    aid                 VARCHAR(100) NULL COMMENT '승인 ID',
    partner_order_id    VARCHAR(100) NULL,
    partner_user_id     VARCHAR(100) NULL,
    total_amount        BIGINT       NULL,
    tax_free_amount     BIGINT       NOT NULL DEFAULT 0
                        COMMENT '모든 상품 과세 처리 → 항상 0',
    vat_amount          BIGINT       NULL COMMENT '카카오페이 자동 계산',
    payment_method_type VARCHAR(20)  NULL COMMENT 'CARD / MONEY',
    raw_response        JSON         NULL,
    PRIMARY KEY (payment_id),
    KEY idx_kakaopay_tid (tid),
    CONSTRAINT fk_kakaopay_payments_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '카카오페이 결제 상세';


-- ---------------------------------------------------------------------
-- 6-3. 코인 결제 (MetaMask + Polygon Amoy + ERC-20)
-- ---------------------------------------------------------------------

CREATE TABLE crypto_assets (
    asset_id               BIGINT      NOT NULL AUTO_INCREMENT,
    symbol                 VARCHAR(10) NOT NULL COMMENT 'USDT',
    chain_id               BIGINT      NOT NULL COMMENT 'Polygon Amoy = 80002',
    chain_name             VARCHAR(30) NOT NULL,
    token_contract         VARCHAR(42) NOT NULL,
    decimals               INT         NOT NULL COMMENT 'USDT = 6',
    required_confirmations INT         NOT NULL DEFAULT 5,
    enabled                BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_crypto_assets_token (chain_id, token_contract)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '지원 디지털 자산';


CREATE TABLE crypto_wallets (
    wallet_id  BIGINT       NOT NULL AUTO_INCREMENT,
    chain_id   BIGINT       NOT NULL,
    address    VARCHAR(42)  NOT NULL COMMENT '개인키는 DB에 저장 금지',
    purpose    VARCHAR(10)  NOT NULL COMMENT 'RECEIVE / REFUND',
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    memo       VARCHAR(255) NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (wallet_id),
    UNIQUE KEY uk_crypto_wallets_addr (chain_id, address, purpose),
    KEY idx_crypto_wallets_active (chain_id, purpose, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '플랫폼 지갑. 개인키는 환경변수/시크릿으로만 관리';


CREATE TABLE crypto_quotes (
    quote_id     BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id     BIGINT          NOT NULL,
    market_rate  DECIMAL(20, 8)  NOT NULL COMMENT '거래소 시세',
    buffer_rate  DECIMAL(6, 4)   NOT NULL COMMENT '0.0030',
    applied_rate DECIMAL(20, 8)  NOT NULL,
    krw_amount   BIGINT          NOT NULL,
    asset_amount DECIMAL(36, 18) NOT NULL,
    issued_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at   DATETIME        NOT NULL COMMENT '10분. 슬롯 만료와 동일',
    used         BOOLEAN         NOT NULL DEFAULT FALSE,
    PRIMARY KEY (quote_id),
    KEY idx_quotes_expire (expires_at, used),
    CONSTRAINT fk_quotes_asset
        FOREIGN KEY (asset_id) REFERENCES crypto_assets (asset_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '코인 결제 Quote';


CREATE TABLE crypto_payments (
    payment_id             BIGINT          NOT NULL,
    asset_id               BIGINT          NOT NULL,
    quote_id               BIGINT          NOT NULL,
    wallet_id              BIGINT          NOT NULL,
    chain_id               BIGINT          NOT NULL,
    token_contract         VARCHAR(42)     NOT NULL COMMENT '스냅샷',
    from_address           VARCHAR(42)     NOT NULL COMMENT 'MetaMask 연결 계정',
    to_address             VARCHAR(42)     NOT NULL COMMENT '수취 주소 스냅샷',
    asset_amount           DECIMAL(36, 18) NOT NULL,
    raw_amount             VARCHAR(78)     NOT NULL COMMENT 'uint256 원본',
    tx_hash                VARCHAR(66)     NULL,
    block_number           BIGINT          NULL,
    confirmations          INT             NOT NULL DEFAULT 0,
    required_confirmations INT             NOT NULL COMMENT '스냅샷',
    receipt_status         VARCHAR(10)     NULL COMMENT 'SUCCESS / REVERTED',
    confirmed_at           DATETIME        NULL,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_crypto_payments_tx (tx_hash),
    KEY idx_crypto_payments_polling (confirmations, required_confirmations),
    KEY idx_crypto_payments_from (chain_id, from_address),
    CONSTRAINT fk_crypto_payments_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_crypto_payments_asset
        FOREIGN KEY (asset_id) REFERENCES crypto_assets (asset_id),
    CONSTRAINT fk_crypto_payments_quote
        FOREIGN KEY (quote_id) REFERENCES crypto_quotes (quote_id),
    CONSTRAINT fk_crypto_payments_wallet
        FOREIGN KEY (wallet_id) REFERENCES crypto_wallets (wallet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '코인 결제 상세. tx_hash UNIQUE로 중복 처리 차단';


CREATE TABLE crypto_deposits (
    deposit_id     BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id     BIGINT          NULL COMMENT '매칭된 결제 건',
    chain_id       BIGINT          NOT NULL,
    tx_hash        VARCHAR(66)     NOT NULL,
    log_index      INT             NOT NULL,
    block_number   BIGINT          NOT NULL,
    token_contract VARCHAR(42)     NOT NULL,
    from_address   VARCHAR(42)     NOT NULL,
    to_address     VARCHAR(42)     NOT NULL,
    raw_amount     VARCHAR(78)     NOT NULL,
    asset_amount   DECIMAL(36, 18) NOT NULL,
    confirmations  INT             NOT NULL DEFAULT 0,
    status         VARCHAR(15)     NOT NULL DEFAULT 'DETECTED'
                   COMMENT 'DETECTED/MATCHED/UNMATCHED/UNDERPAID/OVERPAID/REFUNDED',
    memo           VARCHAR(255)    NULL,
    detected_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    matched_at     DATETIME        NULL,
    PRIMARY KEY (deposit_id),
    UNIQUE KEY uk_deposits_tx_log (tx_hash, log_index),
    KEY idx_deposits_status (status, detected_at),
    KEY idx_deposits_from (chain_id, from_address),
    KEY idx_deposits_payment (payment_id),
    CONSTRAINT fk_deposits_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '온체인 입금 감지 원본. 미매칭 입금도 무조건 기록';


CREATE TABLE chain_scan_cursors (
    chain_id           BIGINT   NOT NULL COMMENT 'Polygon Amoy = 80002',
    last_scanned_block BIGINT   NOT NULL,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                       ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (chain_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '이벤트 폴링 커서. 재시작 시 여기서부터 재스캔';


-- =====================================================================
-- 7. 에스크로 · 배송
-- =====================================================================

CREATE TABLE escrows (
    escrow_id        BIGINT      NOT NULL AUTO_INCREMENT,
    participation_id BIGINT      NOT NULL,
    group_buy_id     BIGINT      NOT NULL,
    amount           BIGINT      NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'HELD'
                     COMMENT 'HELD / RELEASED / REFUNDED',
    held_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at      DATETIME    NULL COMMENT '정산 시',
    refunded_at      DATETIME    NULL,
    PRIMARY KEY (escrow_id),
    UNIQUE KEY uk_escrows_participation (participation_id),
    KEY idx_escrows_group (group_buy_id, status),
    CONSTRAINT fk_escrows_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id),
    CONSTRAINT fk_escrows_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '에스크로. 공구한 내부 원장 기반';


CREATE TABLE deliveries (
    delivery_id      BIGINT      NOT NULL AUTO_INCREMENT,
    participation_id BIGINT      NOT NULL,
    courier_code     VARCHAR(20) NULL,
    tracking_number  VARCHAR(50) NULL,
    shipped_at       DATETIME    NULL,
    delivered_at     DATETIME    NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'READY'
                     COMMENT 'READY / SHIPPING / DELIVERED',
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                     ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uk_deliveries_participation (participation_id),
    UNIQUE KEY uk_deliveries_tracking (courier_code, tracking_number),
    CONSTRAINT fk_deliveries_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '배송 (참여자별 개별 관리). 알림은 택배사가 수행';


-- =====================================================================
-- 8. 반품
-- =====================================================================

CREATE TABLE return_requests (
    return_id             BIGINT       NOT NULL AUTO_INCREMENT,
    participation_id      BIGINT       NOT NULL,
    reason_type           VARCHAR(20)  NOT NULL
                          COMMENT 'CHANGE_OF_MIND / DEFECT',
    reason_detail         VARCHAR(500) NULL,
    return_shipping_fee   BIGINT       NOT NULL DEFAULT 0
                          COMMENT '배송비 x2 (왕복)',
    fee_bearer            VARCHAR(10)  NOT NULL COMMENT 'BUYER / SELLER',
    refund_amount         BIGINT       NOT NULL DEFAULT 0,
    post_confirm          BOOLEAN      NOT NULL DEFAULT FALSE
                          COMMENT '구매확정 후 요청 → 관리자 검토 대상',
    admin_review_required BOOLEAN      NOT NULL DEFAULT FALSE,
    status                VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED'
                          COMMENT 'REQUESTED/APPROVED/PICKED_UP/REFUNDED/REJECTED/DISPUTED',
    requested_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    auto_refund_due_at    DATETIME     NULL
                          COMMENT '신청 + 7일. 경과 시 자동 환불',
    approved_at           DATETIME     NULL,
    approve_type          VARCHAR(10)  NULL COMMENT 'MANUAL / AUTO',
    picked_up_at          DATETIME     NULL COMMENT '판매자 직접 수거 확인',
    auto_refunded         BOOLEAN      NOT NULL DEFAULT FALSE
                          COMMENT '수거 미확인 상태로 7일 경과하여 자동 환불',
    forced_by             BIGINT       NULL COMMENT '관리자 강제 처리',
    forced_at             DATETIME     NULL,
    completed_at          DATETIME     NULL,
    reject_reason         VARCHAR(255) NULL,
    PRIMARY KEY (return_id),
    KEY idx_returns_participation (participation_id),
    KEY idx_returns_auto_refund (status, auto_refund_due_at),
    KEY idx_returns_review (admin_review_required, requested_at),
    CONSTRAINT fk_returns_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id),
    CONSTRAINT fk_returns_admin
        FOREIGN KEY (forced_by) REFERENCES admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '반품. 수거 미확인 7일 경과 시 자동 환불';


-- =====================================================================
-- 9. 정산
-- =====================================================================

CREATE TABLE settlements (
    settlement_id        BIGINT        NOT NULL AUTO_INCREMENT,
    group_buy_id         BIGINT        NOT NULL,
    seller_id            BIGINT        NOT NULL,
    round_no             INT           NOT NULL DEFAULT 1 COMMENT '일 1회 배치 회차',
    confirmed_count      INT           NOT NULL DEFAULT 0,
    share_total          BIGINT        NOT NULL DEFAULT 0,
    shipping_total       BIGINT        NOT NULL DEFAULT 0,
    rounding_diff        BIGINT        NOT NULL DEFAULT 0,
    platform_fee_rate    DECIMAL(5, 4) NOT NULL COMMENT '스냅샷',
    platform_fee         BIGINT        NOT NULL DEFAULT 0
                         COMMENT 'share_total x rate (배송비 제외)',
    settlement_amount    BIGINT        NOT NULL DEFAULT 0,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                         COMMENT 'PENDING / COMPLETED / HOLD',
    transfer_status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                         COMMENT 'SSAFY 계좌 이체 상태',
    institution_tx_no    VARCHAR(20)   NULL COMMENT '이체 요청 기관거래고유번호',
    withdrawal_tx_no     BIGINT        NULL COMMENT '출금 거래고유번호',
    deposit_tx_no        BIGINT        NULL COMMENT '입금 거래고유번호',
    transferred_at       DATETIME      NULL,
    transfer_fail_reason VARCHAR(255)  NULL,
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (settlement_id),
    UNIQUE KEY uk_settlements_round (group_buy_id, round_no),
    KEY idx_settlements_seller (seller_id, status),
    KEY idx_settlements_transfer (transfer_status, created_at),
    CONSTRAINT fk_settlements_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id),
    CONSTRAINT fk_settlements_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (seller_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '판매자 정산. 플랫폼 계좌 → 판매자 계좌 SSAFY 이체';


CREATE TABLE settlement_items (
    item_id          BIGINT NOT NULL AUTO_INCREMENT,
    settlement_id    BIGINT NOT NULL,
    participation_id BIGINT NOT NULL,
    share_amount     BIGINT NOT NULL,
    shipping_fee     BIGINT NOT NULL,
    PRIMARY KEY (item_id),
    UNIQUE KEY uk_settlement_items_participation (participation_id),
    KEY idx_settlement_items_settlement (settlement_id),
    CONSTRAINT fk_settlement_items_settlement
        FOREIGN KEY (settlement_id) REFERENCES settlements (settlement_id),
    CONSTRAINT fk_settlement_items_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '정산 대상 참여 건';


-- =====================================================================
-- 10. 환불
-- =====================================================================

CREATE TABLE refunds (
    refund_id            BIGINT          NOT NULL AUTO_INCREMENT,
    participation_id     BIGINT          NULL COMMENT '미매칭 입금 환불 시 NULL',
    return_id            BIGINT          NULL,
    deposit_id           BIGINT          NULL,
    amount               BIGINT          NOT NULL COMMENT 'KRW 기준',
    reason_type          VARCHAR(30)     NOT NULL
                         COMMENT 'RECRUIT_FAILED/PARTICIPATION_CANCEL/SLOT_EXPIRED/RETURN/SELLER_FAULT/UNMATCHED_DEPOSIT',
    refund_method        VARCHAR(20)     NOT NULL
                         COMMENT 'CARD_CANCEL / ACCOUNT_TRANSFER / KAKAOPAY / CRYPTO',
    institution_tx_no    VARCHAR(20)     NULL COMMENT 'SSAFY 요청 기관거래고유번호',
    ssafy_transaction_no BIGINT          NULL COMMENT 'SSAFY 거래고유번호',
    to_account_no        VARCHAR(16)     NULL COMMENT '계좌 이체 환불 시',
    to_address           VARCHAR(42)     NULL COMMENT '코인 환불 수취 주소',
    asset_amount         DECIMAL(36, 18) NULL,
    tx_hash              VARCHAR(66)     NULL,
    status               VARCHAR(20)     NOT NULL DEFAULT 'PROCESSING'
                         COMMENT 'PROCESSING / COMPLETED / FAILED',
    fail_reason          VARCHAR(255)    NULL,
    created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at         DATETIME        NULL,
    PRIMARY KEY (refund_id),
    UNIQUE KEY uk_refunds_tx (tx_hash),
    KEY idx_refunds_participation (participation_id),
    KEY idx_refunds_status (status, created_at),
    CONSTRAINT fk_refunds_participation
        FOREIGN KEY (participation_id) REFERENCES participations (participation_id),
    CONSTRAINT fk_refunds_return
        FOREIGN KEY (return_id) REFERENCES return_requests (return_id),
    CONSTRAINT fk_refunds_deposit
        FOREIGN KEY (deposit_id) REFERENCES crypto_deposits (deposit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '환불. 카드 취소 실패 시 계좌 이체로 폴백';


-- =====================================================================
-- 11. 제재 · 운영
-- =====================================================================

CREATE TABLE seller_penalties (
    penalty_id   BIGINT       NOT NULL AUTO_INCREMENT,
    seller_id    BIGINT       NOT NULL,
    group_buy_id BIGINT       NULL,
    reason_type  VARCHAR(30)  NOT NULL
                 COMMENT 'NO_INVOICE/FAKE_INVOICE/ITEM_MISMATCH/WITHDRAWAL/RETURN_REFUSAL/RETURN_NEGLECT',
    detail       VARCHAR(500) NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'CANDIDATE'
                 COMMENT 'CANDIDATE / CONFIRMED / DISMISSED / REVOKED',
    detected_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by  BIGINT       NULL,
    reviewed_at  DATETIME     NULL,
    memo         VARCHAR(500) NULL,
    PRIMARY KEY (penalty_id),
    KEY idx_penalties_seller (seller_id, status),
    KEY idx_penalties_status (status, detected_at),
    CONSTRAINT fk_penalties_seller
        FOREIGN KEY (seller_id) REFERENCES sellers (seller_id),
    CONSTRAINT fk_penalties_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id),
    CONSTRAINT fk_penalties_admin
        FOREIGN KEY (reviewed_by) REFERENCES admins (admin_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '판매자 위반 기록 (영구 누적, 3회 시 판매 정지)';


CREATE TABLE notifications (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    recipient_type  VARCHAR(10)  NOT NULL COMMENT 'BUYER / SELLER',
    recipient_id    BIGINT       NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    title           VARCHAR(100) NOT NULL,
    body            VARCHAR(500) NULL,
    link_url        VARCHAR(255) NULL,
    read_at         DATETIME     NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    KEY idx_notifications_recipient (recipient_type, recipient_id, read_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '인앱 알림';


CREATE TABLE audit_logs (
    log_id      BIGINT      NOT NULL AUTO_INCREMENT,
    actor_type  VARCHAR(10) NOT NULL COMMENT 'BUYER / SELLER / ADMIN / SYSTEM',
    actor_id    BIGINT      NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id   BIGINT      NULL,
    action      VARCHAR(50) NOT NULL,
    before_json JSON        NULL,
    after_json  JSON        NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_audit_target (target_type, target_id, created_at),
    KEY idx_audit_actor (actor_type, actor_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '감사 로그';


SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
-- 초기 데이터
-- =====================================================================

-- 청약철회 제한 사유
INSERT INTO withdrawal_restriction_reasons
    (code, name, description, requires_evidence, sort_order) VALUES
    ('PERISHABLE',   '신선식품 등 변질 우려 상품',
     '시간 경과에 따라 재판매가 곤란할 정도로 가치가 감소하는 상품', TRUE, 1),
    ('HYGIENE',      '개봉 시 재판매 불가한 위생 상품',
     '포장을 훼손하면 재판매가 곤란한 위생 관련 상품', TRUE, 2),
    ('CUSTOM_MADE',  '주문 제작 상품',
     '구매자의 주문에 따라 개별적으로 생산되는 상품', TRUE, 3),
    ('SEALED_MEDIA', '복제 가능한 포장 훼손 상품',
     '음반, 영상물, 소프트웨어 등 포장을 훼손한 경우', FALSE, 4),
    ('DIGITAL',      '디지털 콘텐츠',
     '다운로드 또는 즉시 이용이 개시된 디지털 콘텐츠', FALSE, 5);

-- 코인 결제 (토큰 배포 후 실제 주소로 교체)
INSERT INTO crypto_assets
    (symbol, chain_id, chain_name, token_contract, decimals, required_confirmations, enabled)
VALUES
    ('USDT', 80002, 'Polygon Amoy',
     '0x0000000000000000000000000000000000000000', 6, 5, TRUE);

INSERT INTO crypto_wallets (chain_id, address, purpose, active, memo) VALUES
    (80002, '0x0000000000000000000000000000000000000000', 'RECEIVE', TRUE, '결제 수취'),
    (80002, '0x0000000000000000000000000000000000000000', 'REFUND',  TRUE, '환불 송금');

INSERT INTO chain_scan_cursors (chain_id, last_scanned_block) VALUES (80002, 0);

-- 운영 정책값
INSERT INTO platform_policies (policy_key, policy_value, description) VALUES
    -- 수수료 및 인원
    ('PLATFORM_FEE_RATE',        '0.05',  '수수료율 (상품 분담금 기준, 배송비 제외)'),
    ('MIN_TARGET_PARTICIPANTS',  '2',     '목표 모집 인원 하한'),
    -- 슬롯 (payments.PROCESSING)
    ('SLOT_TTL_CARD_SEC',        '300',   '카드·카카오페이 슬롯 유효시간'),
    ('SLOT_TTL_CRYPTO_SEC',      '600',   '코인 슬롯 유효시간'),
    -- 코인
    ('QUOTE_TTL_SEC',            '600',   'Quote 유효시간 (슬롯과 동일)'),
    ('CRYPTO_BUFFER_RATE',       '0.003', '코인 결제 Buffer율'),
    ('CHAIN_SCAN_INTERVAL_MS',   '5000',  '이벤트 폴링 주기'),
    ('CHAIN_SCAN_BATCH_BLOCKS',  '500',   '1회 스캔 최대 블록 수'),
    -- 기한
    ('SHIPPING_DEADLINE_DAYS',   '3',     '모집 완료 후 송장 등록 기한 (영업일)'),
    ('AUTO_CONFIRM_DAYS',        '7',     '배송완료 후 자동 구매확정 기한'),
    ('WITHDRAWAL_PERIOD_DAYS',   '7',     '청약철회 가능 기간'),
    ('SETTLEMENT_ELIGIBLE_DAYS', '7',     '배송완료 후 정산 자격 발생까지'),
    ('RETURN_APPROVE_DAYS',      '3',     '반품 자동 승인 기한 (영업일)'),
    ('RETURN_AUTO_REFUND_DAYS',  '7',     '수거 미확인 시 자동 환불 기한'),
    ('CARD_BILLING_CUTOFF_DAYS', '8',     '카드 청구서 발행 추정 경과일'),
    ('PENALTY_LIMIT',            '3',     '판매 정지 위반 누적 횟수'),
    ('SETTLEMENT_BATCH_HOUR',    '3',     '정산 배치 실행 시각 (일 1회)'),
    -- SSAFY 금융망 (apiKey / 플랫폼 userKey는 환경변수로 관리)
    ('SSAFY_MERCHANT_ID',        '',      'createMerchant 응답 merchantId'),
    ('SSAFY_PLATFORM_ACCOUNT_NO','',      '공구한 정산 출금 계좌번호'),
    ('SSAFY_INSTITUTION_CODE',   '00100', '기관코드 고정값'),
    ('SSAFY_FINTECH_APP_NO',     '001',   '핀테크 앱 일련번호 고정값');
