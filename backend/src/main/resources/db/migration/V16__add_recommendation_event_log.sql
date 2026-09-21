CREATE TABLE recommendation_sessions (
    session_id     VARCHAR(36) NOT NULL,
    user_id        BIGINT      NOT NULL,
    started_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_event_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at       DATETIME    NULL,
    end_reason     VARCHAR(20) NULL COMMENT 'PAGEHIDE / TIMEOUT',
    PRIMARY KEY (session_id),
    KEY idx_recommendation_sessions_user_time (user_id, last_event_at),
    CONSTRAINT fk_recommendation_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '개인화 추천 행동 세션. 30분 무활동도 종료 세션으로 해석';

CREATE TABLE recommendation_events (
    event_id             BIGINT        NOT NULL AUTO_INCREMENT,
    session_id           VARCHAR(36)   NOT NULL,
    user_id              BIGINT        NOT NULL,
    group_buy_id         BIGINT        NOT NULL,
    event_type           VARCHAR(20)   NOT NULL COMMENT 'IMPRESSION / CLICK / DETAIL_VIEW',
    source               VARCHAR(30)   NOT NULL DEFAULT 'UNKNOWN',
    position             INT           NULL,
    product_id_snapshot   BIGINT        NOT NULL,
    brand_snapshot       VARCHAR(100)  NULL,
    category_id_snapshot BIGINT        NULL,
    recruitment_rate     DECIMAL(7, 6) NOT NULL,
    discount_rate        DECIMAL(7, 6) NOT NULL,
    deadline_urgency     DECIMAL(7, 6) NOT NULL,
    occurred_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_recommendation_events_user_time (user_id, occurred_at),
    KEY idx_recommendation_events_session_time (session_id, occurred_at),
    KEY idx_recommendation_events_group_type (group_buy_id, event_type),
    CONSTRAINT fk_recommendation_events_session
        FOREIGN KEY (session_id) REFERENCES recommendation_sessions (session_id),
    CONSTRAINT fk_recommendation_events_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_recommendation_events_group_buy
        FOREIGN KEY (group_buy_id) REFERENCES group_buys (group_buy_id),
    CONSTRAINT fk_recommendation_events_product
        FOREIGN KEY (product_id_snapshot) REFERENCES products (product_id),
    CONSTRAINT ck_recommendation_event_type
        CHECK (event_type IN ('IMPRESSION', 'CLICK', 'DETAIL_VIEW')),
    CONSTRAINT ck_recommendation_position CHECK (position IS NULL OR position >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '추천 학습용 노출·클릭·상세조회 로그';
