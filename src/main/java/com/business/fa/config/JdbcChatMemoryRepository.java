package com.business.fa.config;

import com.business.fa.tenant.TenantContext;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MySQL 持久化 ChatMemoryRepository - 支持多租户
 * 所有查询自动带 tenant_id 过滤
 */
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        String tenantId = TenantContext.getTenantId();
        return jdbcTemplate.queryForList(
                "SELECT conversation_id FROM chat_message WHERE tenant_id = ? GROUP BY conversation_id ORDER BY MAX(create_time) DESC",
                String.class, tenantId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        String tenantId = TenantContext.getTenantId();
        List<Message> messages = new ArrayList<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT role, content FROM chat_message WHERE tenant_id = ? AND conversation_id = ? ORDER BY id ASC",
                tenantId, conversationId);
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
        String tenantId = TenantContext.getTenantId();
        jdbcTemplate.update("DELETE FROM chat_message WHERE tenant_id = ? AND conversation_id = ?",
                tenantId, conversationId);
        for (Message msg : messages) {
            jdbcTemplate.update(
                    "INSERT INTO chat_message (tenant_id, conversation_id, role, content) VALUES (?, ?, ?, ?)",
                    tenantId, conversationId, msg.getMessageType().name(), msg.getText());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        String tenantId = TenantContext.getTenantId();
        jdbcTemplate.update("DELETE FROM chat_message WHERE tenant_id = ? AND conversation_id = ?",
                tenantId, conversationId);
    }
}
