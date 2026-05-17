-- Sokind chat: initial schema (MySQL 8.0)
-- UUID 컬럼은 운영 가독성을 위해 VARCHAR(36) CHARACTER SET ascii.
-- (BINARY(16) 대비 인덱스 크기/비교 비용 약간 증가하나, mysql CLI / GUI 에서 사람이
--  바로 읽을 수 있다는 이득이 더 크다고 판단. design.md 의 결정 표 참조.)

CREATE TABLE sessions (
    id         VARCHAR(36) CHARACTER SET ascii NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at   DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE participants (
    session_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    left_at    DATETIME(6) NULL,
    presence   VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    PRIMARY KEY (session_id, user_id),
    CONSTRAINT fk_participants_session FOREIGN KEY (session_id) REFERENCES sessions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE events (
    server_seq      BIGINT      NOT NULL AUTO_INCREMENT,
    session_id      VARCHAR(36) CHARACTER SET ascii NOT NULL,
    client_event_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    type            VARCHAR(24) NOT NULL,
    payload         JSON        NOT NULL,
    client_ts       DATETIME(6) NOT NULL,
    server_ts       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (server_seq),
    UNIQUE KEY uk_events_dedup (session_id, client_event_id),
    KEY idx_events_session_seq (session_id, server_seq),
    KEY idx_events_session_ts (session_id, server_ts),
    CONSTRAINT fk_events_session FOREIGN KEY (session_id) REFERENCES sessions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE projection_outbox (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    event_seq    BIGINT      NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count  INT         NOT NULL DEFAULT 0,
    next_attempt DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error   TEXT        NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_outbox_status_next (status, next_attempt)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE snapshots (
    session_id VARCHAR(36) CHARACTER SET ascii NOT NULL,
    up_to_seq  BIGINT      NOT NULL,
    state      JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (session_id, up_to_seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
