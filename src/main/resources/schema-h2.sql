-- H2 建表脚本

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content CLOB NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chat_conversation ON chat_message(conversation_id);

CREATE TABLE IF NOT EXISTS knowledge_doc (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    source VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    chunk_ids CLOB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128),
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_token_session ON token_usage(session_id);

CREATE TABLE IF NOT EXISTS relay_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    relay_key_id VARCHAR(32) NOT NULL,
    client_token VARCHAR(128),
    model VARCHAR(64),
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_relay_key ON relay_usage(relay_key_id);
CREATE INDEX IF NOT EXISTS idx_relay_client ON relay_usage(client_token);

CREATE TABLE IF NOT EXISTS relay_api_key (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128),
    api_key VARCHAR(256) NOT NULL,
    base_url VARCHAR(512),
    model VARCHAR(256) DEFAULT '*',
    enabled BOOLEAN DEFAULT TRUE,
    total_tokens_used BIGINT DEFAULT 0,
    total_requests BIGINT DEFAULT 0,
    quota_limit BIGINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS relay_client_token (
    id VARCHAR(32) PRIMARY KEY,
    token VARCHAR(128) NOT NULL,
    name VARCHAR(128),
    enabled BOOLEAN DEFAULT TRUE,
    quota_limit BIGINT DEFAULT 0,
    used_tokens BIGINT DEFAULT 0,
    total_requests BIGINT DEFAULT 0,
    expire_at TIMESTAMP NULL,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rct_token ON relay_client_token(token);

CREATE TABLE IF NOT EXISTS relay_recharge_code (
    id VARCHAR(32) PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    token_amount BIGINT NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_by VARCHAR(32),
    used_at TIMESTAMP NULL,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(256)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_rrc_code ON relay_recharge_code(code);

CREATE TABLE IF NOT EXISTS relay_pay_order (
    id VARCHAR(32) PRIMARY KEY,
    client_token_id VARCHAR(32) NOT NULL,
    client_name VARCHAR(128),
    plan_name VARCHAR(64),
    token_amount BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confirm_at TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS idx_pay_status ON relay_pay_order(status);
CREATE INDEX IF NOT EXISTS idx_pay_client ON relay_pay_order(client_token_id);

CREATE TABLE IF NOT EXISTS session_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL,
    summary VARCHAR(512),
    user_intent VARCHAR(128),
    resolution VARCHAR(32),
    message_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_summary_session ON session_summary(session_id);
