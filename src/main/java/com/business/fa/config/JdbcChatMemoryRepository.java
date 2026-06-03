package com.business.fa.config;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MySQL 持久化的 ChatMemoryRepository
 * 对话记录存入 chat_message 表，重启不丢失
 */
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT conversation_id FROM chat_message GROUP BY conversation_id ORDER BY MAX(create_time) DESC",
                String.class);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role, content FROM chat_message WHERE conversation_id = ? ORDER BY id ASC",
                conversationId);
        for (Map<String, Object> row : rows) {
            String role = (String) row.get("role");
            String content = (String) row.get("content");
            Message msg = switch (role) {
                case "USER" -> new UserMessage(content);
                case "ASSISTANT" -> new AssistantMessage(content);
                case "SYSTEM" -> new SystemMessage(content);
                default -> null;
            };
            if (msg != null) messages.add(msg);
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 先删除旧记录，再全量写入（简单实现）
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
        for (Message msg : messages) {
            jdbcTemplate.update(
                    "INSERT INTO chat_message (conversation_id, role, content) VALUES (?, ?, ?)",
                    conversationId,
                    msg.getMessageType().name(),
                    msg.getText()
            );
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
    }
}
