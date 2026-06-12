CREATE DATABASE IF NOT EXISTS bluenote_order DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

USE bluenote_order;

CREATE TABLE IF NOT EXISTS coupon_template (
    template_id BIGINT NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    face_value INT NOT NULL,
    threshold_amount INT NOT NULL DEFAULT 0,
    valid_days INT NOT NULL DEFAULT 7,
    cover_url VARCHAR(512) NULL,
    description VARCHAR(512) NULL,
    template_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (template_id),
    KEY idx_coupon_template_status (template_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_activity (
    activity_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    activity_name VARCHAR(128) NOT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    locked_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    per_user_limit INT NOT NULL DEFAULT 1,
    pay_amount INT NOT NULL DEFAULT 0,
    activity_status VARCHAR(32) NOT NULL,
    start_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    pay_timeout_minutes INT NOT NULL DEFAULT 15,
    preheated_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (activity_id),
    KEY idx_coupon_activity_current (activity_status, start_at, end_at),
    KEY idx_coupon_activity_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_seckill_request (
    request_id BIGINT NOT NULL,
    client_request_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    user_coupon_id BIGINT NULL,
    request_status VARCHAR(32) NOT NULL,
    result_message VARCHAR(256) NULL,
    pay_required TINYINT NOT NULL DEFAULT 0,
    pay_amount INT NOT NULL DEFAULT 0,
    expire_at DATETIME(3) NULL,
    requested_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (request_id),
    UNIQUE KEY uk_order_request_user_client (user_id, client_request_id),
    UNIQUE KEY uk_order_request_user_activity (user_id, activity_id),
    KEY idx_order_request_user_status (user_id, request_status, requested_at),
    KEY idx_order_request_activity_status (activity_id, request_status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS voucher_order (
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    pay_amount INT NOT NULL DEFAULT 0,
    order_status VARCHAR(32) NOT NULL,
    expire_at DATETIME(3) NULL,
    paid_at DATETIME(3) NULL,
    success_at DATETIME(3) NULL,
    closed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_request (request_id),
    UNIQUE KEY uk_order_user_activity (user_id, activity_id),
    KEY idx_order_user_time (user_id, created_at),
    KEY idx_order_status_expire (order_status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS payment_record (
    payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    channel_trade_no VARCHAR(128) NOT NULL,
    pay_amount INT NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    paid_at DATETIME(3) NULL,
    raw_payload_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uk_payment_channel_trade (channel, channel_trade_no),
    KEY idx_payment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS user_coupon (
    user_coupon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    source_order_id BIGINT NOT NULL,
    coupon_status VARCHAR(32) NOT NULL,
    face_value INT NOT NULL,
    threshold_amount INT NOT NULL DEFAULT 0,
    valid_start_at DATETIME(3) NOT NULL,
    valid_end_at DATETIME(3) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    expired_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY uk_user_coupon_source_order (source_order_id),
    KEY idx_user_coupon_page (user_id, coupon_status, issued_at, user_coupon_id),
    KEY idx_user_coupon_valid (coupon_status, valid_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS coupon_stock_log (
    id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    request_id BIGINT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_amount INT NOT NULL,
    before_stock INT NULL,
    after_stock INT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_log_activity_time (activity_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_status_log (
    id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    operator_type VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_status_log_order_time (order_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_timeout_task (
    task_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    expire_at DATETIME(3) NOT NULL,
    task_status VARCHAR(32) NOT NULL,
    executed_at DATETIME(3) NULL,
    error_message VARCHAR(256) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_order_timeout_order (order_id),
    KEY idx_order_timeout_due (task_status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_consume_record (
    id BIGINT NOT NULL,
    consumer_group VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    biz_key VARCHAR(128) NULL,
    envelope_json JSON NOT NULL,
    consume_status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(512) NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_consumer_event (consumer_group, event_id),
    KEY idx_order_consume_status_time (consume_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS order_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    send_status VARCHAR(32) NOT NULL DEFAULT 'INIT',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_outbox_event (event_id),
    KEY idx_order_outbox_status_retry (send_status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
