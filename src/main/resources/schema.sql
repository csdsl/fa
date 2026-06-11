-- 对话记录表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_conversation (tenant_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识文档元数据表
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id VARCHAR(32) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    name VARCHAR(256) NOT NULL,
    source VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    chunk_ids TEXT COMMENT 'JSON array of chunk IDs',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Token 用量表
CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    session_id VARCHAR(128),
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_session (tenant_id, session_id),
    INDEX idx_date (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Token 中转站用量表
CREATE TABLE IF NOT EXISTS relay_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    relay_key_id VARCHAR(32) NOT NULL COMMENT '使用的上游 Key ID',
    client_token VARCHAR(128) COMMENT '客户端 Token',
    model VARCHAR(64) COMMENT '请求的模型',
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_relay_key (relay_key_id),
    INDEX idx_client (client_token),
    INDEX idx_relay_date (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 中转站：上游 API Key 表
CREATE TABLE IF NOT EXISTS relay_api_key (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(128),
    api_key VARCHAR(256) NOT NULL,
    base_url VARCHAR(512),
    model VARCHAR(256) DEFAULT '*',
    enabled TINYINT(1) DEFAULT 1,
    total_tokens_used BIGINT DEFAULT 0,
    total_requests BIGINT DEFAULT 0,
    quota_limit BIGINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 中转站：客户端 Token 表
CREATE TABLE IF NOT EXISTS relay_client_token (
    id VARCHAR(32) PRIMARY KEY,
    token VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128),
    enabled TINYINT(1) DEFAULT 1,
    quota_limit BIGINT DEFAULT 0,
    used_tokens BIGINT DEFAULT 0,
    total_requests BIGINT DEFAULT 0,
    expire_at DATETIME NULL,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 中转站：充值码表
CREATE TABLE IF NOT EXISTS relay_recharge_code (
    id VARCHAR(32) PRIMARY KEY,
    code VARCHAR(32) NOT NULL UNIQUE,
    token_amount BIGINT NOT NULL,
    used TINYINT(1) DEFAULT 0,
    used_by VARCHAR(32),
    used_at DATETIME NULL,
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 中转站：充值订单表
CREATE TABLE IF NOT EXISTS relay_pay_order (
    id VARCHAR(32) PRIMARY KEY,
    client_token_id VARCHAR(32) NOT NULL,
    client_name VARCHAR(128),
    plan_name VARCHAR(64),
    token_amount BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/paid/confirmed/rejected',
    create_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    confirm_at DATETIME NULL,
    INDEX idx_pay_status (status),
    INDEX idx_pay_client (client_token_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话摘要表
CREATE TABLE IF NOT EXISTS session_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    session_id VARCHAR(128) NOT NULL,
    summary VARCHAR(512),
    user_intent VARCHAR(128),
    resolution VARCHAR(32),
    message_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_session (tenant_id, session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
