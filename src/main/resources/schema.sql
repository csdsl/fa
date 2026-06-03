-- 对话记录表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'USER/ASSISTANT/SYSTEM',
    content TEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识文档元数据表
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(256) NOT NULL,
    source VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    chunk_ids TEXT COMMENT 'JSON array of chunk IDs',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Token 用量表
CREATE TABLE IF NOT EXISTS token_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128),
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id),
    INDEX idx_date (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 对话摘要表
CREATE TABLE IF NOT EXISTS session_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    summary VARCHAR(512),
    user_intent VARCHAR(128),
    resolution VARCHAR(32),
    message_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
