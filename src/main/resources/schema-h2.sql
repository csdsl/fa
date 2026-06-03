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
